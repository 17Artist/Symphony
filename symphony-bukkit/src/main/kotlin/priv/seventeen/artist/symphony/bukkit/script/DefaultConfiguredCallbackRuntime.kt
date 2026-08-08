/*
 * Copyright 2026 17Artist
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package priv.seventeen.artist.symphony.bukkit.script

import org.bukkit.Bukkit
import org.bukkit.attribute.Attribute
import org.bukkit.entity.LivingEntity
import org.bukkit.entity.Player
import org.bukkit.plugin.Plugin
import org.bukkit.scheduler.BukkitTask
import org.bukkit.potion.PotionEffect
import org.bukkit.potion.PotionEffectType
import priv.seventeen.artist.symphony.api.attribute.AttributeKey
import priv.seventeen.artist.symphony.api.attribute.AttributeModifier
import priv.seventeen.artist.symphony.api.attribute.AttributeOperation
import priv.seventeen.artist.symphony.api.attribute.AttributeService
import priv.seventeen.artist.symphony.api.attribute.AttributeSourceKey
import priv.seventeen.artist.symphony.api.damage.DamageChannelAmount
import priv.seventeen.artist.symphony.api.damage.DamageRequest
import priv.seventeen.artist.symphony.api.damage.DamageService
import priv.seventeen.artist.symphony.api.source.AttributeSourceService
import priv.seventeen.artist.symphony.api.source.SourceUpdateResult
import priv.seventeen.artist.symphony.bukkit.compat.BukkitEffectTypes
import priv.seventeen.artist.symphony.engine.trigger.EntityTriggerContext
import java.util.Locale
import java.util.UUID
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.ThreadLocalRandom
import kotlin.math.min

class DefaultConfiguredCallbackRuntime(
    private val plugin: Plugin,
    private val attributes: AttributeService,
    private val sources: AttributeSourceService,
    private val damage: DamageService,
    private val random: () -> Double = { ThreadLocalRandom.current().nextDouble() },
    private val skillCaster: (LivingEntity, String, LivingEntity?) -> Boolean = { _, _, _ -> false },
    private val levelReader: (LivingEntity) -> Int? = { null },
    private val statusApplier: (LivingEntity, String, Int, Long?) -> Boolean = { _, _, _, _ -> false }
) : ConfiguredCallbackRuntime {
    private data class CooldownKey(val entityId: UUID, val callbackId: String, val key: String)
    private data class BuffKey(val entityId: UUID, val source: AttributeSourceKey)
    private data class ActiveBuff(val token: UUID, val task: BukkitTask)

    private val cooldowns = ConcurrentHashMap<CooldownKey, Long>()
    private val activeBuffs = ConcurrentHashMap<BuffKey, ActiveBuff>()

    override fun validateConditions(ownerId: String, conditions: List<Map<String, Any?>>) {
        ConfiguredCallbackSchema.validateConditions(ownerId, conditions)
    }

    override fun validateActions(ownerId: String, actions: List<Map<String, Any?>>) {
        ConfiguredCallbackSchema.validateActions(ownerId, actions)
    }

    override fun test(
        ownerId: String,
        conditions: List<Map<String, Any?>>,
        context: EntityTriggerContext
    ): Boolean {
        val pendingCooldowns = mutableListOf<Pair<CooldownKey, Long>>()
        val now = System.currentTimeMillis()
        val passed = conditions.all { evaluateCondition(ownerId, it, context, now, pendingCooldowns) }
        if (passed) pendingCooldowns.forEach { (key, until) -> cooldowns[key] = until }
        if (cooldowns.size > MAX_COOLDOWNS) maintenance(now)
        return passed
    }

    override fun execute(ownerId: String, actions: List<Map<String, Any?>>, context: EntityTriggerContext) {
        check(Bukkit.isPrimaryThread()) { "配置动作必须在 Bukkit 主线程执行" }
        actions.forEachIndexed { index, action -> executeAction(ownerId, index, action, context) }
    }

    fun clear() {
        cooldowns.clear()
        activeBuffs.values.forEach { it.task.cancel() }
        activeBuffs.clear()
    }

    fun forget(entityId: UUID) {
        cooldowns.keys.removeIf { it.entityId == entityId }
        activeBuffs.entries.toList().forEach { (key, buff) ->
            if (key.entityId == entityId && activeBuffs.remove(key, buff)) buff.task.cancel()
        }
    }

    fun maintenance(now: Long) {
        cooldowns.entries.removeIf { it.value <= now }
    }

    fun retainCallbacks(callbackIds: Set<String>) {
        cooldowns.keys.removeIf { it.callbackId !in callbackIds }
    }

    private fun evaluateCondition(
        ownerId: String,
        condition: Map<String, Any?>,
        context: EntityTriggerContext,
        now: Long,
        pendingCooldowns: MutableList<Pair<CooldownKey, Long>>
    ): Boolean {
        return when (val type = condition.getValue("type").toString()) {
        "chance" -> random() < resolveDouble(condition["value"], context).coerceIn(0.0, 1.0)
        "cooldown" -> {
            val key = CooldownKey(context.self.uniqueId, ownerId, condition.getValue("key").toString())
            val duration = resolveLong(condition["duration-ms"], context)
            if ((cooldowns[key] ?: 0L) > now) false else true.also { pendingCooldowns += key to Math.addExact(now, duration) }
        }
        "health" -> {
            val target = resolveTarget(condition["target"]?.toString(), context) ?: return false
            val actual = if (condition["percent"] == true) {
                val maximum = target.getAttribute(Attribute.GENERIC_MAX_HEALTH)?.value ?: target.health
                if (maximum <= 0.0) 0.0 else target.health / maximum
            } else target.health
            compare(actual, condition["operator"]?.toString() ?: ">=", resolveDouble(condition["value"], context))
        }
        "level" -> {
            val target = resolveTarget(condition["target"]?.toString(), context) ?: return false
            val level = levelReader(target) ?: return false
            compare(level.toDouble(), condition["operator"]?.toString() ?: ">=", resolveDouble(condition["value"], context))
        }
        "world" -> context.self.world.name in stringSet(condition["names"])
        "biome" -> {
            val target = resolveTarget(condition["target"]?.toString(), context) ?: return false
            target.location.block.biome.name in stringSet(condition["names"]).map(String::uppercase)
        }
        "permission" -> (resolveTarget(condition["target"]?.toString(), context) as? Player)
            ?.hasPermission(condition.getValue("permission").toString()) == true
        "attribute" -> {
            val target = resolveTarget(condition["target"]?.toString(), context) ?: return false
            compare(
                attributes.value(target, attributeKey(condition.getValue("attribute").toString())),
                condition["operator"]?.toString() ?: ">=",
                resolveDouble(condition["value"], context)
            )
        }
        "target_type" -> context.target?.type?.name in stringSet(condition["entity-types"]).map(String::uppercase)
        "posture" -> context.values["posture"]?.toString() == condition["value"]?.toString()
        "equipment" -> context.values["equipment:${condition["slot"]}"]?.toString() in
            setOfNotNull(condition["item"]?.toString(), condition["tag"]?.toString())
        "and" -> listOfMaps(condition["conditions"], "condition.conditions")
            .all { evaluateCondition(ownerId, it, context, now, pendingCooldowns) }
        "or" -> listOfMaps(condition["conditions"], "condition.conditions")
            .any { evaluateCondition(ownerId, it, context, now, pendingCooldowns) }
        "not" -> !evaluateCondition(ownerId, stringMap(condition["condition"], "condition.condition"), context, now, pendingCooldowns)
            else -> throw IllegalStateException("未编译条件类型 $type")
        }
    }

    private fun executeAction(ownerId: String, index: Int, action: Map<String, Any?>, context: EntityTriggerContext) {
        val target = resolveTarget(action["target"]?.toString(), context) ?: context.self
        when (action.getValue("type").toString()) {
            "damage" -> {
                val amount = resolveAmount(action, context)
                damage.damage(
                    DamageRequest(
                        attacker = context.self,
                        victim = target,
                        channels = listOf(DamageChannelAmount(action.getValue("channel").toString(), amount)),
                        cause = "action:$ownerId",
                        parentTransactionId = context.transactionId,
                        allowCritical = action["allow-critical"] as? Boolean ?: false
                    )
                )
            }
            "heal" -> {
                val maximum = target.getAttribute(Attribute.GENERIC_MAX_HEALTH)?.value ?: target.health
                val healingPower = attributes.value(context.self, HEALING_POWER).coerceAtLeast(-1.0)
                target.health = min(maximum, target.health + resolveAmount(action, context) * (1.0 + healingPower))
            }
            "attribute_buff" -> applyModifier(ownerId, index, action, context, target, persistent = false)
            "permanent_modifier" -> applyModifier(ownerId, index, action, context, target, persistent = true)
            "skill" -> skillCaster(context.self, namespaced(action.getValue("skill").toString()), target)
            "potion" -> {
                val effect = potionType(action.getValue("effect").toString())
                target.addPotionEffect(
                    PotionEffect(
                        effect,
                        resolveInt(action["duration-ticks"] ?: 20, context).coerceAtLeast(1),
                        resolveInt(action["amplifier"] ?: 0, context).coerceAtLeast(0),
                        action["ambient"] as? Boolean ?: false,
                        action["particles"] as? Boolean ?: true,
                        action["icon"] as? Boolean ?: true
                    )
                )
            }
            "particle" -> target.world.spawnParticle(
                BukkitEffectTypes.particle(action.getValue("particle").toString()),
                target.location.clone().add(0.0, target.height * 0.5, 0.0),
                resolveInt(action["count"] ?: 1, context).coerceIn(0, 10_000),
                resolveDouble(action["offset-x"] ?: 0.0, context),
                resolveDouble(action["offset-y"] ?: 0.0, context),
                resolveDouble(action["offset-z"] ?: 0.0, context),
                resolveDouble(action["extra"] ?: 0.0, context)
            )
            "sound" -> target.world.playSound(
                target.location,
                BukkitEffectTypes.sound(action.getValue("sound").toString()),
                resolveDouble(action["volume"] ?: 1.0, context).toFloat(),
                resolveDouble(action["pitch"] ?: 1.0, context).toFloat()
            )
            "message" -> target.sendMessage(interpolate(action.getValue("message").toString(), context))
            "command" -> {
                val command = interpolate(action.getValue("command").toString(), context).removePrefix("/")
                if (action["as"]?.toString() == "self" && context.self is Player) {
                    Bukkit.dispatchCommand(context.self as Player, command)
                } else Bukkit.dispatchCommand(Bukkit.getConsoleSender(), command)
            }
            "shield" -> {
                val amount = resolveDouble(action["amount"], context)
                val next = if (action["mode"]?.toString() == "set") amount else damage.shield(target) + amount
                damage.setShield(target, next.coerceAtLeast(0.0))
            }
            "status" -> check(statusApplier(
                target,
                namespaced(action.getValue("status").toString()),
                resolveInt(action["stacks"] ?: 1, context),
                action["duration-ms"]?.let { resolveLong(it, context) }
            )) { "状态动作被状态服务拒绝" }
        }
    }

    private fun applyModifier(
        ownerId: String,
        index: Int,
        action: Map<String, Any?>,
        context: EntityTriggerContext,
        target: LivingEntity,
        persistent: Boolean
    ) {
        val source = AttributeSourceKey(if (persistent) "persistent" else "buff", "$ownerId:$index")
        val duration = action["duration-ms"]?.let { resolveLong(it, context) }
        if (!persistent) require(duration != null && duration > 0) { "attribute_buff 必须有正 duration-ms" }
        val expires = duration?.let { Math.addExact(System.currentTimeMillis(), it) }
        val modifier = AttributeModifier(
            id = action["key"]?.toString() ?: "$ownerId:$index",
            attribute = attributeKey(action.getValue("attribute").toString()),
            operation = AttributeOperation.parse(action["operation"]?.toString() ?: "add"),
            value = resolveDouble(action["value"], context),
            priority = (action["priority"] as? Number)?.toInt() ?: 0,
            persistent = persistent,
            expiresAtMillis = expires,
            description = "callback:$ownerId"
        )
        val result = sources.replaceSource(target, source, listOf(modifier))
        require(result !is SourceUpdateResult.Rejected) {
            "属性动作提交失败：${(result as SourceUpdateResult.Rejected).reason}"
        }
        if (duration != null) {
            val token = UUID.randomUUID()
            val entityId = target.uniqueId
            val key = BuffKey(entityId, source)
            val ticks = ((duration + 49L) / 50L).coerceAtLeast(1L)
            val task = Bukkit.getScheduler().runTaskLater(plugin, Runnable {
                val active = activeBuffs[key] ?: return@Runnable
                if (active.token != token || !activeBuffs.remove(key, active)) return@Runnable
                val current = Bukkit.getEntity(entityId) as? LivingEntity ?: return@Runnable
                sources.removeSource(current, source)
            }, ticks)
            activeBuffs.put(key, ActiveBuff(token, task))?.task?.cancel()
        }
    }

    private fun resolveAmount(action: Map<String, Any?>, context: EntityTriggerContext): Double {
        val direct = action["amount"]?.let { resolveDouble(it, context) } ?: 0.0
        val perStack = action["amount-per-stack"]?.let { resolveDouble(it, context) } ?: 0.0
        return (direct + perStack * ((context.values["stacks"] as? Number)?.toInt() ?: 0)).also {
            require(it.isFinite() && it >= 0.0) { "动作数值必须是非负有限数" }
        }
    }

    private fun resolveTarget(raw: String?, context: EntityTriggerContext): LivingEntity? = when (raw ?: "target") {
        "self", "caster" -> context.self
        "target" -> context.target
        "attacker" -> context.values["attacker"] as? LivingEntity
        "victim" -> context.values["victim"] as? LivingEntity
        else -> null
    }

    private fun resolveDouble(raw: Any?, context: EntityTriggerContext): Double {
        val value = when (raw) {
            is Number -> raw.toDouble()
            is String -> if (raw.startsWith('{') && raw.endsWith('}')) {
                (context.values[raw.substring(1, raw.length - 1)] as? Number)?.toDouble()
            } else raw.removeSuffix("%").toDoubleOrNull()?.let { if (raw.endsWith('%')) it / 100.0 else it }
            else -> null
        }
        require(value != null && value.isFinite()) { "无法把 $raw 解析为有限数值" }
        return value
    }

    private fun resolveLong(raw: Any?, context: EntityTriggerContext): Long = resolveDouble(raw, context).let {
        require(it >= 0.0 && it <= Long.MAX_VALUE.toDouble() && it % 1.0 == 0.0) { "$raw 必须是非负整数" }
        it.toLong()
    }

    private fun resolveInt(raw: Any?, context: EntityTriggerContext): Int = resolveLong(raw, context).let(Math::toIntExact)

    private fun interpolate(raw: String, context: EntityTriggerContext): String = EMBEDDED_PLACEHOLDER.replace(raw) { match ->
        context.values[match.groupValues[1]]?.toString() ?: match.value
    }

    private fun compare(actual: Double, operator: String, expected: Double): Boolean = when (operator) {
        ">" -> actual > expected
        ">=" -> actual >= expected
        "<" -> actual < expected
        "<=" -> actual <= expected
        "==", "=" -> actual == expected
        "!=", "<>" -> actual != expected
        else -> throw IllegalArgumentException("未知比较运算符 $operator")
    }

    private fun listOfMaps(raw: Any?, path: String): List<Map<String, Any?>> =
        (raw as? List<*>)?.mapIndexed { index, value -> stringMap(value, "$path[$index]") }
            ?: throw IllegalArgumentException("$path 必须是列表")

    private fun stringMap(raw: Any?, path: String): Map<String, Any?> =
        (raw as? Map<*, *>)?.entries?.associate { it.key.toString() to it.value }
            ?: throw IllegalArgumentException("$path 必须是 YAML 映射")

    private fun stringSet(raw: Any?): Set<String> = when (raw) {
        is Collection<*> -> raw.mapTo(linkedSetOf()) { it.toString() }
        is String -> setOf(raw)
        else -> emptySet()
    }

    private fun attributeKey(raw: String) = AttributeKey(namespaced(raw))
    private fun namespaced(raw: String): String = if (':' in raw) raw else "symphony:$raw"

    @Suppress("DEPRECATION")
    private fun potionType(raw: String): PotionEffectType =
        PotionEffectType.getByName(raw.uppercase(Locale.ROOT))
            ?: throw IllegalArgumentException("未知药水效果 $raw")

    companion object {
        private const val MAX_COOLDOWNS = 100_000
        private val EMBEDDED_PLACEHOLDER = Regex("\\{([a-zA-Z0-9_.-]+)}")
        private val HEALING_POWER = AttributeKey.symphony("healing_power")
    }
}
