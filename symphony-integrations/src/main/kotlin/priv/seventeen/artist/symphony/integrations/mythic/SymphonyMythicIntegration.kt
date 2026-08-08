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

package priv.seventeen.artist.symphony.integrations.mythic

import io.lumine.mythic.api.adapters.AbstractEntity
import io.lumine.mythic.api.config.MythicConfig
import io.lumine.mythic.api.config.MythicLineConfig
import io.lumine.mythic.api.skills.ITargetedEntitySkill
import io.lumine.mythic.api.skills.SkillMetadata
import io.lumine.mythic.api.skills.SkillResult
import io.lumine.mythic.api.skills.placeholders.PlaceholderDouble
import io.lumine.mythic.api.skills.placeholders.PlaceholderInt
import io.lumine.mythic.api.skills.placeholders.PlaceholderString
import io.lumine.mythic.bukkit.BukkitAdapter
import io.lumine.mythic.bukkit.events.MythicMechanicLoadEvent
import io.lumine.mythic.bukkit.events.MythicMobDeathEvent
import io.lumine.mythic.bukkit.events.MythicMobDespawnEvent
import io.lumine.mythic.bukkit.events.MythicMobSpawnEvent
import io.lumine.mythic.core.skills.mechanics.CustomMechanic
import org.bukkit.Bukkit
import org.bukkit.NamespacedKey
import org.bukkit.attribute.Attribute
import org.bukkit.entity.LivingEntity
import org.bukkit.event.EventHandler
import org.bukkit.event.EventPriority
import org.bukkit.event.HandlerList
import org.bukkit.event.Listener
import org.bukkit.plugin.Plugin
import priv.seventeen.artist.symphony.api.attribute.AttributeKey
import priv.seventeen.artist.symphony.api.attribute.AttributeModifier
import priv.seventeen.artist.symphony.api.attribute.AttributeOperation
import priv.seventeen.artist.symphony.api.attribute.AttributeSourceKey
import priv.seventeen.artist.symphony.api.damage.DamageChannelAmount
import priv.seventeen.artist.symphony.api.damage.DamageRequest
import priv.seventeen.artist.symphony.api.damage.DamageResultState
import priv.seventeen.artist.symphony.api.service.SymphonyApi
import priv.seventeen.artist.symphony.api.source.SourceUpdateResult
import java.util.*
import java.util.function.BiConsumer
import java.util.function.Consumer

