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

package priv.seventeen.artist.symphony.bukkit.gui

import org.bukkit.Bukkit
import org.bukkit.Material
import org.bukkit.NamespacedKey
import org.bukkit.entity.Player
import org.bukkit.inventory.Inventory
import org.bukkit.inventory.ItemStack
import priv.seventeen.artist.blink.BlinkLog
import priv.seventeen.artist.overture.api.OvertureAPI
import priv.seventeen.artist.overture.api.data.ItemDataNode
import priv.seventeen.artist.symphony.api.event.AffixApplyEvent
import priv.seventeen.artist.symphony.api.event.AffixRemoveEvent
import priv.seventeen.artist.symphony.api.event.EnhanceEvent
import priv.seventeen.artist.symphony.api.event.GemInsertEvent
import priv.seventeen.artist.symphony.api.event.GemRemoveEvent
import priv.seventeen.artist.symphony.api.event.SocketDrillEvent
import priv.seventeen.artist.symphony.engine.config.LanguageBundle
import priv.seventeen.artist.symphony.engine.definition.*
import priv.seventeen.artist.symphony.overture.item.ItemMutationPlanResult
import priv.seventeen.artist.symphony.overture.item.OvertureItemSourceCompiler
import priv.seventeen.artist.symphony.overture.item.OvertureMutationGateway
import java.util.UUID
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.ThreadLocalRandom

sealed interface WorkshopResult {
    data class Success(val message: String) : WorkshopResult
    data class Rejected(val reason: String) : WorkshopResult
}

/**
 * 在主线程运行、以服务器为权威的物品事务协调器。
 *
 * Overture 始终修改物品副本。只有重新校验目标身份、事件结果以及所有暂存工作槽物品后，
 * 本服务才会写回副本。提交失败时，会还原所有受影响的背包槽位。
 */
