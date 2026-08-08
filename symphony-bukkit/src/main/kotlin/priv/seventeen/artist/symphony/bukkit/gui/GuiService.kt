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
import org.bukkit.entity.Player
import org.bukkit.event.entity.PlayerDeathEvent
import org.bukkit.event.inventory.InventoryAction
import org.bukkit.event.inventory.InventoryClickEvent
import org.bukkit.event.inventory.InventoryCloseEvent
import org.bukkit.event.inventory.InventoryDragEvent
import org.bukkit.inventory.Inventory
import org.bukkit.inventory.ItemStack
import org.bukkit.plugin.Plugin
import org.bukkit.scheduler.BukkitTask
import priv.seventeen.artist.symphony.api.service.SymphonyApi
import priv.seventeen.artist.symphony.bukkit.equipment.EquipmentReconciler
import priv.seventeen.artist.symphony.bukkit.service.BukkitTriggerService
import priv.seventeen.artist.symphony.engine.attribute.AttributeStateStore
import priv.seventeen.artist.symphony.engine.config.LanguageBundle
import priv.seventeen.artist.symphony.engine.definition.DefinitionRepository
import java.util.UUID
import java.util.concurrent.ConcurrentHashMap

class GuiService(
    private val plugin: Plugin,
    private val api: SymphonyApi,
    private val store: AttributeStateStore,
    private val triggers: BukkitTriggerService,
    private val equipment: EquipmentReconciler,
    private val workshops: ItemWorkshopService,
    layouts: GuiLayoutRepository,
    private val language: () -> LanguageBundle,
    private val definitions: DefinitionRepository,
    screens: Collection<GuiScreen> = defaultScreens()
) : AutoCloseable {
    private val screens = screens.associateBy { it.id }
    private val sessions = ConcurrentHashMap<UUID, GuiSession>()
    private val actions = ConcurrentHashMap<UUID, Map<Int, GuiAction>>()
    private val inputRefreshTasks = ConcurrentHashMap<UUID, BukkitTask>()
    private val icons = GuiIconFactory()
    @Volatile private var layouts = layouts
    private var refreshTask: BukkitTask? = null

    fun start() {
        check(refreshTask == null) { "GUI 服务已经启动" }
        val interval = layouts.minimumRefreshTicks()
        refreshTask = Bukkit.getScheduler().runTaskTimer(plugin, Runnable(::refreshChanged), interval, interval)
    }

    fun open(viewer: Player, screenId: GuiScreenId = GuiScreenId.ATTRIBUTE_BROWSER, target: Player = viewer, filter: String = "") {
        check(Bukkit.isPrimaryThread()) { "GUI 必须在主线程打开" }
        if (!viewer.hasPermission("symphony.gui.${screenId.alias}")) {
            viewer.sendMessage(language().text("permission.denied"))
            return
        }
        closeSession(viewer.uniqueId, closeInventory = true)
        val session = GuiSession(UUID.randomUUID(), viewer.uniqueId, target.uniqueId, screenId, filter = filter)
        sessions[viewer.uniqueId] = session
        openInventory(viewer, session)
    }

    fun handleClick(event: InventoryClickEvent) {
        val viewer = event.whoClicked as? Player ?: return
        val holder = event.view.topInventory.holder as? GuiInventoryHolder ?: return
        event.isCancelled = true
        val session = sessions[viewer.uniqueId] ?: return
        if (session.token != holder.sessionToken || session.viewerId != viewer.uniqueId) return
        val top = event.view.topInventory
        val layout = layouts.layout(session.screen)
        val roleBySlot = session.screen.workshopRoles().associateBy { layout.workshopSlot(it) }
        if (event.rawSlot in 0 until top.size) {
            val role = roleBySlot[event.rawSlot]
            if (role != null) {
                if (allowsTopAction(role, event.action)) {
                    event.isCancelled = false
                    scheduleInputRefresh(viewer, session)
                }
                return
            }
            val action = actions[session.token]?.get(event.rawSlot) ?: return
            handleAction(viewer, session, action)
            return
        }
        if (event.rawSlot < top.size) return
        when (event.action) {
            InventoryAction.MOVE_TO_OTHER_INVENTORY -> shiftIntoWorkshop(viewer, session, event, top, layout)
            InventoryAction.COLLECT_TO_CURSOR,
            InventoryAction.DROP_ALL_CURSOR,
            InventoryAction.DROP_ONE_CURSOR,
            InventoryAction.DROP_ALL_SLOT,
            InventoryAction.DROP_ONE_SLOT -> Unit
            else -> event.isCancelled = false
        }
    }

    fun handleDrag(event: InventoryDragEvent) {
        val viewer = event.whoClicked as? Player ?: return
        val holder = event.view.topInventory.holder as? GuiInventoryHolder ?: return
        val session = sessions[viewer.uniqueId] ?: run { event.isCancelled = true; return }
        if (holder.sessionToken != session.token) { event.isCancelled = true; return }
        val topSize = event.view.topInventory.size
        val topSlots = event.rawSlots.filter { it in 0 until topSize }
        if (topSlots.isEmpty()) return
        val layout = layouts.layout(session.screen)
        val editable = session.screen.workshopRoles()
            .filterNot { it == WorkshopSlotRole.OUTPUT }
            .mapTo(linkedSetOf()) { layout.workshopSlot(it) }
        if (topSlots.size != 1 || topSlots.single() !in editable) {
            event.isCancelled = true
            return
        }
        scheduleInputRefresh(viewer, session)
    }

    fun handleClose(event: InventoryCloseEvent) {
        val holder = event.inventory.holder as? GuiInventoryHolder ?: return
        val viewerId = event.player.uniqueId
        val current = sessions[viewerId]
        if (current?.token != holder.sessionToken || current.transitioning) return
        captureInputs(event.inventory, holder.screen, current)
        if (!sessions.remove(viewerId, current)) return
        actions.remove(current.token)
        inputRefreshTasks.remove(viewerId)?.cancel()
        workshops.release(viewerId)
        returnItems(event.player as Player, takeStaged(current))
    }

    fun handleDeath(event: PlayerDeathEvent) {
        val player = event.entity
        val session = sessions[player.uniqueId] ?: return
        captureOpenInputs(player, session)
        sessions.remove(player.uniqueId, session)
        actions.remove(session.token)
        inputRefreshTasks.remove(player.uniqueId)?.cancel()
        workshops.release(player.uniqueId)
        val returned = takeStaged(session)
        if (event.keepInventory) returnItems(player, returned)
        else event.drops += returned.map(ItemStack::clone)
    }

    fun hasSession(playerId: UUID): Boolean = sessions.containsKey(playerId)

    fun closeSession(playerId: UUID, closeInventory: Boolean) {
        val removed = sessions[playerId] ?: return
        val player = Bukkit.getPlayer(playerId)
        if (player != null) captureOpenInputs(player, removed)
        if (!sessions.remove(playerId, removed)) return
        actions.remove(removed.token)
        inputRefreshTasks.remove(playerId)?.cancel()
        workshops.release(playerId)
        if (player != null) returnItems(player, takeStaged(removed))
        if (closeInventory) player?.takeIf { it.isOnline }?.closeInventory()
    }

    fun sessionCount(): Int = sessions.size

    private fun handleAction(viewer: Player, session: GuiSession, action: GuiAction) {
        when (action) {
            is GuiAction.Explain -> scheduleOpen(viewer, GuiScreenId.ATTRIBUTE_EXPLAIN, action.attribute.value)
            is GuiAction.SelectSection -> {
                session.section = action.section
                session.page = 0
                renderCurrent(viewer, session)
            }
            is GuiAction.SelectIndex -> {
                session.selectedIndex = action.index.coerceAtLeast(0)
                renderCurrent(viewer, session)
            }
            is GuiAction.Page -> {
                session.page = (session.page + action.delta).coerceAtLeast(0)
                renderCurrent(viewer, session)
            }
            GuiAction.Close -> viewer.closeInventory()
            is GuiAction.Transaction -> {
                val inventory = viewer.openInventory.topInventory
                val result = workshops.execute(
                    viewer,
                    session,
                    inventory,
                    layouts.layout(session.screen),
                    action.type,
                    action.operation
                )
                when (result) {
                    is WorkshopResult.Success -> {
                        viewer.sendMessage(language().text("messages.success", "message" to result.message))
                        equipment.mark(viewer)
                    }
                    is WorkshopResult.Rejected -> viewer.sendMessage(language().text("messages.error", "message" to result.reason))
                }
                renderCurrent(viewer, session)
            }
        }
    }

    private fun scheduleOpen(viewer: Player, screen: GuiScreenId, filter: String) {
        val expected = sessions[viewer.uniqueId] ?: return
        Bukkit.getScheduler().runTask(plugin, Runnable {
            val current = sessions[viewer.uniqueId]
            if (viewer.isOnline && current?.token == expected.token) switchScreen(viewer, current, screen, filter)
        })
    }

    private fun switchScreen(viewer: Player, session: GuiSession, screen: GuiScreenId, filter: String) {
        if (!viewer.hasPermission("symphony.gui.${screen.alias}")) {
            viewer.sendMessage(language().text("permission.denied"))
            return
        }
        captureOpenInputs(viewer, session)
        val carryTarget = session.stagedItems.remove(WorkshopSlotRole.TARGET)
        returnItems(viewer, takeStaged(session))
        if (screen in ITEM_INTERACTION_SCREENS && carryTarget != null && !carryTarget.type.isAir) {
            session.stagedItems[WorkshopSlotRole.TARGET] = carryTarget
        } else if (carryTarget != null && !carryTarget.type.isAir) {
            returnItems(viewer, listOf(carryTarget))
        }
        actions.remove(session.token)
        session.screen = screen
        session.filter = filter
        session.page = 0
        session.selectedIndex = 0
        session.transitioning = true
        try {
            openInventory(viewer, session)
        } finally {
            session.transitioning = false
        }
    }

    private fun openInventory(viewer: Player, session: GuiSession) {
        val screen = requireNotNull(screens[session.screen]) { "缺少 GUI 界面 ${session.screen}" }
        val layout = layouts.layout(session.screen)
        val holder = GuiInventoryHolder(session.token, session.screen)
        val inventory = holder.create(layout.rows * 9, language().text(layout.titleKey))
        restoreStaged(session, inventory, layout)
        render(viewer, session, inventory, screen)
        viewer.openInventory(inventory)
    }

    private fun renderCurrent(viewer: Player, session: GuiSession) {
        val holder = viewer.openInventory.topInventory.holder as? GuiInventoryHolder ?: return
        if (holder.sessionToken != session.token) return
        val screen = screens[session.screen] ?: return
        render(viewer, session, viewer.openInventory.topInventory, screen)
    }

    private fun render(viewer: Player, session: GuiSession, inventory: Inventory, screen: GuiScreen) {
        val target = Bukkit.getPlayer(session.targetId) ?: viewer
        val layout = layouts.layout(screen.id)
        val preserved = screen.id.workshopRoles().associateWith { role ->
            inventory.getItem(layout.workshopSlot(role)).copyOrAir()
        }
        inventory.clear()
        preserved.forEach { (role, item) ->
            if (!item.type.isAir) inventory.setItem(layout.workshopSlot(role), item)
        }
        val context = GuiContext(
            viewer,
            target,
            session,
            inventory,
            api,
            store,
            definitions,
            language(),
            icons,
            workshops::preview
        ) { triggers.failures() }
            .also { it.layout = layout }
        val render = screen.render(context)
        actions[session.token] = render.actions.toMap()
        session.snapshotRevision = store.state(target.uniqueId).revision
    }

    private fun refreshChanged() {
        sessions.values.toList().forEach { session ->
            val viewer = Bukkit.getPlayer(session.viewerId) ?: run {
                closeSession(session.viewerId, false)
                return@forEach
            }
            val target = Bukkit.getPlayer(session.targetId) ?: viewer
            if (store.state(target.uniqueId).revision != session.snapshotRevision) renderCurrent(viewer, session)
        }
    }

    override fun close() {
        refreshTask?.cancel()
        refreshTask = null
        sessions.keys.toList().forEach { closeSession(it, true) }
        actions.clear()
        inputRefreshTasks.values.forEach(BukkitTask::cancel)
        inputRefreshTasks.clear()
        workshops.releaseAll()
    }

    fun replaceLayouts(candidate: GuiLayoutRepository) {
        check(Bukkit.isPrimaryThread()) { "GUI 布局必须在 Bukkit 主线程替换" }
        layouts = candidate
        sessions.keys.toList().forEach { closeSession(it, true) }
        refreshTask?.cancel()
        refreshTask = null
        start()
    }

    companion object {
        fun defaultScreens(): List<GuiScreen> = listOf(
            AttributeBrowserScreen(), AttributeExplainScreen(), AffixWorkshopScreen(), SocketWorkshopScreen(),
            UnsocketWorkshopScreen(), EnhancementWorkshopScreen(), AdminDiagnosticsScreen()
        )
    }

    private fun allowsTopAction(role: WorkshopSlotRole, action: InventoryAction): Boolean = when (role) {
        WorkshopSlotRole.OUTPUT -> action in AllowedActions.OUTPUT_TAKE_ACTIONS
        else -> action in AllowedActions.INPUT_ACTIONS
    }

    private fun shiftIntoWorkshop(
        viewer: Player,
        session: GuiSession,
        event: InventoryClickEvent,
        top: Inventory,
        layout: GuiLayout
    ) {
        val source = event.currentItem?.takeUnless { it.type.isAir }?.clone() ?: return
        val target = top.getItem(layout.workshopSlot(WorkshopSlotRole.TARGET)).copyOrAir()
        val role = session.screen.workshopRoles().filterNot { it == WorkshopSlotRole.OUTPUT }
            .firstOrNull { candidate ->
                candidate in session.screen.workshopRoles() &&
                    workshops.acceptsInput(session.screen, candidate, target, source) &&
                    canMerge(top.getItem(layout.workshopSlot(candidate)), source)
            }
        if (role == null) {
            viewer.sendMessage(language().text("gui.workshop.no-compatible-slot"))
            return
        }
        val destinationSlot = layout.workshopSlot(role)
        val destination = top.getItem(destinationSlot).copyOrAir()
        val capacity = if (destination.type.isAir) source.maxStackSize else destination.maxStackSize - destination.amount
        val moved = minOf(capacity, source.amount)
        if (moved <= 0) return
        val nextDestination = if (destination.type.isAir) source.clone().also { it.amount = moved }
        else destination.also { it.amount += moved }
        top.setItem(destinationSlot, nextDestination)
        if (source.amount == moved) event.currentItem = null
        else event.currentItem = source.also { it.amount -= moved }
        scheduleInputRefresh(viewer, session)
    }

    private fun scheduleInputRefresh(viewer: Player, session: GuiSession) {
        if (inputRefreshTasks.containsKey(viewer.uniqueId)) return
        val task = Bukkit.getScheduler().runTask(plugin, Runnable {
            inputRefreshTasks.remove(viewer.uniqueId)
            val current = sessions[viewer.uniqueId]
            val holder = viewer.openInventory.topInventory.holder as? GuiInventoryHolder
            if (current?.token == session.token && holder?.sessionToken == session.token) {
                equipment.mark(viewer)
                renderCurrent(viewer, current)
            }
        })
        inputRefreshTasks[viewer.uniqueId] = task
    }

    private fun captureOpenInputs(player: Player, session: GuiSession) {
        val top = player.openInventory.topInventory
        val holder = top.holder as? GuiInventoryHolder ?: return
        if (holder.sessionToken != session.token) return
        captureInputs(top, holder.screen, session)
    }

    private fun captureInputs(inventory: Inventory, screen: GuiScreenId, session: GuiSession) {
        if (screen !in ITEM_INTERACTION_SCREENS) return
        val layout = layouts.layout(screen)
        screen.workshopRoles().forEach { role ->
            val slot = layout.workshopSlot(role)
            val item = inventory.getItem(slot).copyOrAir()
            if (!item.type.isAir) session.stagedItems[role] = item
            inventory.setItem(slot, null)
        }
    }

    private fun restoreStaged(session: GuiSession, inventory: Inventory, layout: GuiLayout) {
        session.screen.workshopRoles().forEach { role ->
            val item = session.stagedItems.remove(role) ?: return@forEach
            if (!item.type.isAir) inventory.setItem(layout.workshopSlot(role), item)
        }
    }

    private fun takeStaged(session: GuiSession): List<ItemStack> = session.stagedItems.values
        .map(ItemStack::clone)
        .also { session.stagedItems.clear() }

    private fun returnItems(player: Player, items: Collection<ItemStack>) {
        val valid = items.filterNot { it.type.isAir || it.amount <= 0 }
        if (valid.isEmpty()) return
        val leftovers = player.inventory.addItem(*valid.map(ItemStack::clone).toTypedArray())
        equipment.mark(player)
        if (leftovers.isEmpty()) {
            player.sendMessage(language().text("gui.workshop.returned"))
            return
        }
        leftovers.values.forEach { player.world.dropItemNaturally(player.location, it) }
        player.sendMessage(language().text("gui.workshop.returned-dropped", "count" to leftovers.values.sumOf { it.amount }))
    }

    private fun canMerge(current: ItemStack?, incoming: ItemStack): Boolean {
        val item = current.copyOrAir()
        return item.type.isAir || item.isSimilar(incoming) && item.amount < item.maxStackSize
    }

    private fun ItemStack?.copyOrAir(): ItemStack =
        this?.takeUnless { it.type.isAir }?.clone() ?: ItemStack(Material.AIR)

    private object AllowedActions {
        val INPUT_ACTIONS = setOf(
            InventoryAction.PICKUP_ALL,
            InventoryAction.PICKUP_HALF,
            InventoryAction.PICKUP_ONE,
            InventoryAction.PICKUP_SOME,
            InventoryAction.PLACE_ALL,
            InventoryAction.PLACE_ONE,
            InventoryAction.PLACE_SOME,
            InventoryAction.SWAP_WITH_CURSOR,
            InventoryAction.MOVE_TO_OTHER_INVENTORY,
            InventoryAction.HOTBAR_SWAP,
            InventoryAction.HOTBAR_MOVE_AND_READD,
            InventoryAction.NOTHING
        )
        val OUTPUT_TAKE_ACTIONS = setOf(
            InventoryAction.PICKUP_ALL,
            InventoryAction.PICKUP_HALF,
            InventoryAction.PICKUP_ONE,
            InventoryAction.PICKUP_SOME,
            InventoryAction.MOVE_TO_OTHER_INVENTORY,
            InventoryAction.NOTHING
        )
    }
}
