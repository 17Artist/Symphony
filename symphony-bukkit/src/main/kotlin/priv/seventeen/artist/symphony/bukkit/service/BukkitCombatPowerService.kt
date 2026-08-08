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
import org.bukkit.entity.LivingEntity
import priv.seventeen.artist.symphony.api.attribute.AttributeKey
import priv.seventeen.artist.symphony.api.power.CombatPowerService
import priv.seventeen.artist.symphony.api.power.CombatPowerSnapshot
import priv.seventeen.artist.symphony.engine.attribute.AttributeStateStore
import priv.seventeen.artist.symphony.engine.attribute.EntityAttributeState
import priv.seventeen.artist.symphony.engine.definition.CombatPowerDefinition
import priv.seventeen.artist.symphony.engine.definition.DefinitionRepository
import priv.seventeen.artist.symphony.engine.definition.DefinitionSnapshot
import java.math.BigDecimal
import java.text.DecimalFormat
import java.text.DecimalFormatSymbols
import java.util.Collections
import java.util.Locale
import java.util.UUID
import java.util.concurrent.ConcurrentHashMap

class BukkitCombatPowerService(
    private val definitions: DefinitionRepository,
    private val store: AttributeStateStore,
    private val levels: BukkitLevelService,
    private val clock: () -> Long = System::currentTimeMillis,
    private val onError: (LivingEntity, Throwable) -> Unit = { _, _ -> }
) : CombatPowerService, AutoCloseable {
    private val cache = ConcurrentHashMap<UUID, CombatPowerSnapshot>()

    override fun snapshot(entity: LivingEntity): CombatPowerSnapshot {
        val state = store.state(entity.uniqueId)
        val definitionSnapshot = definitions.current().snapshot
        val definitionRevision = definitionSnapshot.revision
        cache[entity.uniqueId]?.takeIf {
            it.attributeRevision == state.revision && it.definitionRevision == definitionRevision
        }?.let { return it }

        val definition = definitionSnapshot.combatPower
        if (!Bukkit.isPrimaryThread() && definition.expression.variables.any(LEVEL_VARIABLES::contains)) {
            throw IllegalStateException("战力必须先在 Bukkit 主线程完成一次计算")
        }
        return calculate(entity, definition, definitionSnapshot, state).also { cache[entity.uniqueId] = it }
    }

    fun refresh(entity: LivingEntity): CombatPowerSnapshot {
        invalidate(entity.uniqueId)
        return snapshot(entity)
    }

    /** 保持已观察对象的战力为最新值，同时避免预先缓存所有带属性的生物。 */
    fun refreshIfCached(entity: LivingEntity) {
        if (cache.containsKey(entity.uniqueId)) refresh(entity)
    }

    override fun cached(entityId: UUID): CombatPowerSnapshot? = cache[entityId]

    override fun invalidate(entityId: UUID) {
        cache.remove(entityId)
    }

    fun invalidateAll(entityIds: Iterable<UUID>) {
        entityIds.forEach(cache::remove)
    }

    override fun close() {
        cache.clear()
    }

    internal fun cacheSize(): Int = cache.size

    private fun calculate(
        entity: LivingEntity,
        definition: CombatPowerDefinition,
        definitions: DefinitionSnapshot,
        state: EntityAttributeState
    ): CombatPowerSnapshot {
        var variables: Map<String, Double> = emptyMap()
        return try {
            variables = buildVariables(entity, definition, definitions, state)
            if (!definition.enabled) {
                return snapshot(entity, state, definition, definitions.revision, variables, 0.0, 0.0)
            }
            val raw = definition.expression.evaluate(variables)
            val bounded = raw.coerceIn(definition.minimum, definition.maximum)
            val rounded = BigDecimal.valueOf(bounded).setScale(definition.scale, definition.roundingMode).toDouble()
            snapshot(entity, state, definition, definitions.revision, variables, raw, rounded)
        } catch (error: Throwable) {
            runCatching { onError(entity, error) }
            snapshot(
                entity,
                state,
                definition,
                definitions.revision,
                variables,
                0.0,
                0.0,
                error.message ?: error.javaClass.simpleName
            )
        }
    }

    private fun buildVariables(
        entity: LivingEntity,
        definition: CombatPowerDefinition,
        definitions: DefinitionSnapshot,
        state: EntityAttributeState
    ): Map<String, Double> {
        val itemSources = state.sources.values.mapNotNull { it.item }
        val level = if (definition.expression.variables.any(LEVEL_VARIABLES::contains)) levels.snapshot(entity) else null
        val values = linkedMapOf<String, Double>()
        definition.expression.variables.sorted().forEach { variable ->
            values[variable] = when {
                variable == "level" -> level?.level?.toDouble() ?: 0.0
                variable == "experience" -> level?.experience?.toDouble() ?: 0.0
                variable == "source_count" -> state.sources.size.toDouble()
                variable == "item_source_count" -> itemSources.size.toDouble()
                variable == "set_count" -> state.setResolution.counts.count { it.value > 0 }.toDouble()
                variable == "set_piece_count" -> state.setResolution.counts.values.sum().toDouble()
                variable == "set_tier_count" -> state.setResolution.activeThresholds.size.toDouble()
                variable == "skill_count" -> itemSources.sumOf { it.skills.size }.toDouble()
                variable == "affix_count" -> itemSources.sumOf { it.affixes.size }.toDouble()
                variable == "gem_count" -> itemSources.sumOf { it.gems.size }.toDouble()
                variable == "enhancement_total" -> itemSources.sumOf { it.enhancementLevel }.toDouble()
                variable == "enhancement_max" -> itemSources.maxOfOrNull { it.enhancementLevel }?.toDouble() ?: 0.0
                variable.startsWith("attribute:") -> {
                    val raw = variable.removePrefix("attribute:")
                    val key = AttributeKey(
                        if (':' in raw) raw else "symphony:$raw"
                    )
                    state.snapshot.values[key]
                        ?: definitions.attributes[key]?.definition?.base
                        ?: 0.0
                }
                variable.startsWith("set_pieces:") -> {
                    val raw = variable.removePrefix("set_pieces:")
                    state.setResolution.counts[if (':' in raw) raw else "symphony:$raw"]?.toDouble() ?: 0.0
                }
                variable.startsWith("set_tiers:") -> {
                    val raw = variable.removePrefix("set_tiers:")
                    val id = if (':' in raw) raw else "symphony:$raw"
                    state.setResolution.activeThresholds.count { it.first == id }.toDouble()
                }
                else -> 0.0
            }
        }
        return Collections.unmodifiableMap(values)
    }

    private fun snapshot(
        entity: LivingEntity,
        state: EntityAttributeState,
        definition: CombatPowerDefinition,
        definitionRevision: Long,
        variables: Map<String, Double>,
        raw: Double,
        value: Double,
        error: String? = null
    ): CombatPowerSnapshot = CombatPowerSnapshot(
        entity.uniqueId,
        raw,
        value,
        formatter(definition).format(value),
        state.revision,
        definitionRevision,
        variables,
        clock(),
        error
    )

    private fun formatter(definition: CombatPowerDefinition): DecimalFormat =
        DecimalFormat(definition.formatPattern, DecimalFormatSymbols.getInstance(Locale.ROOT)).apply {
            roundingMode = definition.roundingMode
            isGroupingUsed = definition.formatPattern.contains(',')
        }

    companion object {
        private val LEVEL_VARIABLES = setOf("level", "experience")
    }
}
