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
import org.bukkit.entity.LivingEntity
import priv.seventeen.artist.symphony.api.service.ActiveAffix
import priv.seventeen.artist.symphony.api.service.AffixService
import priv.seventeen.artist.symphony.api.service.AuraView
import priv.seventeen.artist.symphony.api.service.CombatStateView
import priv.seventeen.artist.symphony.api.service.MetadataService
import priv.seventeen.artist.symphony.api.service.RuntimeStatusView
import priv.seventeen.artist.symphony.bukkit.gameplay.ElementReactionRuntime
import priv.seventeen.artist.symphony.bukkit.gameplay.PassiveRuleRuntime
import priv.seventeen.artist.symphony.bukkit.gameplay.StatusRuntime
import priv.seventeen.artist.symphony.bukkit.combat.BukkitDamageService
import priv.seventeen.artist.symphony.engine.attribute.AttributeStateStore
import priv.seventeen.artist.symphony.engine.definition.DefinitionRepository

class BukkitMetadataService(
    private val statuses: StatusRuntime,
    private val reactions: ElementReactionRuntime,
    private val passives: PassiveRuleRuntime,
    private val store: AttributeStateStore,
    private val damage: BukkitDamageService,
    private val clock: () -> Long = System::currentTimeMillis
) : MetadataService {
    override fun combatState(entity: LivingEntity): CombatStateView = CombatStateView(
        damage.isInCombat(entity),
        damage.combatRemainingMillis(entity)
    )

    override fun statuses(entity: LivingEntity): List<RuntimeStatusView> {
        val now = clock()
        return statuses.snapshots(entity).mapNotNull { status ->
            NamespacedKey.fromString(status.id)?.let { RuntimeStatusView(it, status.stacks, (status.nextExpiryMillis - now).coerceAtLeast(0L)) }
        }
    }

    override fun auras(entity: LivingEntity): List<AuraView> {
        val now = clock()
        return reactions.snapshots(entity).map { AuraView(it.channel, it.gauge, (it.expiresAtMillis - now).coerceAtLeast(0L)) }
    }

    override fun activeSets(entity: LivingEntity): Map<NamespacedKey, Int> =
        store.stateIfPresent(entity.uniqueId)?.setResolution?.counts.orEmpty().mapNotNull { (id, count) ->
            NamespacedKey.fromString(id)?.let { it to count }
        }.toMap()

    override fun activePassives(entity: LivingEntity): Set<NamespacedKey> =
        passives.active(entity).mapNotNullTo(linkedSetOf(), NamespacedKey::fromString)
}

class BukkitAffixService(
    private val definitions: DefinitionRepository,
    private val store: AttributeStateStore,
    private val enabled: Boolean = true
) : AffixService {
    override fun knownAffixes(): Set<NamespacedKey> =
        if (!enabled) emptySet() else definitions.current().snapshot.affixes.keys.mapNotNullTo(linkedSetOf(), NamespacedKey::fromString)

    override fun activeAffixes(entity: LivingEntity): List<ActiveAffix> =
        if (!enabled) emptyList() else store.stateIfPresent(entity.uniqueId)?.sources?.values.orEmpty().asSequence().mapNotNull { it.item }.flatMap { it.affixes.asSequence() }
            .map { ActiveAffix(it.id, it.level, it.parameters) }
            .sortedWith(compareBy({ it.id.toString() }, { it.level }))
            .toList()
}
