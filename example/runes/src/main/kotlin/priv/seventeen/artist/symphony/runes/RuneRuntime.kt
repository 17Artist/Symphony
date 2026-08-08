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

package priv.seventeen.artist.symphony.runes

import org.bukkit.Bukkit
import org.bukkit.entity.Player
import org.bukkit.plugin.java.JavaPlugin
import priv.seventeen.artist.blink.BlinkLog
import priv.seventeen.artist.symphony.api.attribute.AttributeKey
import priv.seventeen.artist.symphony.api.attribute.AttributeSourceKey
import priv.seventeen.artist.symphony.api.event.LevelChangeEvent
import priv.seventeen.artist.symphony.api.registration.RegistrationHandle
import priv.seventeen.artist.symphony.api.service.SymphonyApi
import priv.seventeen.artist.symphony.api.source.SourceUpdateResult
import priv.seventeen.artist.symphony.runes.config.RuneCatalogLoader
import priv.seventeen.artist.symphony.runes.model.PlayerRuneState
import priv.seventeen.artist.symphony.runes.model.RuneActivationState
import priv.seventeen.artist.symphony.runes.model.RuneCatalog
import priv.seventeen.artist.symphony.runes.model.RuneMutationFailure
import priv.seventeen.artist.symphony.runes.model.RuneMutationResult
import priv.seventeen.artist.symphony.runes.model.RuneSlotDefinition
import priv.seventeen.artist.symphony.runes.model.RuneSlotStatus
import priv.seventeen.artist.symphony.runes.storage.PlayerRuneRepository
import priv.seventeen.artist.symphony.runes.text.Messages
import java.util.UUID
import java.util.concurrent.ConcurrentHashMap

object RuneRuntime {
    private lateinit var plugin: JavaPlugin
    private lateinit var api: SymphonyApi
    private lateinit var catalog: RuneCatalog
    private lateinit var repository: PlayerRuneRepository
    private lateinit var messages: Messages
    private val definitionHandles = mutableListOf<RegistrationHandle>()
    private val trackedSlots = ConcurrentHashMap<UUID, Set<String>>()
    private var enabled = false
    private val ownerNamespace get() = plugin.name.lowercase().replace(Regex("[^a-z0-9._-]"), "_")

    fun load(plugin: JavaPlugin) {
        this.plugin = plugin
        saveDefault("config.yml")
        saveDefault("attributes.yml")
        saveDefault("language.yml")
        saveDefault("runes/ember_sigil.yml")
        saveDefault("runes/glacial_ward.yml")
        saveDefault("runes/prismatic_convergence.yml")
    }

    fun enable() {
        api = Bukkit.getServicesManager().load(SymphonyApi::class.java)
            ?: error("SymphonyApi 服务不可用")
        messages = Messages(plugin.dataFolder.resolve("language.yml"))
        repository = PlayerRuneRepository(plugin.dataFolder.toPath().resolve("players"))
        catalog = RuneCatalogLoader(plugin).load()
        try {
            definitionHandles += registerDefinitions(catalog)
            validateAttributes(catalog)
        } catch (error: Throwable) {
            definitionHandles.asReversed().forEach(RegistrationHandle::close)
            definitionHandles.clear()
            throw error
        }
        enabled = true
        Bukkit.getOnlinePlayers().forEach(::loadPlayer)
        BlinkLog.success(message("console.enabled", "runes" to catalog.runes.size, "slots" to catalog.slots.size))
    }

    fun disable() {
        if (!enabled) return
        Bukkit.getOnlinePlayers().forEach(::clearPlayerSources)
        repository.saveAll()
        definitionHandles.asReversed().forEach(RegistrationHandle::close)
        definitionHandles.clear()
        trackedSlots.clear()
        enabled = false
        BlinkLog.info(message("console.disabled"))
    }

    fun reload(): Result<Unit> = runCatching {
        val candidate = RuneCatalogLoader(plugin).load()
        val previous = catalog
        definitionHandles.asReversed().forEach(RegistrationHandle::close)
        definitionHandles.clear()
        try {
            definitionHandles += registerDefinitions(candidate)
            validateAttributes(candidate)
            catalog = candidate
        } catch (error: Throwable) {
            definitionHandles.asReversed().forEach(RegistrationHandle::close)
            definitionHandles.clear()
            definitionHandles += registerDefinitions(previous)
            validateAttributes(previous)
            throw error
        }
        messages.reload()
        Bukkit.getOnlinePlayers().forEach { syncPlayer(it, "reload") }
    }

