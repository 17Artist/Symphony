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

import org.bukkit.NamespacedKey
import org.bukkit.plugin.Plugin
import priv.seventeen.artist.symphony.api.attribute.AttributeDefinition
import priv.seventeen.artist.symphony.api.attribute.AttributeKey
import priv.seventeen.artist.symphony.api.registration.RegistrationHandle
import priv.seventeen.artist.symphony.api.service.DefinitionService
import priv.seventeen.artist.symphony.api.service.SetBonusView
import priv.seventeen.artist.symphony.api.service.SetDefinitionView
import priv.seventeen.artist.symphony.api.service.PassiveDefinitionView
import priv.seventeen.artist.symphony.engine.definition.CompiledAttributeDefinition
import priv.seventeen.artist.symphony.engine.definition.DefinitionRepository
import priv.seventeen.artist.symphony.engine.definition.DefinitionSnapshot

class BukkitDefinitionService(
    private val repository: DefinitionRepository,
    initialBase: DefinitionSnapshot
) : DefinitionService {
    private val extensions = OwnedPriorityRegistry<AttributeDefinition>("attribute-definition")

    @Volatile
    private var base: DefinitionSnapshot = initialBase

    override val revision: Long get() = repository.current().snapshot.revision

    override fun attribute(key: AttributeKey): AttributeDefinition? =
        repository.current().snapshot.attributes[key]?.definition

    override fun attributes(): Map<AttributeKey, AttributeDefinition> =
        repository.current().snapshot.attributes.mapValues { it.value.definition }

    override fun sets(): Map<NamespacedKey, SetDefinitionView> = repository.current().snapshot.sets.values
        .sortedBy { it.id }.associate { definition ->
            val key = requireNotNull(NamespacedKey.fromString(definition.id))
            key to SetDefinitionView(
                key,
                definition.name,
                definition.pieces,
                definition.bonuses.toSortedMap().map { (threshold, bonus) ->
                    SetBonusView(threshold, bonus.display.name, bonus.display.description)
                }
            )
        }

    override fun passives(): Set<PassiveDefinitionView> {
        val snapshot = repository.current().snapshot
        return buildSet {
            snapshot.resonances.values.sortedBy { it.id }.forEach {
                add(PassiveDefinitionView(requireNotNull(NamespacedKey.fromString(it.id)), "resonance", it.values["name"]?.toString() ?: it.id.substringAfter(':')))
            }
            snapshot.talents.values.sortedBy { it.id }.forEach {
                add(PassiveDefinitionView(requireNotNull(NamespacedKey.fromString(it.id)), "talent", it.values["name"]?.toString() ?: it.id.substringAfter(':')))
            }
        }
    }

    override fun registerAttribute(
        owner: Plugin,
        definition: AttributeDefinition,
        priority: Int
    ): RegistrationHandle {
        val key = NamespacedKey.fromString(definition.key.value)
            ?: throw IllegalArgumentException("属性键无法转换为 Bukkit NamespacedKey：${definition.key}")
        return extensions.register(owner, key, priority, definition, ::rebuild)
    }

    @Synchronized
    fun reloadBase(candidate: DefinitionSnapshot): DefinitionSnapshot {
        base = candidate
        return rebuild()
    }

    @Synchronized
    fun baseSnapshot(): DefinitionSnapshot = base

    fun removeOwner(owner: Plugin) = extensions.closeOwner(owner, ::rebuild)

    fun clearExtensions() = extensions.clear()

    @Synchronized
    private fun rebuild(): DefinitionSnapshot {
        val merged = LinkedHashMap(base.attributes)
        extensions.active().forEach { entry ->
            merged[entry.value.key] = CompiledAttributeDefinition(entry.value, emptyList())
        }
        return repository.commit(base.copy(attributes = merged)).snapshot
    }
}