class SymphonyMythicListener(
    private val api: SymphonyApi,
    private val plugin: Plugin,
    private val onAttributeRejected: BiConsumer<String, String>,
    private val onEntityRemoved: Consumer<UUID>
) : Listener {
    private val attributeConfiguration = MythicAttributeConfiguration()

    @EventHandler
    fun onMechanicLoad(event: MythicMechanicLoadEvent) {
        when (event.mechanicName.lowercase()) {
            "symphonydamage", "symdamage" -> event.register(SymphonyDamageMechanic(event.container, event.config, api))
            "symphonyheal", "symheal" -> event.register(SymphonyHealMechanic(event.container, event.config))
            "symphonybuff", "symbuff" -> event.register(SymphonyBuffMechanic(event.container, event.config, api, plugin))
            "symphonyskill", "symskill" -> event.register(SymphonySkillMechanic(event.container, event.config, api))
        }
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    fun onSpawn(event: MythicMobSpawnEvent) {
        val entity = event.livingEntity
        val mobType = event.mobType.internalName
        val config = event.mobType.config
        val source = source(entity)
        val modifiers = try {
            val enabled = if (config.isSet("Symphony.Enabled")) {
                config.getBoolean("Symphony.Enabled", true)
            } else true
            attributeConfiguration.compile(
                enabled = enabled,
                structured = config.getNestedConfigs("Symphony.Attributes").map { (attribute, node) ->
                    node.toAttributeEntry(attribute)
                },
                legacyLines = config.getStringList("SymphonyAttributes"),
                mobLevel = event.mobLevel
            )
        } catch (error: Throwable) {
            onAttributeRejected.accept(mobType, error.message ?: error.javaClass.simpleName)
            return
        }

        val result = if (modifiers.isEmpty()) {
            api.sources.removeSource(entity, source)
        } else api.sources.replaceSource(entity, source, modifiers)
        if (result is SourceUpdateResult.Rejected) {
            onAttributeRejected.accept(mobType, result.reason)
        }
    }

    @EventHandler
    fun onDeath(event: MythicMobDeathEvent) {
        val entity = event.entity as? LivingEntity ?: return
        api.sources.removeSource(entity, source(entity))
        onEntityRemoved.accept(entity.uniqueId)
    }

    @EventHandler
    fun onDespawn(event: MythicMobDespawnEvent) {
        val entity = event.entity as? LivingEntity ?: return
        api.sources.removeSource(entity, source(entity))
        onEntityRemoved.accept(entity.uniqueId)
    }

    private fun source(entity: LivingEntity) = AttributeSourceKey("mythicmobs", entity.uniqueId.toString())

    private fun MythicConfig.toAttributeEntry(attribute: String): MythicAttributeEntry {
        val fields = keys.associateBy(::normalizeField)
        val unknown = fields.keys - ATTRIBUTE_FIELDS
        require(unknown.isEmpty()) {
            "Symphony.Attributes.$attribute 包含未知字段：${unknown.sorted().joinToString()}"
        }
        fun string(field: String, default: String? = null): String? =
            fields[field]?.let { getString(it, default) } ?: default

        return MythicAttributeEntry(
            attribute = attribute,
            operation = string("operation", "add")!!,
            value = string("value")
                ?: throw IllegalArgumentException("缺少必填项 Symphony.Attributes.$attribute.Value"),
            perLevel = string("perlevel", "0")!!,
            priority = string("priority", "0")!!,
            description = string("description")
        )
    }

    private fun normalizeField(value: String): String =
        value.lowercase(Locale.ROOT).replace("-", "").replace("_", "")

    companion object {
        private val ATTRIBUTE_FIELDS = setOf("operation", "value", "perlevel", "priority", "description")
    }
}

private abstract class TargetMechanic(@Suppress("UNUSED_PARAMETER") holder: CustomMechanic?) : ITargetedEntitySkill {
    final override fun castAtEntity(data: SkillMetadata, target: AbstractEntity): SkillResult {
        val entity = BukkitAdapter.adapt(target) as? LivingEntity ?: return SkillResult.INVALID_TARGET
        return execute(data, entity, target)
    }
    protected abstract fun execute(data: SkillMetadata, target: LivingEntity, abstractTarget: AbstractEntity): SkillResult
}

private class SymphonyDamageMechanic(holder: CustomMechanic?, config: MythicLineConfig, private val api: SymphonyApi) : TargetMechanic(holder) {
    private val amount: PlaceholderDouble = config.getPlaceholderDouble(arrayOf("a", "amount"), 1.0)
    private val channel: PlaceholderString = config.getPlaceholderString(arrayOf("c", "channel"), "physical")
    override fun execute(data: SkillMetadata, target: LivingEntity, abstractTarget: AbstractEntity): SkillResult {
        val attacker = BukkitAdapter.adapt(data.caster.entity) as? LivingEntity
        val result = api.damage.damage(
            DamageRequest(attacker, target, listOf(DamageChannelAmount(channel.get(data, abstractTarget), amount.get(data, abstractTarget))), "mythicmobs:mechanic")
        )
        return if (result.state == DamageResultState.REJECTED) SkillResult.ERROR else SkillResult.SUCCESS
    }
}

private class SymphonyHealMechanic(holder: CustomMechanic?, config: MythicLineConfig) : TargetMechanic(holder) {
    private val amount: PlaceholderDouble = config.getPlaceholderDouble(arrayOf("a", "amount"), 1.0)
    override fun execute(data: SkillMetadata, target: LivingEntity, abstractTarget: AbstractEntity): SkillResult {
        val maximum = target.getAttribute(Attribute.GENERIC_MAX_HEALTH)?.value ?: target.health
        target.health = (target.health + amount.get(data, abstractTarget)).coerceIn(0.0, maximum)
        return SkillResult.SUCCESS
    }
}

private class SymphonyBuffMechanic(
    holder: CustomMechanic?,
    config: MythicLineConfig,
    private val api: SymphonyApi,
    private val plugin: Plugin
) : TargetMechanic(holder) {
    private val attribute: PlaceholderString = config.getPlaceholderString(arrayOf("attr", "attribute"), "physical_damage")
    private val amount: PlaceholderDouble = config.getPlaceholderDouble(arrayOf("a", "amount"), 1.0)
    private val duration: PlaceholderInt = config.getPlaceholderInteger(arrayOf("d", "duration"), 100)
    override fun execute(data: SkillMetadata, target: LivingEntity, abstractTarget: AbstractEntity): SkillResult {
        val key = AttributeKey(
            attribute.get(data, abstractTarget).let { if (':' in it) it else "symphony:$it" }
        )
        val expires = System.currentTimeMillis() + duration.get(data, abstractTarget).coerceAtLeast(1) * 50L
        val token = UUID.randomUUID()
        val modifier = AttributeModifier(
            "mythic:$token", key, AttributeOperation.ADD,
            amount.get(data, abstractTarget), expiresAtMillis = expires, description = "由 MythicMobs 机制写入"
        )
        val source = AttributeSourceKey("mythicmobs", "buff:${target.uniqueId}:$token")
        api.sources.replaceSource(target, source, listOf(modifier))
        val entityId = target.uniqueId
        Bukkit.getScheduler().runTaskLater(plugin, Runnable {
            val current = Bukkit.getEntity(entityId) as? LivingEntity ?: return@Runnable
            api.sources.removeSource(current, source)
        }, duration.get(data, abstractTarget).coerceAtLeast(1).toLong())
        return SkillResult.SUCCESS
    }
}

private class SymphonySkillMechanic(holder: CustomMechanic?, config: MythicLineConfig, private val api: SymphonyApi) : TargetMechanic(holder) {
    private val skill: PlaceholderString = config.getPlaceholderString(arrayOf("s", "skill"), "")
    override fun execute(data: SkillMetadata, target: LivingEntity, abstractTarget: AbstractEntity): SkillResult {
        val caster = BukkitAdapter.adapt(data.caster.entity) as? LivingEntity ?: return SkillResult.INVALID_TARGET
        val raw = skill.get(data, abstractTarget)
        val key = NamespacedKey.fromString(if (':' in raw) raw else "symphony:$raw") ?: return SkillResult.ERROR
        return if (api.skills.cast(caster, key, target)) SkillResult.SUCCESS else SkillResult.CONDITION_FAILED
    }
}

object MythicMobsIntegration {
    private var listener: SymphonyMythicListener? = null

    @JvmStatic
    fun install(
        plugin: Plugin,
        api: SymphonyApi,
        onAttributeRejected: BiConsumer<String, String>,
        onEntityRemoved: Consumer<UUID>
    ) {
        if (listener != null) return
        SymphonyMythicListener(api, plugin, onAttributeRejected, onEntityRemoved).also {
            Bukkit.getPluginManager().registerEvents(it, plugin)
            listener = it
        }
    }

    @JvmStatic
    fun uninstall() {
        listener?.let(HandlerList::unregisterAll)
        listener = null
    }
}