    fun loadPlayer(player: Player) {
        repository.load(player.uniqueId)
        syncPlayer(player, "join")
    }

    fun unloadPlayer(player: Player) {
        clearPlayerSources(player)
        repository.unload(player.uniqueId)
    }

    fun onLevelChange(event: LevelChangeEvent) {
        val player = event.entity as? Player ?: return
        syncPlayer(player, "level:${event.reason}")
    }

    fun state(player: Player): PlayerRuneState = repository.load(player.uniqueId)
    fun runeIds(): Set<String> = catalog.runes.keys
    fun slotIds(): Set<String> = catalog.slots.keys
    fun rune(id: String) = catalog.runes[id]
    fun attributeName(key: AttributeKey): String =
        api.definitions.attribute(key)?.name ?: message("common.unknown-attribute")
    fun categoryName(id: String): String = messages.optional("categories.$id", message("categories.other"))
    fun operationName(id: String): String = messages.optional("operations.$id", message("operations.other"))

    fun statuses(player: Player): List<RuneSlotStatus> {
        val state = repository.load(player.uniqueId)
        val level = api.levels.snapshot(player)?.level
        return catalog.slots.values.sortedBy { it.id }.map { slot -> status(slot, state, level) }
    }

    fun grant(player: Player, runeId: String, rank: Int): RuneMutationResult {
        val rune = catalog.runes[runeId] ?: return RuneMutationResult.Failure(RuneMutationFailure.RUNE_NOT_FOUND)
        if (rank !in 1..rune.maximumRank) return RuneMutationResult.Failure(RuneMutationFailure.RUNE_NOT_FOUND)
        repository.update(player.uniqueId) { state ->
            state.copy(unlocked = state.unlocked + (runeId to maxOf(rank, state.unlocked[runeId] ?: 0)))
        }
        val rejected = syncPlayer(player, "grant")
        return if (rejected == null) RuneMutationResult.Success()
        else RuneMutationResult.Failure(RuneMutationFailure.SOURCE_REJECTED, detail = rejected)
    }

    fun revoke(player: Player, runeId: String): RuneMutationResult {
        if (runeId !in repository.load(player.uniqueId).unlocked) return RuneMutationResult.Failure(RuneMutationFailure.NOT_UNLOCKED)
        repository.update(player.uniqueId) { state ->
            state.copy(
                unlocked = state.unlocked - runeId,
                equipped = state.equipped.filterValues { it != runeId }
            )
        }
        val rejected = syncPlayer(player, "revoke")
        return if (rejected == null) RuneMutationResult.Success()
        else RuneMutationResult.Failure(RuneMutationFailure.SOURCE_REJECTED, detail = rejected)
    }

    fun equip(player: Player, slotId: String, runeId: String): RuneMutationResult {
        val slot = catalog.slots[slotId] ?: return RuneMutationResult.Failure(RuneMutationFailure.SLOT_NOT_FOUND)
        val rune = catalog.runes[runeId] ?: return RuneMutationResult.Failure(RuneMutationFailure.RUNE_NOT_FOUND)
        val before = repository.load(player.uniqueId)
        val rank = before.unlocked[runeId] ?: return RuneMutationResult.Failure(RuneMutationFailure.NOT_UNLOCKED)
        if (!slot.accepts(rune)) return RuneMutationResult.Failure(RuneMutationFailure.CATEGORY_MISMATCH)
        if (before.equipped.any { (otherSlot, equipped) -> otherSlot != slotId && equipped == runeId }) {
            return RuneMutationResult.Failure(RuneMutationFailure.ALREADY_EQUIPPED)
        }
        val required = rune.requiredLevel(rank)
        val level = api.levels.snapshot(player)?.level
        if (required > 0 && level == null) return RuneMutationResult.Failure(RuneMutationFailure.LEVEL_PROVIDER_MISSING, required)
        if (level != null && level < required) return RuneMutationResult.Failure(RuneMutationFailure.LEVEL_TOO_LOW, required)
        repository.update(player.uniqueId) { it.copy(equipped = it.equipped + (slotId to runeId)) }
        val rejected = syncPlayer(player, "equip")
        if (rejected != null) {
            repository.update(player.uniqueId) { before }
            syncPlayer(player, "equip-rollback")
            return RuneMutationResult.Failure(RuneMutationFailure.SOURCE_REJECTED, detail = rejected)
        }
        return RuneMutationResult.Success(status(slot, repository.load(player.uniqueId), level))
    }

