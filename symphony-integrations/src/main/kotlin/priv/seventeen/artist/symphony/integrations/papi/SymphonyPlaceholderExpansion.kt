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

package priv.seventeen.artist.symphony.integrations.papi

import me.clip.placeholderapi.expansion.PlaceholderExpansion
import org.bukkit.Bukkit
import org.bukkit.NamespacedKey
import org.bukkit.OfflinePlayer
import org.bukkit.entity.LivingEntity
import priv.seventeen.artist.symphony.api.attribute.AttributeDefinition
import priv.seventeen.artist.symphony.api.attribute.AttributeFormat
import priv.seventeen.artist.symphony.api.attribute.AttributeKey
import priv.seventeen.artist.symphony.api.power.CombatPowerSnapshot
import priv.seventeen.artist.symphony.api.service.SymphonyApi
import priv.seventeen.artist.symphony.api.source.ItemSourceSnapshot
import java.math.BigDecimal
import java.math.RoundingMode

class SymphonyPlaceholderExpansion(
    private val api: SymphonyApi,
    private val pluginVersion: String
) : PlaceholderExpansion() {
    override fun getIdentifier() = "symphony"
    override fun getAuthor() = "17Artist"
    override fun getVersion() = pluginVersion
    override fun persist() = true

    override fun onRequest(player: OfflinePlayer?, params: String): String? {
        val entity = player?.player as? LivingEntity ?: return null
        return runCatching { resolve(entity, params) }.getOrNull()
    }

    private fun resolve(entity: LivingEntity, params: String): String? {
        val level = lazy(LazyThreadSafetyMode.NONE) { api.levels.snapshot(entity) }
        val sets = lazy(LazyThreadSafetyMode.NONE) { api.metadata.activeSets(entity) }
        val setDefinitions = lazy(LazyThreadSafetyMode.NONE) { api.definitions.sets() }
        val activeSets = lazy(LazyThreadSafetyMode.NONE) {
            sets.value.filter { (id, pieces) -> setDefinitions.value[id]?.thresholds?.any { it <= pieces } == true }
        }
        val passives = lazy(LazyThreadSafetyMode.NONE) { api.metadata.activePassives(entity) }
        val statuses = lazy(LazyThreadSafetyMode.NONE) { api.metadata.statuses(entity) }
        val auras = lazy(LazyThreadSafetyMode.NONE) { api.metadata.auras(entity) }
        val itemSources = lazy(LazyThreadSafetyMode.NONE) { api.sources.itemSources(entity).values }

        return when {
            params == "combat_power" -> combatPower(entity)?.formatted
            params == "combat_power_value" -> combatPower(entity)?.value?.let(::format)
            params == "combat_power_raw" -> combatPower(entity)?.rawValue?.let(::format)

            params == "level" -> level.value?.level?.toString()
            params == "experience" -> level.value?.experience?.toString()
            params == "level_provider" -> level.value?.provider?.toString()
            params == "character_id" -> level.value?.characterId
            params == "character_name" -> level.value?.characterName

            params == "set_count" -> sets.value.size.toString()
            params == "set_piece_count" -> sets.value.values.sum().toString()
            params == "set_tier_count" -> sets.value.entries.sumOf { (id, pieces) ->
                setDefinitions.value[id]?.thresholds?.count { it <= pieces } ?: 0
            }.toString()
            params == "sets" -> sets.value.keys
                .map { setDefinitions.value[it]?.name ?: it.key }
                .sorted()
                .joinToString(", ")
            params == "set_ids" -> sets.value.keys.map(NamespacedKey::toString).sorted().joinToString(",")

            params == "active_set_count" -> activeSets.value.size.toString()
            params == "active_set_piece_count" -> activeSets.value.values.sum().toString()
            params == "active_set_tier_count" -> sets.value.entries.sumOf { (id, pieces) ->
                setDefinitions.value[id]?.thresholds?.count { it <= pieces } ?: 0
            }.toString()
            params == "active_sets" -> activeSets.value.keys
                .map { setDefinitions.value[it]?.name ?: it.key }
                .sorted()
                .joinToString(", ")
            params == "active_set_ids" -> activeSets.value.keys.map(NamespacedKey::toString).sorted().joinToString(",")

            params == "item_source_count" -> itemSources.value.size.toString()
            params == "skill_count" -> itemSources.value.sumOf { it.skills.size }.toString()
            params == "affix_count" -> itemSources.value.sumOf { it.affixes.size }.toString()
            params == "gem_count" -> itemSources.value.sumOf { it.gems.size }.toString()
            params == "enhancement_total" -> itemSources.value.sumOf(ItemSourceSnapshot::enhancementLevel).toString()
            params == "enhancement_max" -> (itemSources.value.maxOfOrNull(ItemSourceSnapshot::enhancementLevel) ?: 0).toString()

            params == "active_passive_count" -> passives.value.size.toString()
            params == "active_passives" -> {
                val names = api.definitions.passives().associateBy { it.key }
                passives.value.map { names[it]?.name ?: it.key }.sorted().joinToString(", ")
            }
            params == "active_passive_ids" -> passives.value.map(NamespacedKey::toString).sorted().joinToString(",")

            params == "status_count" -> statuses.value.size.toString()
            params == "aura_count" -> auras.value.size.toString()
            params == "in_combat" -> api.metadata.combatState(entity).active.toString()
            params == "combat_remaining_ms" -> api.metadata.combatState(entity).remainingMillis.toString()

            params.startsWith("attribute_formatted_") -> {
                val key = attributeKey(params.removePrefix("attribute_formatted_"))
                val definition = api.definitions.attribute(key)
                formatAttribute(api.attributes.value(entity, key), definition)
            }
            params.startsWith("attribute_") -> {
                val key = attributeKey(params.removePrefix("attribute_"))
                format(api.attributes.value(entity, key))
            }

            params.startsWith("set_") && params.endsWith("_tier_count") -> {
                val id = namespaced(params.removePrefix("set_").removeSuffix("_tier_count")) ?: return null
                val pieces = sets.value[id] ?: 0
                (setDefinitions.value[id]?.thresholds?.count { it <= pieces } ?: 0).toString()
            }
            params.startsWith("set_") && params.endsWith("_tier") -> {
                val id = namespaced(params.removePrefix("set_").removeSuffix("_tier")) ?: return null
                val pieces = sets.value[id] ?: 0
                (setDefinitions.value[id]?.thresholds?.filter { it <= pieces }?.maxOrNull() ?: 0).toString()
            }
            params.startsWith("set_") && params.endsWith("_count") -> {
                val id = namespaced(params.removePrefix("set_").removeSuffix("_count")) ?: return null
                (sets.value[id] ?: 0).toString()
            }
            params.startsWith("set_") && params.endsWith("_active") -> {
                val id = namespaced(params.removePrefix("set_").removeSuffix("_active")) ?: return null
                val pieces = sets.value[id] ?: 0
                (setDefinitions.value[id]?.thresholds?.any { it <= pieces } == true).toString()
            }
            params.startsWith("set_") && params.endsWith("_present") -> {
                val id = namespaced(params.removePrefix("set_").removeSuffix("_present")) ?: return null
                ((sets.value[id] ?: 0) > 0).toString()
            }
            params.startsWith("set_") && params.endsWith("_name") -> {
                val id = namespaced(params.removePrefix("set_").removeSuffix("_name")) ?: return null
                setDefinitions.value[id]?.name
            }

            params.startsWith("resonance_") || params.startsWith("talent_") -> {
                val id = namespaced(params.substringAfter('_')) ?: return null
                (id in passives.value).toString()
            }
            params.startsWith("status_") && params.endsWith("_remaining_ms") -> {
                val id = namespaced(params.removePrefix("status_").removeSuffix("_remaining_ms")) ?: return null
                (statuses.value.firstOrNull { it.id == id }?.remainingMillis ?: 0L).toString()
            }
            params.startsWith("status_") && params.endsWith("_stacks") -> {
                val id = namespaced(params.removePrefix("status_").removeSuffix("_stacks")) ?: return null
                (statuses.value.firstOrNull { it.id == id }?.stacks ?: 0).toString()
            }
            params.startsWith("aura_") && params.endsWith("_remaining_ms") -> {
                val channel = params.removePrefix("aura_").removeSuffix("_remaining_ms")
                (auras.value.firstOrNull { it.channel == channel }?.remainingMillis ?: 0L).toString()
            }
            params.startsWith("aura_") -> {
                val channel = params.removePrefix("aura_")
                format(auras.value.firstOrNull { it.channel == channel }?.gauge ?: 0.0)
            }
            else -> null
        }
    }

    private fun combatPower(entity: LivingEntity): CombatPowerSnapshot? =
        if (Bukkit.isPrimaryThread()) api.combatPower.snapshot(entity)
        else api.combatPower.cached(entity.uniqueId)

    private fun attributeKey(raw: String): AttributeKey =
        AttributeKey(if (':' in raw) raw else "symphony:$raw")

    private fun namespaced(raw: String): NamespacedKey? =
        NamespacedKey.fromString(if (':' in raw) raw else "symphony:$raw")

    private fun formatAttribute(value: Double, definition: AttributeDefinition?): String = when (definition?.format) {
        AttributeFormat.INTEGER -> BigDecimal.valueOf(value).setScale(0, RoundingMode.HALF_UP).toPlainString()
        AttributeFormat.PERCENT -> decimal(value * 100.0, definition.roundingScale) + "%"
        AttributeFormat.NUMBER -> decimal(value, definition.roundingScale)
        null -> format(value)
    }

    private fun decimal(value: Double, scale: Int): String =
        BigDecimal.valueOf(value).setScale(scale, RoundingMode.HALF_UP).stripTrailingZeros().toPlainString()

    private fun format(value: Double): String = BigDecimal.valueOf(value).stripTrailingZeros().toPlainString()
}

object PlaceholderApiIntegration {
    private var expansion: SymphonyPlaceholderExpansion? = null

    @JvmStatic
    fun install(api: SymphonyApi, version: String): Boolean {
        if (expansion != null) return true
        val candidate = SymphonyPlaceholderExpansion(api, version)
        if (!candidate.register()) return false
        expansion = candidate
        return true
    }

    @JvmStatic
    fun uninstall() {
        expansion?.unregister()
        expansion = null
    }
}
