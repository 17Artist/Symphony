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

package priv.seventeen.artist.symphony.bukkit.gameplay

import org.bukkit.Bukkit
import org.bukkit.NamespacedKey
import org.bukkit.entity.LivingEntity
import org.bukkit.entity.Player
import org.bukkit.inventory.EquipmentSlot
import priv.seventeen.artist.symphony.api.attribute.AttributeSourceKey
import priv.seventeen.artist.symphony.api.event.SkillConfirmedEvent
import priv.seventeen.artist.symphony.api.event.SkillPrepareEvent
import priv.seventeen.artist.symphony.api.service.SkillService
import priv.seventeen.artist.symphony.api.service.SkillActivationView
import priv.seventeen.artist.symphony.api.service.SkillView
import priv.seventeen.artist.symphony.bukkit.script.AriaCallbackRuntime
import priv.seventeen.artist.symphony.bukkit.service.BukkitAttributeSourceService
import priv.seventeen.artist.symphony.bukkit.service.BukkitTriggerService
import priv.seventeen.artist.symphony.engine.definition.*
import priv.seventeen.artist.symphony.engine.trigger.EntityTriggerContext
import priv.seventeen.artist.symphony.engine.trigger.SkillCastTrigger
import priv.seventeen.artist.symphony.engine.trigger.SkillHitTrigger
import java.util.UUID
import java.util.concurrent.ConcurrentHashMap

enum class SkillInputStatus { NONE, SUCCESS, COOLDOWN, NO_TARGET, REJECTED }

data class SkillInputResult(
    val status: SkillInputStatus,
    val skillName: String? = null,
    val remainingMillis: Long = 0L,
    val cancelEvent: Boolean = false
)