class ItemWorkshopService(
    private val definitions: DefinitionRepository,
    private val language: () -> LanguageBundle,
    private val mutationGateway: OvertureMutationGateway = OvertureMutationGateway(),
    private val random: () -> Double = { ThreadLocalRandom.current().nextDouble() },
    private val affixesEnabled: Boolean = true,
    private val gemsEnabled: Boolean = true,
    private val socketsEnabled: Boolean = true,
    private val enhancementEnabled: Boolean = true
) {
    private data class Reservation(
        val token: UUID,
        val playerId: UUID,
        val sessionToken: UUID,
        val inventory: Inventory,
        val targetSlot: Int,
        val materialSlot: Int,
        val downgradeProtectionSlot: Int,
        val destroyProtectionSlot: Int,
        val outputSlot: Int,
        val originalTarget: ItemStack,
        val originalMaterial: ItemStack,
        val originalDowngradeProtection: ItemStack,
        val originalDestroyProtection: ItemStack,
        val originalOutput: ItemStack,
        val type: String,
        val selectedIndex: Int
    )

    private data class CostReservation(val amount: Int = 0)
    private data class RuntimeSocket(
        val index: Int,
        val accepts: Set<String>,
        val unlockAt: Int,
        val tool: String? = null,
        val gem: ItemDataNode.Compound? = null
    ) {
        fun accepts(category: String): Boolean = "*" in accepts || category in accepts
    }

    private val reservations = ConcurrentHashMap<UUID, Reservation>()

    fun preview(player: Player, inventory: Inventory, layout: GuiLayout, type: String, selectedIndex: Int): List<String> = runCatching {
        val item = inventory.workshopItem(layout, WorkshopSlotRole.TARGET)
        val material = inventory.workshopItem(layout, WorkshopSlotRole.MATERIAL)
        val downgradeProtection = inventory.workshopItem(layout, WorkshopSlotRole.DOWNGRADE_PROTECTION)
        val destroyProtection = inventory.workshopItem(layout, WorkshopSlotRole.DESTROY_PROTECTION)
        if (item.type.isAir || !OvertureAPI.isOvertureItem(item)) return@runCatching listOf(t("item.invalid"))
        val view = OvertureAPI.readItemData(item)
        val namespace = view.namespace("symphony") ?: return@runCatching listOf(t("item.invalid"))
        val instance = namespace.compound("instance")
        when (type) {
            "affix" -> {
                if (!affixesEnabled) return@runCatching listOf(t("workshop.disabled"))
                val pool = selectAffixPool(item)
                    ?: return@runCatching listOf(t("workshop.affix.no-pool"))
                val current = instance?.list("affixes")?.values.orEmpty()
                buildList {
                    add(t("workshop.affix.capacity", "current" to current.size, "maximum" to pool.maxAffixes, "candidates" to pool.entries.size))
                    add(t("workshop.selected-position", "index" to selectedIndex + 1))
                    add(t("workshop.cost", "cost" to formatRequirement(pool.cost)))
                    current.forEachIndexed { index, raw ->
                        val entry = raw as? ItemDataNode.Compound ?: return@forEachIndexed
                        val level = entry.int("level") ?: 1
                        add(t(
                            "workshop.affix.entry",
                            "index" to index + 1,
                            "name" to (entry.text("name") ?: t("common.unknown-name")),
                            "level" to level,
                            "locked" to if (entry.bool("locked") == true) t("workshop.affix.locked") else ""
                        ))
                        entry.text("id")?.let { definitions.current().snapshot.affixes[it] }
                            ?.affixDescription(level, entry.numberMap("parameters"))
                            .orEmpty()
                            .forEach { description -> add(t("workshop.affix.description", "description" to description)) }
                    }
                }
            }
            "socket" -> {
                if (!gemsEnabled || !socketsEnabled) return@runCatching listOf(t("workshop.disabled"))
                val sockets = view.component(OvertureItemSourceCompiler.SOCKETS)
                    ?: return@runCatching listOf(t("workshop.socket.unsupported"))
                val enhancement = instance?.compound("enhancement")?.int("level") ?: 0
                val slots = readSockets(sockets, instance)
                val materialId = OvertureAPI.getOvertureId(material)
                val materialName = materialId?.let { id ->
                    definitions.current().snapshot.gems.values.firstOrNull { it.values["overture-item"] == id }?.values?.get("name")?.toString()
                        ?: definitions.current().snapshot.socketTools.values.firstOrNull { it.overtureItem == id }?.name
                }
                buildList {
                    add(t("workshop.socket.capacity", "used" to slots.count { it.gem != null }, "unlocked" to slots.count { enhancement >= it.unlockAt }, "capacity" to slots.size))
                    add(t("workshop.socket.enhancement", "level" to enhancement))
                    add(t("workshop.socket.material", "name" to (materialName ?: t("common.none"))))
                    add(t("workshop.selected-position", "index" to selectedIndex + 1))
                    slots.forEach { slot ->
                        add(t(
                            "workshop.socket.slot",
                            "slot" to slot.index + 1,
                            "categories" to formatCategories(slot.accepts),
                            "state" to when {
                                enhancement < slot.unlockAt -> t("workshop.socket.states.locked", "level" to slot.unlockAt)
                                slot.gem != null -> t("workshop.socket.states.occupied", "name" to (slot.gem.text("name") ?: t("common.unknown-name")))
                                else -> t("workshop.socket.states.empty")
                            }
                        ))
                    }
                }
            }
            "unsocket" -> {
                if (!gemsEnabled || !socketsEnabled) return@runCatching listOf(t("workshop.disabled"))
                val removal = definitions.current().snapshot.socketRemoval
                    ?: return@runCatching listOf(t("workshop.unsocket.unavailable"))
                val sockets = view.component(OvertureItemSourceCompiler.SOCKETS)
                    ?: return@runCatching listOf(t("workshop.socket.unsupported"))
                val slots = readSockets(sockets, instance)
                val selected = slots.getOrNull(selectedIndex)
                listOf(
                    t("workshop.selected-position", "index" to selectedIndex + 1),
                    t("workshop.cost", "cost" to formatRequirement(removal.tool)),
                    selected?.gem?.text("name")?.let { t("workshop.unsocket.selected", "name" to it) }
                        ?: t("workshop.unsocket.empty")
                )
            }
            "enhancement" -> {
                if (!enhancementEnabled) return@runCatching listOf(t("workshop.disabled"))
                val rules = definitions.current().snapshot.enhancement
                    ?: return@runCatching listOf(t("workshop.enhancement.unavailable"))
                val current = instance?.compound("enhancement")?.int("level") ?: 0
                val target = current + 1
                val rule = rules.levels.filterKeys { it <= target }.maxByOrNull { it.key }?.value
                    ?: return@runCatching listOf(t("workshop.enhancement.maximum"))
                val stay = (1.0 - rule.successChance - rule.destroyChance - rule.downgradeChance).coerceAtLeast(0.0)
                val downgradeState = if (matchesConfiguredItem(downgradeProtection, rules.preventDowngradeItem))
                    t("workshop.enhancement.prevent-downgrade") else t("common.none")
                val destroyState = if (matchesConfiguredItem(destroyProtection, rules.preventDestroyItem))
                    t("workshop.enhancement.prevent-destroy") else t("common.none")
                listOf(
                    t("workshop.enhancement.level", "current" to current, "target" to target, "multiplier" to rule.multiplier),
                    t("workshop.enhancement.chances", "success" to percent(rule.successChance), "destroy" to percent(rule.destroyChance)),
                    t("workshop.enhancement.fallbacks", "downgrade" to percent(rule.downgradeChance), "stay" to percent(stay)),
                    t("workshop.cost", "cost" to formatRequirement(rule.cost)),
                    t("workshop.enhancement.downgrade-protection", "protection" to downgradeState),
                    t("workshop.enhancement.destroy-protection", "protection" to destroyState)
                )
            }
            else -> listOf(t("workshop.unavailable"))
        }
    }.getOrElse { error ->
        BlinkLog.error(t("console.workshop-preview-failed", "player" to player.uniqueId, "type" to type), error)
        listOf(t("workshop.preview-failed"))
    }

    fun execute(
        player: Player,
        session: GuiSession,
        inventory: Inventory,
        layout: GuiLayout,
        type: String,
        operation: String = "default",
        selectedIndex: Int = session.selectedIndex
    ): WorkshopResult {
        check(Bukkit.isPrimaryThread()) { "工坊事务必须在主线程执行" }
        if (session.viewerId != session.targetId || session.viewerId != player.uniqueId) {
            return rejected("workshop.only-self")
        }
        if (!player.hasPermission("symphony.gui.transaction.$type")) {
            return rejected("permission.denied")
        }

        val holder = inventory.holder as? GuiInventoryHolder
            ?: return rejected("workshop.item-changed")
        if (holder.sessionToken != session.token || holder.screen != session.screen) return rejected("workshop.item-changed")
        val targetSlot = layout.workshopSlot(WorkshopSlotRole.TARGET)
        val materialSlot = layout.workshopSlot(WorkshopSlotRole.MATERIAL)
        val downgradeProtectionSlot = layout.workshopSlot(WorkshopSlotRole.DOWNGRADE_PROTECTION)
        val destroyProtectionSlot = layout.workshopSlot(WorkshopSlotRole.DESTROY_PROTECTION)
        val outputSlot = layout.workshopSlot(WorkshopSlotRole.OUTPUT)
        val target = inventory.getItem(targetSlot).cloneOrAir()
        if (target.type.isAir || !OvertureAPI.isOvertureItem(target)) return rejected("item.invalid")
        if (target.amount != 1) return rejected("workshop.single-item-only")
        val inspection = runCatching { OvertureAPI.readItemData(target) }.getOrElse {
            BlinkLog.error(t("console.workshop-read-failed", "player" to player.uniqueId), it)
            return rejected("item.invalid")
        }
        if (inspection.namespace("symphony") == null) return rejected("item.invalid")

        val reservation = Reservation(
            UUID.randomUUID(),
            player.uniqueId,
            session.token,
            inventory,
            targetSlot,
            materialSlot,
            downgradeProtectionSlot,
            destroyProtectionSlot,
            outputSlot,
            target,
            inventory.getItem(materialSlot).cloneOrAir(),
            inventory.getItem(downgradeProtectionSlot).cloneOrAir(),
            inventory.getItem(destroyProtectionSlot).cloneOrAir(),
            inventory.getItem(outputSlot).cloneOrAir(),
            type,
            selectedIndex.coerceAtLeast(0)
        )
        if (reservations.putIfAbsent(player.uniqueId, reservation) != null) return rejected("workshop.busy")
        session.transactionToken = reservation.token
        return try {
            when (type) {
                "affix" -> if (!affixesEnabled) rejected("workshop.disabled") else when (operation) {
                    "remove" -> removeAffix(player, reservation)
                    "replace" -> rerollAffix(player, reservation)
                    "lock" -> toggleAffixLock(player, reservation)
                    else -> applyAffix(player, reservation)
                }
                "socket" -> if (!gemsEnabled || !socketsEnabled) rejected("workshop.disabled") else when (operation) {
                    "replace" -> replaceGem(player, reservation)
                    "drill" -> drillSocket(player, reservation)
                    else -> insertGem(player, reservation)
                }
                "unsocket" -> if (!gemsEnabled || !socketsEnabled) rejected("workshop.disabled") else removeGem(player, reservation)
                "enhancement" -> if (!enhancementEnabled) rejected("workshop.disabled") else applyEnhancement(player, reservation)
                else -> rejected("workshop.unavailable")
            }
        } catch (error: Throwable) {
            rollback(reservation)
            BlinkLog.error(t("console.workshop-transaction-failed", "player" to player.uniqueId, "type" to type, "operation" to operation), error)
            rejected("workshop.transaction-failed")
        } finally {
            reservations.remove(player.uniqueId, reservation)
            if (session.transactionToken == reservation.token) session.transactionToken = null
        }
    }

    fun release(playerId: UUID) { reservations.remove(playerId) }
    fun releaseAll() = reservations.clear()
    fun activeReservations(): Int = reservations.size

    fun acceptsInput(
        screen: GuiScreenId,
        role: WorkshopSlotRole,
        target: ItemStack,
        candidate: ItemStack
    ): Boolean {
        if (candidate.type.isAir || role !in screen.workshopRoles() || role == WorkshopSlotRole.OUTPUT) return false
        if (role == WorkshopSlotRole.TARGET) return isWorkshopTarget(candidate)
        val snapshot = definitions.current().snapshot
        return when (screen) {
            GuiScreenId.AFFIX_WORKSHOP -> role == WorkshopSlotRole.MATERIAL && when {
                target.type.isAir -> true
                else -> selectAffixPool(target)?.cost?.let { matchesRequirement(candidate, it) } == true
            }
            GuiScreenId.SOCKET_WORKSHOP -> role == WorkshopSlotRole.MATERIAL && run {
                val id = OvertureAPI.getOvertureId(candidate) ?: return@run false
                snapshot.gems.values.any { it.values["overture-item"] == id } ||
                    snapshot.socketTools.values.any { it.overtureItem == id }
            }
            GuiScreenId.UNSOCKET_WORKSHOP -> role == WorkshopSlotRole.MATERIAL &&
                snapshot.socketRemoval?.tool?.let { matchesRequirement(candidate, it) } == true
            GuiScreenId.ENHANCEMENT_WORKSHOP -> when (role) {
                WorkshopSlotRole.MATERIAL -> when {
                    target.type.isAir -> true
                    else -> enhancementCost(target)?.let { matchesRequirement(candidate, it) } == true
                }
                WorkshopSlotRole.DOWNGRADE_PROTECTION -> snapshot.enhancement?.let { rules ->
                    matchesConfiguredItem(candidate, rules.preventDowngradeItem)
                } == true
                WorkshopSlotRole.DESTROY_PROTECTION -> snapshot.enhancement?.let { rules ->
                    matchesConfiguredItem(candidate, rules.preventDestroyItem)
                } == true
                else -> false
            }
            else -> false
        }
    }

    private fun applyAffix(player: Player, reservation: Reservation): WorkshopResult {
        val snapshot = definitions.current().snapshot
        val pool = selectAffixPool(reservation.originalTarget)
            ?: return rejected("workshop.affix.no-pool")
        val namespace = requireNotNull(OvertureAPI.readItemData(reservation.originalTarget).namespace("symphony"))
        val instance = namespace.compound("instance")
        val existing = instance?.list("affixes")?.values.orEmpty()
        if (existing.size >= pool.maxAffixes) return rejected("workshop.affix.maximum", "maximum" to pool.maxAffixes)
        if (reservation.selectedIndex != existing.size) {
            return rejected("workshop.affix.add-position", "index" to existing.size + 1)
        }
        val existingIds = existing.mapNotNull { (it as? ItemDataNode.Compound)?.text("id") }.toSet()
        val existingGroups = existingIds.mapNotNull { snapshot.affixes[it]?.values?.get("exclusive-group") as? String }.toSet()
        val candidates = pool.entries.filter { entry ->
            if (entry.affixId in existingIds) return@filter false
            val group = snapshot.affixes[entry.affixId]?.values?.get("exclusive-group") as? String
            group == null || group !in existingGroups
        }
        if (candidates.isEmpty()) return rejected("workshop.affix.no-candidate")
        val selected = weighted(candidates)
        val level = randomLevel(selected.minLevel, selected.maxLevel)
        val definition = snapshot.affixes.getValue(selected.affixId)
        val cost = reserveCost(reservation, pool.cost) ?: return missingCost(pool.cost)
        val key = namespaced(selected.affixId)
        if (!call(AffixApplyEvent(player, reservation.originalTarget.clone(), key))) {
            return rejected("workshop.cancelled")
        }
        val changes = commonInstanceChanges(instance).toMutableMap()
        changes["symphony.instance.affixes"] = ItemDataNode.ListNode(existing + buildParameterizedEntry(definition, level))
        return mutateAndCommit(
            player,
            reservation,
            changes,
            cost = cost,
            message = t("workshop.affix.applied", "name" to definitionName(definition), "level" to level)
        )
    }

    private fun removeAffix(player: Player, reservation: Reservation): WorkshopResult {
        val view = OvertureAPI.readItemData(reservation.originalTarget)
        val instance = requireNotNull(view.namespace("symphony")).compound("instance")
        val existing = instance?.list("affixes")?.values.orEmpty()
        val removedIndex = reservation.selectedIndex
        val candidate = existing.getOrNull(removedIndex) as? ItemDataNode.Compound
            ?: return rejected("workshop.affix.position-empty")
        if (candidate.bool("locked") == true) return rejected("workshop.affix.position-locked")
        val removed = existing[removedIndex] as ItemDataNode.Compound
        val id = removed.text("id") ?: return corruptedItem()
        if (!call(AffixRemoveEvent(player, reservation.originalTarget.clone(), namespaced(id)))) {
            return rejected("workshop.cancelled")
        }
        val changes = commonInstanceChanges(instance).toMutableMap()
        changes["symphony.instance.affixes"] = ItemDataNode.ListNode(existing.filterIndexed { index, _ -> index != removedIndex })
        return mutateAndCommit(player, reservation, changes, message = t(
            "workshop.affix.removed",
            "name" to (removed.text("name") ?: definitionName(definitions.current().snapshot.affixes[id]))
        ))
    }

    private fun toggleAffixLock(player: Player, reservation: Reservation): WorkshopResult {
        val view = OvertureAPI.readItemData(reservation.originalTarget)
        val instance = requireNotNull(view.namespace("symphony")).compound("instance")
        val existing = instance?.list("affixes")?.values.orEmpty()
        val index = reservation.selectedIndex
        val entry = existing.getOrNull(index) as? ItemDataNode.Compound ?: return rejected("workshop.affix.none-lockable")
        val locked = entry.bool("locked") == true
        val replacement = ItemDataNode.Compound(entry.values + ("locked" to ItemDataNode.Bool(!locked)))
        val next = existing.toMutableList().also { it[index] = replacement }
        val changes = commonInstanceChanges(instance).toMutableMap()
        changes["symphony.instance.affixes"] = ItemDataNode.ListNode(next)
        return mutateAndCommit(
            player,
            reservation,
            changes,
            message = t(if (locked) "workshop.affix.unlocked" else "workshop.affix.locked-result")
        )
    }

    private fun rerollAffix(player: Player, reservation: Reservation): WorkshopResult {
        val snapshot = definitions.current().snapshot
        val pool = selectAffixPool(reservation.originalTarget) ?: return rejected("workshop.affix.no-pool")
        val view = OvertureAPI.readItemData(reservation.originalTarget)
        val instance = requireNotNull(view.namespace("symphony")).compound("instance")
        val existing = instance?.list("affixes")?.values.orEmpty()
        val index = reservation.selectedIndex
        val selectedEntry = existing.getOrNull(index) as? ItemDataNode.Compound
            ?: return rejected("workshop.affix.position-empty")
        if (selectedEntry.bool("locked") == true) return rejected("workshop.affix.position-locked")
        val old = existing[index] as ItemDataNode.Compound
        val oldId = old.text("id") ?: return corruptedItem()
        val preservedIds = existing.filterIndexed { i, _ -> i != index }
            .mapNotNull { (it as? ItemDataNode.Compound)?.text("id") }.toSet()
        val preservedGroups = preservedIds.mapNotNull { snapshot.affixes[it]?.values?.get("exclusive-group") as? String }.toSet()
        val candidates = pool.entries.filter { entry ->
            if (entry.affixId in preservedIds) return@filter false
            val group = snapshot.affixes[entry.affixId]?.values?.get("exclusive-group") as? String
            group == null || group !in preservedGroups
        }
        if (candidates.isEmpty()) return rejected("workshop.affix.no-candidate")
        val selected = weighted(candidates)
        val level = randomLevel(selected.minLevel, selected.maxLevel)
        val cost = reserveCost(reservation, pool.cost) ?: return missingCost(pool.cost)
        if (!call(AffixRemoveEvent(player, reservation.originalTarget.clone(), namespaced(oldId)))) {
            return rejected("workshop.cancelled")
        }
        if (!call(AffixApplyEvent(player, reservation.originalTarget.clone(), namespaced(selected.affixId)))) {
            return rejected("workshop.cancelled")
        }
        val selectedDefinition = snapshot.affixes.getValue(selected.affixId)
        val replacement = buildParameterizedEntry(selectedDefinition, level)
        val next = existing.toMutableList().also { it[index] = replacement }
        val changes = commonInstanceChanges(instance).toMutableMap()
        changes["symphony.instance.affixes"] = ItemDataNode.ListNode(next)
        return mutateAndCommit(
            player,
            reservation,
            changes,
            cost = cost,
            message = t("workshop.affix.rerolled", "name" to definitionName(selectedDefinition), "level" to level)
        )
    }

    private fun insertGem(player: Player, reservation: Reservation): WorkshopResult =
        writeGem(player, reservation, replace = false)

    private fun replaceGem(player: Player, reservation: Reservation): WorkshopResult =
        writeGem(player, reservation, replace = true)

    private fun writeGem(player: Player, reservation: Reservation, replace: Boolean): WorkshopResult {
        val gemItem = reservation.originalMaterial
        if (gemItem.type.isAir || !OvertureAPI.isOvertureItem(gemItem)) return rejected("workshop.socket.invalid-gem")
        val gemOvertureId = OvertureAPI.getOvertureId(gemItem) ?: return rejected("workshop.socket.invalid-gem")
        val gem = definitions.current().snapshot.gems.values.firstOrNull { it.values["overture-item"] == gemOvertureId }
            ?: return rejected("workshop.socket.invalid-gem")
        val category = gem.values["category"] as? String ?: return corruptedItem()
        val view = OvertureAPI.readItemData(reservation.originalTarget)
        val sockets = view.component(OvertureItemSourceCompiler.SOCKETS) ?: return rejected("workshop.socket.unsupported")
        val instance = requireNotNull(view.namespace("symphony")).compound("instance")
        val enhancement = instance?.compound("enhancement")?.int("level") ?: 0
        val slots = readSockets(sockets, instance)
        val existing = instance?.list("gems")?.values.orEmpty()
        val targetSlot = slots.getOrNull(reservation.selectedIndex)
            ?: return rejected("workshop.socket.position-missing")
        if (enhancement < targetSlot.unlockAt) return rejected("workshop.socket.position-locked")
        if (!targetSlot.accepts(category)) return rejected("workshop.socket.incompatible")
        if (replace && targetSlot.gem == null) return rejected("workshop.socket.no-replace")
        if (!replace && targetSlot.gem != null) return rejected("workshop.socket.position-occupied")
        if (!call(GemInsertEvent(player, reservation.originalTarget.clone(), namespaced(gem.id), targetSlot.index, category))) {
            return rejected("workshop.cancelled")
        }
        val entry = ItemDataNode.Compound(buildParameterizedEntry(gem, 1).values +
            ("slot" to ItemDataNode.Integer(targetSlot.index.toLong())) +
            ("category" to ItemDataNode.Text(category)))
        val replacedIndex = existing.indexOfFirst { (it as? ItemDataNode.Compound)?.int("slot") == targetSlot.index }
            .let { explicit -> if (explicit >= 0) explicit else if (replace) existing.indexOfFirst { it === targetSlot.gem } else -1 }
        if (replace && replacedIndex !in existing.indices) return corruptedItem()
        val next = if (replace) existing.toMutableList().also { it[replacedIndex] = entry } else existing + entry
        val changes = commonInstanceChanges(instance).toMutableMap()
        changes["symphony.instance.gems"] = ItemDataNode.ListNode(next)
        val returned = if (replace) gemOutput(existing[replacedIndex] as? ItemDataNode.Compound, player) else null
        return mutateAndCommit(
            player, reservation, changes, consumeMaterial = 1, outputs = listOfNotNull(returned),
            message = t(if (replace) "workshop.socket.replaced" else "workshop.socket.inserted", "name" to definitionName(gem), "slot" to targetSlot.index + 1)
        )
    }

    private fun removeGem(player: Player, reservation: Reservation): WorkshopResult {
        val removal = definitions.current().snapshot.socketRemoval
            ?: return rejected("workshop.unsocket.unavailable")
        val cost = reserveCost(reservation, removal.tool) ?: return missingCost(removal.tool)
        val view = OvertureAPI.readItemData(reservation.originalTarget)
        val instance = requireNotNull(view.namespace("symphony")).compound("instance")
        val existing = instance?.list("gems")?.values.orEmpty()
        val removedIndex = existing.indices.firstOrNull { index ->
            val entry = existing[index] as? ItemDataNode.Compound ?: return@firstOrNull false
            (entry.int("slot") ?: index) == reservation.selectedIndex
        } ?: -1
        if (removedIndex < 0) return rejected("workshop.socket.none-removable")
        val removed = existing[removedIndex] as? ItemDataNode.Compound ?: return corruptedItem()
        val id = removed.text("id") ?: return corruptedItem()
        val slot = removed.int("slot") ?: removedIndex
        val output = gemOutput(removed, player) ?: return corruptedItem()
        if (!call(GemRemoveEvent(player, reservation.originalTarget.clone(), namespaced(id), slot))) {
            return rejected("workshop.cancelled")
        }
        val changes = commonInstanceChanges(instance).toMutableMap()
        changes["symphony.instance.gems"] = ItemDataNode.ListNode(existing.filterIndexed { index, _ -> index != removedIndex })
        return mutateAndCommit(
            player,
            reservation,
            changes,
            cost = cost,
            outputs = listOf(output),
            message = t("workshop.socket.removed", "name" to (removed.text("name") ?: definitionName(definitions.current().snapshot.gems[id])), "slot" to slot + 1)
        )
    }

    private fun drillSocket(player: Player, reservation: Reservation): WorkshopResult {
        val toolItem = reservation.originalMaterial
        if (toolItem.type.isAir || !OvertureAPI.isOvertureItem(toolItem)) return rejected("workshop.socket.invalid-tool")
        val overtureId = OvertureAPI.getOvertureId(toolItem) ?: return rejected("workshop.socket.invalid-tool")
        val tool = definitions.current().snapshot.socketTools.values.firstOrNull { it.overtureItem == overtureId }
            ?: return rejected("workshop.socket.invalid-tool")
        val view = OvertureAPI.readItemData(reservation.originalTarget)
        val sockets = view.component(OvertureItemSourceCompiler.SOCKETS) ?: return rejected("workshop.socket.unsupported")
        val instance = requireNotNull(view.namespace("symphony")).compound("instance")
        val existing = instance?.list("extra_sockets")?.values.orEmpty()
        val maximum = sockets.int("max_extra_slots") ?: 0
        if (existing.size >= maximum) return rejected("workshop.socket.maximum-extra", "maximum" to maximum)
        val index = sockets.list("slots")?.values.orEmpty().size + existing.size
        val key = namespaced(tool.id)
        if (!call(SocketDrillEvent(player, reservation.originalTarget.clone(), key, index, tool.accepts))) {
            return rejected("workshop.cancelled")
        }
        val added = compoundOf(
            "accepts" to ItemDataNode.ListNode(tool.accepts.sorted().map(ItemDataNode::Text)),
            "unlock_at_enhancement" to ItemDataNode.Integer(0),
            "tool" to ItemDataNode.Text(tool.id)
        )
        val changes = commonInstanceChanges(instance).toMutableMap()
        changes["symphony.instance.extra_sockets"] = ItemDataNode.ListNode(existing + added)
        return mutateAndCommit(
            player,
            reservation,
            changes,
            consumeMaterial = 1,
            message = t("workshop.socket.drilled", "name" to tool.name, "slot" to index + 1, "categories" to formatCategories(tool.accepts))
        )
    }

    private fun readSockets(
        sockets: ItemDataNode.Compound,
        instance: ItemDataNode.Compound?
    ): List<RuntimeSocket> {
        val gems = instance?.list("gems")?.values.orEmpty().mapIndexedNotNull { index, raw ->
            val gem = raw as? ItemDataNode.Compound ?: return@mapIndexedNotNull null
            (gem.int("slot") ?: index) to gem
        }.toMap()
        val result = mutableListOf<RuntimeSocket>()
        sockets.list("slots")?.values.orEmpty().forEachIndexed { index, raw ->
            val node = raw as? ItemDataNode.Compound ?: return@forEachIndexed
            result += RuntimeSocket(index, node.stringSet("accepts"), node.int("unlock_at_enhancement") ?: 0, gem = gems[index])
        }
        instance?.list("extra_sockets")?.values.orEmpty().forEachIndexed { offset, raw ->
            val node = raw as? ItemDataNode.Compound ?: return@forEachIndexed
            val index = result.size
            result += RuntimeSocket(index, node.stringSet("accepts"), node.int("unlock_at_enhancement") ?: 0, node.text("tool"), gems[index])
        }
        require(result.size <= 64) { "物品槽位数量超过 64" }
        require(result.map { it.index }.distinct().size == result.size) { "物品槽位索引重复" }
        require(gems.keys.all { it in result.indices }) { "宝石引用了不存在的槽位" }
        return result
    }

    private fun applyEnhancement(player: Player, reservation: Reservation): WorkshopResult {
        val rules = definitions.current().snapshot.enhancement ?: return rejected("workshop.enhancement.unavailable")
        val view = OvertureAPI.readItemData(reservation.originalTarget)
        val instance = requireNotNull(view.namespace("symphony")).compound("instance")
        val currentLevel = instance?.compound("enhancement")?.int("level") ?: 0
        val targetLevel = currentLevel + 1
        val rule = rules.levels.filterKeys { it <= targetLevel }.maxByOrNull { it.key }?.value
            ?: return rejected("workshop.enhancement.maximum")
        val cost = reserveCost(reservation, rule.cost) ?: return missingCost(rule.cost)
        val preventsDestroy = matchesConfiguredItem(reservation.originalDestroyProtection, rules.preventDestroyItem)
        val preventsDowngrade = matchesConfiguredItem(reservation.originalDowngradeProtection, rules.preventDowngradeItem)
        val consumeDestroyProtection = if (preventsDestroy) 1 else 0
        val consumeDowngradeProtection = if (preventsDowngrade) 1 else 0
        if (!call(EnhanceEvent(player, reservation.originalTarget.clone(), currentLevel, targetLevel))) {
            return rejected("workshop.cancelled")
        }
        val roll = sample()
        val changes = commonInstanceChanges(instance).toMutableMap()
        return when (rule.resolveOutcome(roll, preventsDestroy, preventsDowngrade)) {
            EnhancementOutcome.SUCCESS -> {
                changes["symphony.instance.enhancement"] = compoundOf("level" to ItemDataNode.Integer(targetLevel.toLong()))
                mutateAndCommit(
                    player,
                    reservation,
                    changes,
                    cost = cost,
                    consumeDowngradeProtection = consumeDowngradeProtection,
                    consumeDestroyProtection = consumeDestroyProtection,
                    message = t("workshop.enhancement.success", "level" to targetLevel)
                )
            }
            EnhancementOutcome.DESTROY ->
                commitInventory(
                    player,
                    reservation,
                    ItemStack(Material.AIR),
                    cost,
                    consumeDowngradeProtection = consumeDowngradeProtection,
                    consumeDestroyProtection = consumeDestroyProtection,
                    message = t("workshop.enhancement.destroyed")
                )
            EnhancementOutcome.DOWNGRADE -> {
                val next = (currentLevel - 1).coerceAtLeast(0)
                changes["symphony.instance.enhancement"] = compoundOf("level" to ItemDataNode.Integer(next.toLong()))
                mutateAndCommit(
                    player,
                    reservation,
                    changes,
                    cost = cost,
                    consumeDowngradeProtection = consumeDowngradeProtection,
                    consumeDestroyProtection = consumeDestroyProtection,
                    message = t("workshop.enhancement.downgraded", "level" to next)
                )
            }
            EnhancementOutcome.STAY -> {
                val protected = (preventsDestroy && roll < rule.successChance + rule.destroyChance) ||
                    (preventsDowngrade && roll < rule.successChance + rule.destroyChance + rule.downgradeChance)
                commitInventory(player, reservation, reservation.originalTarget.clone(), cost,
                    consumeDowngradeProtection = consumeDowngradeProtection,
                    consumeDestroyProtection = consumeDestroyProtection,
                    message = t(if (protected) "workshop.enhancement.protected" else "workshop.enhancement.stayed", "level" to currentLevel))
            }
        }
    }

    private fun mutateAndCommit(
        player: Player,
        reservation: Reservation,
        changes: Map<String, ItemDataNode>,
        cost: CostReservation = CostReservation(),
        consumeMaterial: Int = 0,
        consumeDowngradeProtection: Int = 0,
        consumeDestroyProtection: Int = 0,
        outputs: List<ItemStack> = emptyList(),
        message: String
    ): WorkshopResult {
        return when (val result = mutationGateway.mutate(reservation.originalTarget, player, changes)) {
            is ItemMutationPlanResult.Failure -> {
                BlinkLog.error(
                    t("console.workshop-write-rejected", "player" to player.uniqueId, "reason" to result.reason),
                    result.cause ?: IllegalArgumentException(result.reason)
                )
                rejected("workshop.transaction-failed")
            }
            is ItemMutationPlanResult.Success -> commitInventory(
                player,
                reservation,
                result.itemStack,
                cost,
                consumeMaterial,
                consumeDowngradeProtection,
                consumeDestroyProtection,
                outputs,
                message
            )
        }
    }

    private fun commitInventory(
        player: Player,
        reservation: Reservation,
        newTarget: ItemStack,
        cost: CostReservation,
        consumeMaterial: Int = 0,
        consumeDowngradeProtection: Int = 0,
        consumeDestroyProtection: Int = 0,
        outputs: List<ItemStack> = emptyList(),
        message: String
    ): WorkshopResult {
        if (!sameWorkshop(reservation)) return rejected("workshop.item-changed")
        val materialAmount = Math.addExact(cost.amount, consumeMaterial)
        if (materialAmount > 0 && !sameStack(
                reservation.inventory.getItem(reservation.materialSlot),
                reservation.originalMaterial
            )) return rejected("workshop.material-changed")
        if (cost.amount > 0 && !validateCost(reservation, cost)) return rejected("workshop.material-changed")
        if (consumeDowngradeProtection > 0 && !sameStack(
                reservation.inventory.getItem(reservation.downgradeProtectionSlot),
                reservation.originalDowngradeProtection
            )) return rejected("workshop.material-changed")
        if (consumeDestroyProtection > 0 && !sameStack(
                reservation.inventory.getItem(reservation.destroyProtectionSlot),
                reservation.originalDestroyProtection
            )) return rejected("workshop.material-changed")
        if (outputs.isNotEmpty() && !sameStack(
                reservation.inventory.getItem(reservation.outputSlot),
                reservation.originalOutput
            )) return rejected("workshop.output-occupied")
        if (!canAppendOutputs(reservation.originalOutput, outputs)) return rejected("workshop.output-occupied")

        return try {
            reservation.inventory.setItem(reservation.targetSlot, newTarget.cloneOrAir())
            if (materialAmount > 0) decrementSlot(reservation.inventory, reservation.materialSlot, materialAmount)
            if (consumeDowngradeProtection > 0) decrementSlot(reservation.inventory, reservation.downgradeProtectionSlot, consumeDowngradeProtection)
            if (consumeDestroyProtection > 0) decrementSlot(reservation.inventory, reservation.destroyProtectionSlot, consumeDestroyProtection)
            appendOutputs(reservation.inventory, reservation.outputSlot, outputs)
            WorkshopResult.Success(message)
        } catch (error: Throwable) {
            rollback(reservation)
            BlinkLog.error(t("console.workshop-commit-failed", "player" to player.uniqueId), error)
            rejected("workshop.transaction-failed")
        }
    }

    private fun reserveCost(reservation: Reservation, requirement: ItemRequirementDefinition?): CostReservation? {
        if (requirement == null) return CostReservation()
        val material = reservation.originalMaterial
        return if (matchesRequirement(material, requirement) && material.amount >= requirement.amount) {
            CostReservation(requirement.amount)
        } else null
    }

    private fun validateCost(reservation: Reservation, cost: CostReservation): Boolean =
        cost.amount == 0 || reservation.inventory.getItem(reservation.materialSlot).let { current ->
            sameStack(current, reservation.originalMaterial) && current != null && current.amount >= cost.amount
        }

    private fun canAppendOutputs(current: ItemStack, outputs: List<ItemStack>): Boolean {
        if (outputs.isEmpty()) return true
        if (outputs.size != 1) return false
        val output = outputs.single()
        return current.type.isAir || current.isSimilar(output) && current.amount + output.amount <= current.maxStackSize
    }

    private fun appendOutputs(inventory: Inventory, slot: Int, outputs: List<ItemStack>) {
        if (outputs.isEmpty()) return
        val output = outputs.single().clone()
        val current = inventory.getItem(slot).cloneOrAir()
        if (current.type.isAir) inventory.setItem(slot, output)
        else {
            check(current.isSimilar(output) && current.amount + output.amount <= current.maxStackSize)
            current.amount += output.amount
            inventory.setItem(slot, current)
        }
    }

    private fun matchesRequirement(item: ItemStack, requirement: ItemRequirementDefinition): Boolean = when {
        requirement.material != null -> item.type.name == requirement.material
        requirement.overtureItem != null -> OvertureAPI.getOvertureId(item) == requirement.overtureItem
        else -> false
    }

    private fun missingCost(requirement: ItemRequirementDefinition?): WorkshopResult.Rejected = rejected(
        "workshop.missing-cost",
        "cost" to formatRequirement(requirement)
    )

    private fun formatRequirement(requirement: ItemRequirementDefinition?): String = when {
        requirement == null -> t("common.none")
        requirement.material != null -> "${materialName(requireNotNull(requirement.material))} ×${requirement.amount}"
        requirement.overtureItem != null -> "${OvertureAPI.getItemName(requireNotNull(requirement.overtureItem)) ?: t("common.material")} ×${requirement.amount}"
        else -> t("common.material")
    }

    private fun percent(value: Double): String = "%.2f%%".format(java.util.Locale.ROOT, value * 100.0)

    private fun matchesConfiguredItem(item: ItemStack, configured: String?): Boolean {
        if (configured.isNullOrBlank() || item.type.isAir) return false
        return item.type.name.equals(configured, true) || OvertureAPI.getOvertureId(item) == configured
    }

    private fun selectAffixPool(item: ItemStack): AffixPoolDefinition? {
        val overtureId = OvertureAPI.getOvertureId(item)
        val material = item.type.name
        return definitions.current().snapshot.affixPools.values.asSequence()
            .filter { it.matches(overtureId, material) }
            .sortedWith(
                compareByDescending<AffixPoolDefinition> {
                    it.specificity(overtureId, material)
                }.thenByDescending { it.priority }.thenBy { it.id }
            )
            .firstOrNull()
    }

    private fun enhancementCost(item: ItemStack): ItemRequirementDefinition? {
        if (!isWorkshopTarget(item)) return null
        val rules = definitions.current().snapshot.enhancement ?: return null
        val instance = OvertureAPI.readItemData(item).namespace("symphony")?.compound("instance")
        val target = (instance?.compound("enhancement")?.int("level") ?: 0) + 1
        return rules.levels.filterKeys { it <= target }.maxByOrNull { it.key }?.value?.cost
    }

    private fun isWorkshopTarget(item: ItemStack): Boolean = runCatching {
        !item.type.isAir && item.amount == 1 && OvertureAPI.isOvertureItem(item) &&
            OvertureAPI.readItemData(item).namespace("symphony") != null
    }.getOrDefault(false)

    private fun gemOutput(entry: ItemDataNode.Compound?, player: Player): ItemStack? {
        val id = entry?.text("id") ?: return null
        val definition = definitions.current().snapshot.gems[id] ?: return null
        val overtureId = definition.values["overture-item"] as? String ?: return null
        return OvertureAPI.generateItem(overtureId, player)
    }

    private fun materialName(material: String): String = language().optional(
        "materials.${material.lowercase()}",
        t("common.material")
    )

    private fun definitionName(definition: GenericDefinition?): String =
        definition?.values?.get("name")?.toString()?.takeIf(String::isNotBlank) ?: t("common.unknown-name")

    private fun weighted(entries: List<AffixPoolEntryDefinition>): AffixPoolEntryDefinition {
        val total = entries.sumOf { it.weight }
        var cursor = sample() * total
        entries.forEach { entry ->
            cursor -= entry.weight
            if (cursor < 0.0) return entry
        }
        return entries.last()
    }

    private fun randomLevel(min: Int, max: Int): Int = if (min == max) min else min + (sample() * (max - min + 1)).toInt().coerceAtMost(max - min)
    private fun sample(): Double = random().coerceIn(0.0, Math.nextDown(1.0))
    private fun call(event: org.bukkit.event.Cancellable): Boolean {
        Bukkit.getPluginManager().callEvent(event as org.bukkit.event.Event)
        return !event.isCancelled
    }

    private fun buildParameterizedEntry(definition: GenericDefinition, level: Int): ItemDataNode.Compound {
        val values = definition.values
        val name = definitionName(definition)
        val levels = values["levels"] as? Map<*, *> ?: emptyMap<Any, Any>()
        val levelValues = levels[level.toString()] as? Map<*, *> ?: levels[level] as? Map<*, *>
            ?: throw IllegalArgumentException("${definition.id} 缺少 levels.$level")
        val parameters = levelValues.entries.filterNot { it.key.toString() == "modifiers" }.associate { it.key.toString() to it.value }
        val passive = values["passive"] as? Map<*, *> ?: levelValues["modifiers"] as? Map<*, *> ?: emptyMap<Any, Any>()
        val modifiers = linkedMapOf<String, ItemDataNode>()
        passive.entries.forEach { (rawAttribute, rawDefinition) ->
            val attribute = rawAttribute.toString().let { if (':' in it) it else "symphony:$it" }
            val modifier = rawDefinition as? Map<*, *> ?: return@forEach
            val rawValue = modifier["value"] ?: return@forEach
            modifiers[attribute] = compoundOf(
                "operation" to ItemDataNode.Text(modifier["operation"]?.toString() ?: "add"),
                "value" to ItemDataNode.Decimal(resolveNumber(rawValue, parameters))
            )
        }
        return compoundOf(
            "id" to ItemDataNode.Text(definition.id),
            "name" to ItemDataNode.Text(name),
            "level" to ItemDataNode.Integer(level.toLong()),
            "parameters" to ItemDataNode.Compound(parameters.mapValues { (_, value) ->
                ItemDataNode.Decimal(value.toString().toDoubleOrNull() ?: throw IllegalArgumentException("参数必须是数值快照：$value"))
            }),
            "tags" to ItemDataNode.ListNode((values["tags"] as? List<*>)?.map { ItemDataNode.Text(it.toString()) }.orEmpty()),
            "modifiers" to ItemDataNode.Compound(modifiers)
        )
    }

    private fun resolveNumber(raw: Any?, parameters: Map<String, Any?>): Double {
        val value = when (raw) {
            is Number -> raw.toDouble()
            is String -> if (raw.startsWith('{') && raw.endsWith('}')) {
                parameters[raw.removePrefix("{").removeSuffix("}")]?.toString()?.toDoubleOrNull()
            } else raw.removeSuffix("%").toDoubleOrNull()?.let { if (raw.endsWith('%')) it / 100.0 else it }
            else -> null
        }
        require(value != null && value.isFinite()) { "无法解析参数值 $raw" }
        return value
    }

    private fun commonInstanceChanges(instance: ItemDataNode.Compound?): Map<String, ItemDataNode> = mapOf(
        "symphony.instance.uuid" to ItemDataNode.Text(instance?.text("uuid") ?: UUID.randomUUID().toString()),
        "symphony.instance.revision" to ItemDataNode.Integer(((instance?.int("revision") ?: 0) + 1).toLong())
    )

    private fun sameWorkshop(reservation: Reservation): Boolean {
        val holder = reservation.inventory.holder as? GuiInventoryHolder ?: return false
        return holder.sessionToken == reservation.sessionToken &&
            sameStack(reservation.inventory.getItem(reservation.targetSlot), reservation.originalTarget)
    }

    private fun decrementSlot(inventory: Inventory, slot: Int, amount: Int) {
        val current = inventory.getItem(slot).cloneOrAir()
        require(!current.type.isAir && amount > 0 && current.amount >= amount) { "工坊材料数量不足" }
        if (current.amount == amount) inventory.setItem(slot, null)
        else {
            current.amount -= amount
            inventory.setItem(slot, current)
        }
    }

    private fun rollback(reservation: Reservation) {
        reservation.inventory.setItem(reservation.targetSlot, reservation.originalTarget.cloneOrAir())
        reservation.inventory.setItem(reservation.materialSlot, reservation.originalMaterial.cloneOrAir())
        reservation.inventory.setItem(reservation.downgradeProtectionSlot, reservation.originalDowngradeProtection.cloneOrAir())
        reservation.inventory.setItem(reservation.destroyProtectionSlot, reservation.originalDestroyProtection.cloneOrAir())
        reservation.inventory.setItem(reservation.outputSlot, reservation.originalOutput.cloneOrAir())
    }

    private fun t(key: String, vararg variables: Pair<String, Any?>): String = language().text(key, *variables)

    private fun formatCategories(categories: Set<String>): String = categories.joinToString {
        language().optional(
            "socket-categories.${if (it == "*") "universal" else it.lowercase()}",
            t("socket-categories.other")
        )
    }

    private fun rejected(key: String, vararg variables: Pair<String, Any?>): WorkshopResult.Rejected =
        WorkshopResult.Rejected(t(key, *variables))

    private fun corruptedItem(): WorkshopResult.Rejected = rejected("item.invalid")
}

