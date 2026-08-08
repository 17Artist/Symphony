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

package priv.seventeen.artist.symphony.bukkit.service

import org.bukkit.Bukkit
import org.bukkit.attribute.Attribute
import org.bukkit.attribute.AttributeModifier
import org.bukkit.entity.LivingEntity
import priv.seventeen.artist.blink.BlinkLog
import priv.seventeen.artist.symphony.api.attribute.AttributeKey
import priv.seventeen.artist.symphony.api.event.AttributeSnapshotCommittedEvent
import priv.seventeen.artist.symphony.api.event.AttributeSnapshotPrepareEvent
import priv.seventeen.artist.symphony.engine.attribute.AttributeStateObserver
import priv.seventeen.artist.symphony.engine.attribute.AttributeCommitBarrier
import priv.seventeen.artist.symphony.engine.attribute.EntityAttributeState
import priv.seventeen.artist.symphony.bukkit.runtime.SymphonyRuntime
import java.util.UUID
import java.util.concurrent.ConcurrentHashMap
import kotlin.math.abs

class BukkitAttributeStateObserver(
    private val commitBarrier: AttributeCommitBarrier = AttributeCommitBarrier.Immediate
) : AttributeStateObserver, AutoCloseable {
    private val synchronizedEntities = ConcurrentHashMap.newKeySet<UUID>()
    @Volatile var afterCommit: ((LivingEntity) -> Unit)? = null
    override fun prepare(entityId: UUID, before: EntityAttributeState, candidate: EntityAttributeState): Boolean {
        val entity = Bukkit.getEntity(entityId) as? LivingEntity ?: return true
        val event = AttributeSnapshotPrepareEvent(entity, before.snapshot, candidate.snapshot)
        Bukkit.getPluginManager().callEvent(event)
        return !event.isCancelled
    }

    override fun committed(entityId: UUID, before: EntityAttributeState, committed: EntityAttributeState) {
        val entity = Bukkit.getEntity(entityId) as? LivingEntity ?: return
        commitBarrier.submit(entityId, committed.revision) {
            val current = Bukkit.getEntity(entityId) as? LivingEntity ?: return@submit
            syncVanilla(current, committed)
        }
        Bukkit.getPluginManager().callEvent(AttributeSnapshotCommittedEvent(entity, before.snapshot, committed.snapshot))
        afterCommit?.invoke(entity)
    }

    private fun syncVanilla(entity: LivingEntity, state: EntityAttributeState) {
        synchronizedEntities += entity.uniqueId
        syncAbsolute(entity, state, MAX_HEALTH, Attribute.GENERIC_MAX_HEALTH, 1.0) { value ->
            if (entity.health > value) entity.health = value
        }
        syncAbsolute(entity, state, MOVEMENT_SPEED, Attribute.GENERIC_MOVEMENT_SPEED, 0.0)
        syncMultiplier(entity, state, ATTACK_SPEED, Attribute.GENERIC_ATTACK_SPEED)
        syncAbsolute(entity, state, KNOCKBACK_RESISTANCE, Attribute.GENERIC_KNOCKBACK_RESISTANCE, 0.0, 1.0)
    }

    private fun syncAbsolute(
        entity: LivingEntity,
        state: EntityAttributeState,
        key: AttributeKey,
        vanilla: Attribute,
        minValue: Double,
        maxValue: Double = Double.MAX_VALUE,
        after: (Double) -> Unit = {}
    ) {
        val instance = entity.getAttribute(vanilla) ?: return
        runCatching {
            val modifierId = modifierId(vanilla)
            val existing = instance.modifiers.firstOrNull { it.uniqueId == modifierId }
            val directive = VanillaAttributeSyncPolicy.directive(state, key, VanillaSyncMode.ABSOLUTE)
            if (directive is VanillaSyncDirective.Clear) {
                existing?.let(instance::removeModifier)
                after(instance.value.coerceIn(minValue, maxValue))
                return@runCatching
            }
            val value = (directive as VanillaSyncDirective.Absolute).value.coerceIn(minValue, maxValue)
            if (kotlin.math.abs(instance.value - value) <= EPSILON) {
                after(value)
                return@runCatching
            }
            existing?.let(instance::removeModifier)
            val withoutSymphony = instance.value
            val delta = value - withoutSymphony
            if (kotlin.math.abs(delta) > EPSILON) {
                instance.addModifier(AttributeModifier(modifierId, MODIFIER_NAME, delta, AttributeModifier.Operation.ADD_NUMBER))
            }
            after(value)
        }.onFailure { BlinkLog.error(SymphonyRuntime.language().text("console.vanilla-sync-failed", "attribute" to vanilla, "entity" to entity.uniqueId), it) }
    }

    private fun syncMultiplier(
        entity: LivingEntity,
        state: EntityAttributeState,
        key: AttributeKey,
        vanilla: Attribute
    ) {
        val instance = entity.getAttribute(vanilla) ?: return
        runCatching {
            val modifierId = modifierId(vanilla)
            val existing = instance.modifiers.firstOrNull { it.uniqueId == modifierId }
            when (val directive = VanillaAttributeSyncPolicy.directive(state, key, VanillaSyncMode.MULTIPLY_TOTAL)) {
                is VanillaSyncDirective.Clear -> existing?.let(instance::removeModifier)
                is VanillaSyncDirective.MultiplyTotal -> {
                    if (
                        existing?.operation == AttributeModifier.Operation.MULTIPLY_SCALAR_1 &&
                        kotlin.math.abs(existing.amount - directive.amount) <= EPSILON
                    ) return@runCatching
                    existing?.let(instance::removeModifier)
                    if (kotlin.math.abs(directive.amount) > EPSILON) {
                        instance.addModifier(AttributeModifier(
                            modifierId,
                            MODIFIER_NAME,
                            directive.amount,
                            AttributeModifier.Operation.MULTIPLY_SCALAR_1
                        ))
                    }
                }
                is VanillaSyncDirective.Absolute -> error("攻击速度不能使用绝对值同步指令")
            }
        }.onFailure { BlinkLog.error(SymphonyRuntime.language().text("console.vanilla-sync-failed", "attribute" to vanilla, "entity" to entity.uniqueId), it) }
    }

    override fun close() {
        afterCommit = null
        synchronizedEntities.forEach { entityId ->
            (Bukkit.getEntity(entityId) as? LivingEntity)?.let(::clearVanilla)
        }
        synchronizedEntities.clear()
    }

    fun forget(entityId: UUID, entity: LivingEntity? = Bukkit.getEntity(entityId) as? LivingEntity) {
        if (!synchronizedEntities.remove(entityId)) return
        entity?.let(::clearVanilla)
    }

    internal fun trackedEntityCount(): Int = synchronizedEntities.size

    private fun clearVanilla(entity: LivingEntity) {
        SYNCED_ATTRIBUTES.forEach { attribute ->
            entity.getAttribute(attribute)?.let { instance ->
                instance.modifiers.firstOrNull { it.uniqueId == modifierId(attribute) }?.let(instance::removeModifier)
            }
        }
    }

    private fun modifierId(attribute: Attribute): UUID = UUID.nameUUIDFromBytes("symphony:vanilla-sync:${attribute.name}".toByteArray())

    companion object {
        private val MAX_HEALTH = AttributeKey.symphony("max_health")
        private val MOVEMENT_SPEED = AttributeKey.symphony("movement_speed")
        private val ATTACK_SPEED = AttributeKey.symphony("attack_speed")
        private val KNOCKBACK_RESISTANCE = AttributeKey.symphony("knockback_resistance")
        private const val MODIFIER_NAME = "symphony.vanilla_sync"
        private val SYNCED_ATTRIBUTES = listOf(
            Attribute.GENERIC_MAX_HEALTH,
            Attribute.GENERIC_MOVEMENT_SPEED,
            Attribute.GENERIC_ATTACK_SPEED,
            Attribute.GENERIC_KNOCKBACK_RESISTANCE
        )
        private const val EPSILON = 1.0e-9
    }
}