class RuntimeSkillService(
    private val definitions: DefinitionRepository,
    private val sources: BukkitAttributeSourceService,
    private val aria: AriaCallbackRuntime,
    private val triggers: BukkitTriggerService,
    private val enabled: Boolean = true,
    private val clock: () -> Long = System::currentTimeMillis
) : SkillService {
    private data class CooldownKey(val entityId: UUID, val source: AttributeSourceKey, val skill: String)
    private val cooldowns = ConcurrentHashMap<CooldownKey, Long>()

    override fun knownSkills(): Set<NamespacedKey> = if (!enabled) emptySet() else
        definitions.current().snapshot.skills.keys.mapNotNullTo(linkedSetOf(), NamespacedKey::fromString)

    override fun inspect(caster: LivingEntity): List<SkillView> {
        if (!enabled) return emptyList()
        return sources.itemSources(caster).toSortedMap().flatMap { (source, item) ->
            item.skills.mapNotNull { contribution ->
                val definition = definitions.current().snapshot.skills[contribution.id.toString()] ?: return@mapNotNull null
                val targeting = definition.values["targeting"] as? Map<*, *>
                SkillView(
                    key = contribution.id,
                    name = definition.values["name"]?.toString() ?: contribution.id.key,
                    provider = definition.values["provider"]?.toString() ?: "symphony:aria",
                    source = source,
                    itemId = item.overtureItemId,
                    instanceId = item.instanceId,
                    level = contribution.level,
                    description = definition.values["description"]?.toString().orEmpty(),
                    targetType = targeting?.get("type")?.toString() ?: "self",
                    range = (targeting?.get("range") as? Number)?.toDouble(),
                    activation = definition.skillActivation()?.let { activation ->
                        SkillActivationView(
                            input = activation.input.id,
                            source = activation.source.id,
                            cancelEvent = activation.cancelPolicy.id
                        )
                    },
                    cooldownMillis = (definition.values["cooldown-ms"] as? Number)?.toLong() ?: 0L,
                    remainingCooldownMillis = remainingCooldown(caster, source, contribution.id)
                )
            }
        }
    }

    fun activate(
        player: Player,
        input: SkillActivationInput,
        hand: EquipmentSlot,
        explicitTarget: LivingEntity? = null
    ): SkillInputResult {
        if (!enabled || hand !in setOf(EquipmentSlot.HAND, EquipmentSlot.OFF_HAND)) return SkillInputResult(SkillInputStatus.NONE)
        check(Bukkit.isPrimaryThread()) { "技能输入处理必须在 Bukkit 主线程执行" }
        val candidate = inspect(player).asSequence()
            .mapNotNull { view ->
                val activation = definitions.current().snapshot.skills[view.key.toString()]?.skillActivation()
                    ?: return@mapNotNull null
                if (activation.input != input || !matchesInputSource(view.source, activation.source, hand)) return@mapNotNull null
                Triple(view, activation, activation.priority)
            }
            .sortedWith(compareByDescending<Triple<SkillView, SkillActivationDefinition, Int>> { it.third }
                .thenBy { it.first.source.toString() }
                .thenBy { it.first.key.toString() })
            .firstOrNull()
            ?: return SkillInputResult(SkillInputStatus.NONE)
        val view = candidate.first
        val activation = candidate.second
        val shouldAlwaysCancel = activation.cancelPolicy == SkillCancelPolicy.ALWAYS
        if (view.remainingCooldownMillis > 0L) {
            return SkillInputResult(
                SkillInputStatus.COOLDOWN,
                view.name,
                view.remainingCooldownMillis,
                shouldAlwaysCancel
            )
        }
        val target = resolveTarget(player, view, explicitTarget)
        if (view.targetType != "self" && target == null) {
            return SkillInputResult(SkillInputStatus.NO_TARGET, view.name, cancelEvent = shouldAlwaysCancel)
        }
        val success = cast(player, view.source, view.key, target)
        return SkillInputResult(
            status = if (success) SkillInputStatus.SUCCESS else SkillInputStatus.REJECTED,
            skillName = view.name,
            cancelEvent = shouldAlwaysCancel || success && activation.cancelPolicy == SkillCancelPolicy.ON_SUCCESS
        )
    }

    override fun cast(caster: LivingEntity, skill: NamespacedKey, target: LivingEntity?): Boolean {
        val source = sources.itemSources(caster).toSortedMap().entries.firstOrNull { (_, item) ->
            item.skills.any { it.id == skill }
        }?.key ?: return false
        return cast(caster, source, skill, target)
    }

    override fun cast(
        caster: LivingEntity,
        source: AttributeSourceKey,
        skill: NamespacedKey,
        target: LivingEntity?
    ): Boolean {
        if (!enabled) return false
        check(Bukkit.isPrimaryThread()) { "技能施放必须在 Bukkit 主线程执行" }
        val item = sources.itemSources(caster)[source] ?: return false
        val contribution = item.skills.firstOrNull { it.id == skill } ?: return false
        val definition = definitions.current().snapshot.skills[skill.toString()] ?: return false
        val now = clock()
        val cooldownKey = CooldownKey(caster.uniqueId, source, skill.toString())
        if ((cooldowns[cooldownKey] ?: 0L) > now) return false
        if (!validTarget(caster, target, definition.values["targeting"])) return false

        val prepareEvent = SkillPrepareEvent(caster, skill, target, source, contribution.level, item.overtureItemId)
        Bukkit.getPluginManager().callEvent(prepareEvent)
        if (prepareEvent.isCancelled) return false
        val values = mapOf(
            "skill" to skill.toString(),
            "source" to source.toString(),
            "itemId" to item.overtureItemId,
            "instanceId" to item.instanceId,
            "target" to target,
            "level" to contribution.level
        )
        val context = EntityTriggerContext(UUID.randomUUID(), caster, target, now, values)
        if (triggers.dispatch(SkillCastTrigger, context).cancelled) return false
        if (!aria.invokeSkill(skill.toString(), context)) return false
        val cooldown = (definition.values["cooldown-ms"] as? Number)?.toLong()?.coerceAtLeast(0L) ?: 0L
        cooldowns[cooldownKey] = Math.addExact(now, cooldown)
        if (target != null) triggers.dispatch(SkillHitTrigger, context)
        Bukkit.getPluginManager().callEvent(
            SkillConfirmedEvent(caster, skill, target, source, contribution.level, item.overtureItemId)
        )
        return true
    }

    fun remainingCooldown(entity: LivingEntity, source: AttributeSourceKey, skill: NamespacedKey): Long {
        val key = CooldownKey(entity.uniqueId, source, skill.toString())
        val now = clock()
        val until = cooldowns[key] ?: return 0L
        if (until <= now) {
            cooldowns.remove(key, until)
            return 0L
        }
        return until - now
    }

    fun forget(entityId: UUID) {
        cooldowns.keys.removeIf { it.entityId == entityId }
    }

    fun maintenance(now: Long) {
        cooldowns.entries.removeIf { it.value <= now }
    }

    fun retainSkills(skillIds: Set<String>) {
        cooldowns.keys.removeIf { it.skill !in skillIds }
    }

    fun clear() = cooldowns.clear()

    private fun matchesInputSource(source: AttributeSourceKey, activationSource: SkillActivationSource, hand: EquipmentSlot): Boolean =
        when (activationSource) {
            SkillActivationSource.MAIN_HAND -> hand == EquipmentSlot.HAND && source == AttributeSourceKey("equipment", "main_hand")
            SkillActivationSource.OFF_HAND -> hand == EquipmentSlot.OFF_HAND && source == AttributeSourceKey("equipment", "off_hand")
            SkillActivationSource.ANY -> hand == EquipmentSlot.HAND
        }

    private fun resolveTarget(player: Player, view: SkillView, explicitTarget: LivingEntity?): LivingEntity? {
        if (view.targetType == "self") return player
        if (explicitTarget != null) return explicitTarget
        val range = view.range ?: return null
        return player.world.rayTraceEntities(
            player.eyeLocation,
            player.eyeLocation.direction,
            range,
            0.3
        ) { entity -> entity is LivingEntity && entity != player }?.hitEntity as? LivingEntity
    }

    private fun validTarget(caster: LivingEntity, target: LivingEntity?, raw: Any?): Boolean {
        val targeting = raw as? Map<*, *> ?: return true
        val type = targeting["type"]?.toString() ?: "self"
        val range = (targeting["range"] as? Number)?.toDouble() ?: Double.MAX_VALUE
        return when (type) {
            "self" -> target == null || target == caster
            "single_enemy" -> target != null && target != caster && target.world == caster.world &&
                target.location.distanceSquared(caster.location) <= range * range
            "single_ally" -> target != null && target.world == caster.world &&
                target.location.distanceSquared(caster.location) <= range * range
            else -> false
        }
    }
}