private fun namespaced(raw: String): NamespacedKey = requireNotNull(NamespacedKey.fromString(if (':' in raw) raw else "symphony:$raw"))
private fun ItemStack?.cloneOrAir(): ItemStack = this?.takeUnless { it.type.isAir }?.clone() ?: ItemStack(Material.AIR)
private fun sameStack(current: ItemStack?, expected: ItemStack): Boolean {
    val actual = current.cloneOrAir()
    if (actual.type.isAir || expected.type.isAir) return actual.type.isAir && expected.type.isAir
    return actual.amount == expected.amount && actual.isSimilar(expected)
}
private fun Inventory.workshopItem(layout: GuiLayout, role: WorkshopSlotRole): ItemStack =
    getItem(layout.workshopSlot(role)).cloneOrAir()
private fun ItemDataNode.Compound.compound(name: String): ItemDataNode.Compound? = values[name] as? ItemDataNode.Compound
private fun ItemDataNode.Compound.list(name: String): ItemDataNode.ListNode? = values[name] as? ItemDataNode.ListNode
private fun ItemDataNode.Compound.text(name: String): String? = (values[name] as? ItemDataNode.Text)?.value
private fun ItemDataNode.Compound.bool(name: String): Boolean? = (values[name] as? ItemDataNode.Bool)?.value
private fun ItemDataNode.Compound.stringSet(name: String): Set<String> =
    list(name)?.values.orEmpty().mapNotNullTo(linkedSetOf()) { (it as? ItemDataNode.Text)?.value }
private fun ItemDataNode.Compound.int(name: String): Int? = when (val node = values[name]) {
    is ItemDataNode.Integer -> Math.toIntExact(node.value)
    is ItemDataNode.Decimal -> node.toLongExact().let(Math::toIntExact)
    else -> null
}
private fun ItemDataNode.Compound.numberMap(name: String): Map<String, Double> =
    compound(name)?.values.orEmpty().mapNotNull { (key, value) ->
        val number = when (value) {
            is ItemDataNode.Integer -> value.value.toDouble()
            is ItemDataNode.Decimal -> value.value
            is ItemDataNode.Text -> value.value.removeSuffix("%").toDoubleOrNull()?.let {
                if (value.value.endsWith('%')) it / 100.0 else it
            }
            else -> null
        }
        number?.let { key to it }
    }.toMap()
private fun compoundOf(vararg values: Pair<String, ItemDataNode>) = ItemDataNode.Compound(linkedMapOf(*values))