internal enum class VanillaSyncMode { ABSOLUTE, MULTIPLY_TOTAL }

internal sealed interface VanillaSyncDirective {
    object Clear : VanillaSyncDirective
    data class Absolute(val value: Double) : VanillaSyncDirective
    data class MultiplyTotal(val amount: Double) : VanillaSyncDirective
}

/** 独立的纯决策层，防止中性或默认属性在无提示的情况下接管原版属性。 */
internal object VanillaAttributeSyncPolicy {
    fun directive(state: EntityAttributeState, key: AttributeKey, mode: VanillaSyncMode): VanillaSyncDirective {
        val value = state.snapshot.values[key] ?: return VanillaSyncDirective.Clear
        val base = state.explanations[key]?.base ?: return VanillaSyncDirective.Clear
        return directive(base, value, mode)
    }

    fun directive(base: Double, value: Double, mode: VanillaSyncMode): VanillaSyncDirective {
        if (abs(value - base) <= 1.0e-9) return VanillaSyncDirective.Clear
        return when (mode) {
            VanillaSyncMode.ABSOLUTE -> VanillaSyncDirective.Absolute(value)
            VanillaSyncMode.MULTIPLY_TOTAL -> VanillaSyncDirective.MultiplyTotal(value - 1.0)
        }
    }
}
