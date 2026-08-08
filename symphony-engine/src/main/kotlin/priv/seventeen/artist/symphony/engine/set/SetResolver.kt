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

package priv.seventeen.artist.symphony.engine.set

import org.bukkit.NamespacedKey
import priv.seventeen.artist.symphony.api.attribute.AttributeModifier
import priv.seventeen.artist.symphony.api.attribute.AttributeSourceKey
import priv.seventeen.artist.symphony.api.source.ItemSourceSnapshot
import priv.seventeen.artist.symphony.api.source.SetThresholdChange
import priv.seventeen.artist.symphony.engine.definition.SetDefinition

data class SetResolution(
    val counts: Map<String, Int>,
    val activeThresholds: Set<Pair<String, Int>>,
    val modifierSources: Map<AttributeSourceKey, List<AttributeModifier>>,
    val thresholdChanges: List<SetThresholdChange>
) {
    companion object {
        val EMPTY = SetResolution(emptyMap(), emptySet(), emptyMap(), emptyList())
    }
}

class SetResolver {
    fun resolve(
        itemSources: Map<AttributeSourceKey, ItemSourceSnapshot>,
        definitions: Map<String, SetDefinition>,
        previousActive: Set<Pair<String, Int>> = emptySet()
    ): SetResolution {
        val counts = linkedMapOf<String, Int>()
        val orderedSources = itemSources.entries.sortedBy { it.key }
        val contributionsBySet = linkedMapOf<String, MutableList<ItemSourceSnapshot>>()
        orderedSources.forEach { (_, source) ->
            source.setPieces.asSequence().map { normalize(it.setId.toString()) }.distinct().forEach { setId ->
                contributionsBySet.getOrPut(setId, ::arrayListOf).add(source)
            }
        }

        definitions.toSortedMap().forEach { (setId, definition) ->
            val seenInstances = hashSetOf<String>()
            val seenPieces = hashSetOf<String>()
            var count = 0

            contributionsBySet[setId].orEmpty().forEach sourceLoop@{ source ->
                val contributions = source.setPieces.filter { normalize(it.setId.toString()) == setId }
                if (contributions.isEmpty()) return@sourceLoop

                val instanceId = source.instanceId
                if (definition.duplicateInstanceOnce && instanceId != null && !seenInstances.add(instanceId)) {
                    return@sourceLoop
                }

                contributions.sortedBy { it.pieceId }.forEach pieceLoop@{ piece ->
                    if (!definition.allowDuplicatePieceId && !seenPieces.add(piece.pieceId)) {
                        return@pieceLoop
                    }
                    count += piece.amount
                }
            }
            counts[setId] = count
        }

        val active = linkedSetOf<Pair<String, Int>>()
        val modifierSources = linkedMapOf<AttributeSourceKey, List<AttributeModifier>>()
        definitions.toSortedMap().forEach { (setId, definition) ->
            val count = counts[setId] ?: 0
            definition.bonuses.toSortedMap().forEach { (threshold, bonus) ->
                if (count >= threshold) {
                    active += setId to threshold
                    if (bonus.modifiers.isNotEmpty()) {
                        val source = AttributeSourceKey("set", "$setId@$threshold")
                        modifierSources[source] = bonus.modifiers.map { modifier ->
                            modifier.copy(id = "$setId:$threshold:${modifier.id}")
                        }
                    }
                }
            }
        }

        val changes = ((active - previousActive).map { it to true } +
            (previousActive - active).map { it to false })
            .sortedWith(compareBy({ it.first.first }, { it.first.second }, { !it.second }))
            .map { (entry, enabled) ->
                val (setId, threshold) = entry
                val key = NamespacedKey.fromString(setId)
                    ?: throw IllegalStateException("编译后的套装 ID 无效：$setId")
                SetThresholdChange(key, threshold, enabled)
            }

        return SetResolution(counts, active, modifierSources, changes)
    }

    private fun normalize(id: String): String = if (':' in id) id else "symphony:$id"
}
