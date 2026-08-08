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
import org.bukkit.plugin.Plugin
import priv.seventeen.artist.symphony.api.event.LevelChangeEvent
import priv.seventeen.artist.symphony.api.level.LevelProvider
import priv.seventeen.artist.symphony.api.level.LevelService
import priv.seventeen.artist.symphony.api.level.LevelSnapshot
import priv.seventeen.artist.symphony.api.registration.RegistrationHandle
import priv.seventeen.artist.symphony.engine.trigger.EntityTriggerContext
import priv.seventeen.artist.symphony.engine.trigger.PlayerLevelUpTrigger
import java.util.UUID
import java.util.concurrent.ConcurrentHashMap

class BukkitLevelService(
    private val attributes: BukkitAttributeService,
    private val triggers: BukkitTriggerService,
    private val clock: () -> Long = System::currentTimeMillis
) : LevelService {
    private val providers = OwnedPriorityRegistry<LevelProvider>("level-provider")
    private val observed = ConcurrentHashMap<UUID, LevelSnapshot>()

    override fun snapshot(entity: LivingEntity): LevelSnapshot? = resolve(entity)

    override fun refresh(entity: LivingEntity, reason: String): LevelSnapshot? {
        check(Bukkit.isPrimaryThread()) { "等级刷新必须在 Bukkit 主线程执行" }
        val previous = observed[entity.uniqueId]
        val current = resolve(entity)
        if (current == null) observed.remove(entity.uniqueId) else observed[entity.uniqueId] = current
        if (previous == current) return current
        Bukkit.getPluginManager().callEvent(LevelChangeEvent(entity, previous, current, reason.ifBlank { "provider.refresh" }))
        attributes.recalculate(entity)
        if (current != null && current.level > (previous?.level ?: 0)) {
            triggers.dispatch(
                PlayerLevelUpTrigger,
                EntityTriggerContext(
                    UUID.randomUUID(),
                    entity,
                    null,
                    clock(),
                    mapOf(
                        "previousLevel" to (previous?.level ?: 0),
                        "level" to current.level,
                        "provider" to current.provider.toString(),
                        "characterId" to current.characterId,
                        "reason" to reason
                    )
                )
            )
        }
        return current
    }

    override fun registerProvider(owner: Plugin, provider: LevelProvider, priority: Int): RegistrationHandle =
        providers.register(owner, provider.id, priority, provider) { observed.clear() }

    fun remove(entity: LivingEntity) {
        remove(entity.uniqueId)
    }

    fun remove(entityId: UUID) { observed.remove(entityId) }

    fun removeOwner(owner: Plugin) = providers.closeOwner(owner) { observed.clear() }

    fun clearProviders() = providers.clear()

    fun clear() {
        observed.clear()
    }

    private fun resolve(entity: LivingEntity): LevelSnapshot? = providers.active()
        .sortedWith(compareByDescending<RegistryEntry<LevelProvider>> { it.priority }.thenByDescending { it.sequence })
        .firstNotNullOfOrNull { entry ->
            entry.value.snapshot(entity)?.let { provided ->
                LevelSnapshot(
                    entry.key,
                    entry.value.displayName,
                    provided.level,
                    provided.experience,
                    provided.experienceForNextLevel,
                    provided.characterId,
                    provided.characterName,
                    provided.metadata.toMap()
                )
            }
        }
}
