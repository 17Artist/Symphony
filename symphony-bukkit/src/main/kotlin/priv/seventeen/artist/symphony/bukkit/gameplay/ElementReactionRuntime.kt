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

import org.bukkit.entity.LivingEntity
import priv.seventeen.artist.symphony.api.damage.DamageRequest
import priv.seventeen.artist.symphony.bukkit.combat.DamageReactionPlan
import priv.seventeen.artist.symphony.bukkit.combat.DamageReactionPlanner
import priv.seventeen.artist.symphony.bukkit.compat.BukkitEffectTypes
import priv.seventeen.artist.symphony.engine.definition.DefinitionRepository
import java.util.UUID
import java.util.concurrent.ConcurrentHashMap

data class AuraSnapshot(val channel: String, val gauge: Double, val expiresAtMillis: Long)

class ElementReactionRuntime(
    private val definitions: DefinitionRepository,
    private val enabled: Boolean = true,
    private val clock: () -> Long = System::currentTimeMillis,
    private val onEffectFailure: (String, Throwable) -> Unit = { _, _ -> }
) : DamageReactionPlanner, AutoCloseable {
    private data class Aura(val gauge: Double, val expiresAtMillis: Long)
    private data class ReactionMatch(
        val id: String,
        val incoming: String,
        val aura: String,
        val consume: Double,
        val effects: Map<*, *>
    )

    private val auras = ConcurrentHashMap<UUID, ConcurrentHashMap<String, Aura>>()

    override fun prepare(
        request: DamageRequest,
        victim: LivingEntity,
        channels: Map<String, Double>
    ): DamageReactionPlan {
        if (!enabled) return DamageReactionPlan(channels)
        val now = clock()
        prune(victim.uniqueId, now)
        val adjusted = channels.toMutableMap()
        val matches = mutableListOf<ReactionMatch>()
        definitions.current().snapshot.reactions.toSortedMap().forEach { (id, definition) ->
            val trigger = definition.values["trigger"]?.toString() ?: return@forEach
            val auraId = definition.values["aura"]?.toString() ?: return@forEach
            val input = adjusted[trigger] ?: return@forEach
            val aura = auras[victim.uniqueId]?.get(auraId) ?: return@forEach
            if (aura.gauge <= 0.0) return@forEach
            val type = definition.values["type"]?.toString() ?: "amplify"
            val multiplier = (definition.values["multiplier"] as? Number)?.toDouble() ?: 1.0
            adjusted[trigger] = when (type) {
                "amplify" -> input * multiplier
                "add" -> input + multiplier
                else -> throw IllegalArgumentException("元素反应 $id 使用了不支持的类型 $type")
            }
            val consume = (definition.values["gauge-consume"] as? Number)?.toDouble()?.coerceAtLeast(0.0) ?: 0.0
            matches += ReactionMatch(id, trigger, auraId, consume, definition.values["effects"] as? Map<*, *> ?: emptyMap<Any, Any>())
        }
        return DamageReactionPlan(
            adjusted.toMap(),
            matches.groupBy(ReactionMatch::incoming).mapValues { (_, values) -> values.mapTo(linkedSetOf(), ReactionMatch::id) }
        ) {
            val entityAuras = auras.computeIfAbsent(victim.uniqueId) { ConcurrentHashMap() }
            matches.forEach { match ->
                entityAuras.computeIfPresent(match.aura) { _, aura ->
                    aura.copy(gauge = (aura.gauge - match.consume).coerceAtLeast(0.0))
                }
            }
            channels.keys.filter { channel -> definitions.current().snapshot.damageChannels[channel]?.element == true }
                .forEach { channel ->
                    entityAuras.compute(channel) { _, aura ->
                        Aura(
                            ((aura?.gauge ?: 0.0) + DEFAULT_GAUGE).coerceAtMost(MAX_GAUGE),
                            now + DEFAULT_AURA_DURATION_MILLIS
                        )
                    }
                }
            entityAuras.entries.removeIf { it.value.gauge <= 0.0 || it.value.expiresAtMillis <= now }
            if (entityAuras.isEmpty()) auras.remove(victim.uniqueId)
            matches.forEach { match ->
                runCatching { playEffects(victim, match.effects) }
                    .onFailure { onEffectFailure(match.id, it) }
            }
        }
    }

    fun snapshots(entity: LivingEntity): List<AuraSnapshot> {
        if (!enabled) return emptyList()
        val now = clock()
        return auras[entity.uniqueId].orEmpty().asSequence()
            .filter { (_, aura) -> aura.gauge > 0.0 && aura.expiresAtMillis > now }
            .map { (channel, aura) ->
            AuraSnapshot(channel, aura.gauge, aura.expiresAtMillis)
            }.sortedBy { it.channel }.toList()
    }

    fun forget(entityId: UUID) {
        auras.remove(entityId)
    }

    fun maintenance(now: Long = clock()) {
        auras.keys.forEach { prune(it, now) }
    }

    internal fun trackedEntityCount(): Int = auras.size

    private fun prune(entityId: UUID, now: Long) {
        val values = auras[entityId] ?: return
        values.entries.removeIf { it.value.expiresAtMillis <= now || it.value.gauge <= 0.0 }
        if (values.isEmpty()) auras.remove(entityId)
    }

    private fun playEffects(victim: LivingEntity, effects: Map<*, *>) {
        effects["particle"]?.toString()?.let { raw ->
            val particle = BukkitEffectTypes.particle(raw)
            victim.world.spawnParticle(particle, victim.location.clone().add(0.0, victim.height * 0.5, 0.0), 1)
        }
        effects["sound"]?.toString()?.let { raw ->
            val sound = BukkitEffectTypes.sound(raw)
            victim.world.playSound(victim.location, sound, 1.0f, 1.0f)
        }
    }

    override fun close() = auras.clear()

    companion object {
        private const val DEFAULT_GAUGE = 1.0
        private const val MAX_GAUGE = 4.0
        private const val DEFAULT_AURA_DURATION_MILLIS = 10_000L
    }
}