    fun unequip(player: Player, slotId: String): RuneMutationResult {
        if (slotId !in catalog.slots) return RuneMutationResult.Failure(RuneMutationFailure.SLOT_NOT_FOUND)
        val before = repository.load(player.uniqueId)
        if (slotId !in before.equipped) return RuneMutationResult.Success(status(requireNotNull(catalog.slots[slotId]), before, api.levels.snapshot(player)?.level))
        repository.update(player.uniqueId) { it.copy(equipped = it.equipped - slotId) }
        val rejected = syncPlayer(player, "unequip")
        if (rejected != null) {
            repository.update(player.uniqueId) { before }
            syncPlayer(player, "unequip-rollback")
            return RuneMutationResult.Failure(RuneMutationFailure.SOURCE_REJECTED, detail = rejected)
        }
        return RuneMutationResult.Success(status(requireNotNull(catalog.slots[slotId]), repository.load(player.uniqueId), api.levels.snapshot(player)?.level))
    }

    fun message(key: String, vararg variables: Pair<String, Any?>): String = messages.text(key, *variables)

    private fun syncPlayer(player: Player, reason: String): String? {
        val state = repository.load(player.uniqueId)
        val level = api.levels.snapshot(player)?.level
        val slotsToVisit = trackedSlots[player.uniqueId].orEmpty() + catalog.slots.keys
        var rejected: String? = null
        slotsToVisit.sorted().forEach { slotId ->
            val slot = catalog.slots[slotId]
            val source = source(slotId)
            val result = if (slot == null) {
                api.sources.removeSource(player, source)
            } else {
                val status = status(slot, state, level)
                if (status.state == RuneActivationState.ACTIVE) {
                    api.sources.replaceSource(player, source, requireNotNull(status.rune).createModifiers(requireNotNull(status.rank)))
                } else api.sources.removeSource(player, source)
            }
            if (result is SourceUpdateResult.Rejected) rejected = "$source: ${result.reason}"
        }
        trackedSlots[player.uniqueId] = catalog.slots.keys
        if (rejected != null) BlinkLog.warn(message("console.source-rejected", "player" to player.name, "reason" to rejected, "context" to reason))
        return rejected
    }

    private fun clearPlayerSources(player: Player) {
        (trackedSlots.remove(player.uniqueId).orEmpty() + catalog.slots.keys).forEach { slotId ->
            api.sources.removeSource(player, source(slotId))
        }
    }

    private fun status(slot: RuneSlotDefinition, state: PlayerRuneState, level: Int?): RuneSlotStatus {
        val runeId = state.equipped[slot.id] ?: return RuneSlotStatus(slot, null, null, null, RuneActivationState.EMPTY)
        val rune = catalog.runes[runeId] ?: return RuneSlotStatus(slot, null, state.unlocked[runeId], null, RuneActivationState.RUNE_MISSING)
        val ownedRank = state.unlocked[runeId] ?: return RuneSlotStatus(slot, rune, null, null, RuneActivationState.NOT_UNLOCKED)
        val rank = rune.normalizedRank(ownedRank)
        val required = rune.requiredLevel(rank)
        val activation = when {
            !slot.accepts(rune) -> RuneActivationState.CATEGORY_MISMATCH
            required > 0 && level == null -> RuneActivationState.LEVEL_PROVIDER_MISSING
            level != null && level < required -> RuneActivationState.LEVEL_TOO_LOW
            else -> RuneActivationState.ACTIVE
        }
        return RuneSlotStatus(slot, rune, rank, required, activation)
    }

    private fun registerDefinitions(candidate: RuneCatalog): List<RegistrationHandle> = candidate.customAttributes.values
        .sortedBy { it.key }
        .map { api.definitions.registerAttribute(plugin, it, candidate.definitionPriority) }

    private fun validateAttributes(candidate: RuneCatalog) {
        val unknown = candidate.runes.values.flatMap { rune -> rune.modifiers.map { it.attribute } }
            .distinct().filter { api.definitions.attribute(it) == null }
        require(unknown.isEmpty()) { "符文修改器引用了未知属性：${unknown.joinToString()}" }
    }

    private fun source(slotId: String): AttributeSourceKey = AttributeSourceKey(ownerNamespace, "slot/$slotId")

    private fun saveDefault(path: String) {
        if (!plugin.dataFolder.resolve(path).isFile) plugin.saveResource(path, false)
    }
}
