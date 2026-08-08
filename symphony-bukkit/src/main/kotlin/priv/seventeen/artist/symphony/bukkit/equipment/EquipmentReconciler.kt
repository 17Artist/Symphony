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

package priv.seventeen.artist.symphony.bukkit.equipment

import org.bukkit.Bukkit
import org.bukkit.entity.LivingEntity
import org.bukkit.entity.Player
import org.bukkit.inventory.ItemStack
import org.bukkit.plugin.Plugin
import org.bukkit.scheduler.BukkitTask
import priv.seventeen.artist.blink.BlinkLog
import priv.seventeen.artist.overture.api.OvertureAPI
import priv.seventeen.artist.overture.api.data.ItemMutationResult
import priv.seventeen.artist.symphony.api.attribute.AttributeSourceKey
import priv.seventeen.artist.symphony.api.source.AttributeSourceService
import priv.seventeen.artist.symphony.api.source.SourceUpdateResult
import priv.seventeen.artist.symphony.bukkit.service.BukkitAttributeSourceService
import priv.seventeen.artist.symphony.bukkit.service.BukkitTriggerService
import priv.seventeen.artist.symphony.bukkit.runtime.SymphonyRuntime
import priv.seventeen.artist.symphony.engine.trigger.EntityTriggerContext
import priv.seventeen.artist.symphony.engine.trigger.ItemEquipTrigger
import priv.seventeen.artist.symphony.engine.trigger.ItemUnequipTrigger
import priv.seventeen.artist.symphony.engine.equipment.OffhandMode
import priv.seventeen.artist.symphony.engine.equipment.OffhandSettings
import java.util.UUID
import java.util.concurrent.ConcurrentHashMap

class EquipmentReconciler(
    private val plugin: Plugin,
    private val sources: BukkitAttributeSourceService,
    private val triggers: BukkitTriggerService,
    private val offhand: OffhandSettings,
    private val coalesceTicks: Long
) : AutoCloseable {
    private val pending = ConcurrentHashMap.newKeySet<UUID>()
    private val pendingLoreRefresh = ConcurrentHashMap.newKeySet<UUID>()
    private var task: BukkitTask? = null
    var onSetCountsChanged: (LivingEntity) -> Unit = {}

    fun mark(entity: LivingEntity, refreshLore: Boolean = false) {
        pending += entity.uniqueId
        if (refreshLore && entity is Player) pendingLoreRefresh += entity.uniqueId
        if (task == null) {
            task = Bukkit.getScheduler().runTaskLater(plugin, Runnable(::flush), coalesceTicks)
        }
    }

    fun reconcile(entity: LivingEntity, refreshLore: Boolean = false) {
        check(Bukkit.isPrimaryThread()) { "装备状态同步必须在主线程执行" }
        val equipment = entity.equipment ?: return
        val setCountsBefore = sources.setCounts(entity)
        val before = sources.itemSources(entity).filterKeys { it.namespace == "equipment" }
        val items = linkedMapOf(
            AttributeSourceKey("equipment", "head") to equipment.helmet,
            AttributeSourceKey("equipment", "chest") to equipment.chestplate,
            AttributeSourceKey("equipment", "legs") to equipment.leggings,
            AttributeSourceKey("equipment", "feet") to equipment.boots,
            AttributeSourceKey("equipment", "main_hand") to equipment.itemInMainHand
        )
        if (offhand.mode != OffhandMode.DISABLED) {
            items[AttributeSourceKey("equipment", "off_hand")] = equipment.itemInOffHand
        }
        val result = sources.replaceEquipmentSources(entity, items, offhand)
        if (result is SourceUpdateResult.Rejected) {
            BlinkLog.error(SymphonyRuntime.language().text("console.equipment-source-failed", "reason" to result.reason), result.cause ?: IllegalArgumentException(result.reason))
            return
        }
        val after = sources.itemSources(entity).filterKeys { it.namespace == "equipment" }
        (before.keys + after.keys).toSortedSet().forEach { source ->
            val previous = before[source]
            val current = after[source]
            if (previous == current) return@forEach
            val values = mapOf("source" to source.toString(), "before" to previous, "after" to current)
            if (previous != null) triggers.dispatch(
                ItemUnequipTrigger,
                EntityTriggerContext(UUID.randomUUID(), entity, null, System.currentTimeMillis(), values)
            )
            if (current != null) triggers.dispatch(
                ItemEquipTrigger,
                EntityTriggerContext(UUID.randomUUID(), entity, null, System.currentTimeMillis(), values)
            )
        }
        val setCountsChanged = setCountsBefore != sources.setCounts(entity)
        if (entity is Player && (setCountsChanged || refreshLore)) refreshPlayerItems(entity)
        if (setCountsChanged) onSetCountsChanged(entity)
    }

    fun forget(entity: LivingEntity) {
        forget(entity.uniqueId)
        sources.replaceEquipmentSources(entity, emptyMap(), offhand)
    }

    fun forget(entityId: UUID) {
        pending.remove(entityId)
        pendingLoreRefresh.remove(entityId)
    }

    fun refreshLore(player: Player) {
        check(Bukkit.isPrimaryThread()) { "物品 Lore 刷新必须在主线程执行" }
        refreshPlayerItems(player)
    }

    private fun flush() {
        task = null
        val ids = pending.toList()
        pending.removeAll(ids.toSet())
        ids.forEach { id ->
            val refreshLore = pendingLoreRefresh.remove(id)
            val entity = Bukkit.getEntity(id) as? LivingEntity ?: return@forEach
            if (entity.isValid) reconcile(entity, refreshLore)
        }
        if (pending.isNotEmpty() && task == null) {
            task = Bukkit.getScheduler().runTaskLater(plugin, Runnable(::flush), coalesceTicks)
        }
    }

    private fun refreshPlayerItems(player: Player) {
        for (slot in 0 until player.inventory.size) {
            val item = player.inventory.getItem(slot) ?: continue
            rebuildItem(player, item, slot.toString())?.let { rebuilt -> player.inventory.setItem(slot, rebuilt) }
        }
        val cursor = player.itemOnCursor
        rebuildItem(player, cursor, "cursor")?.let(player::setItemOnCursor)
    }

    private fun rebuildItem(player: Player, item: ItemStack, slot: String): ItemStack? {
        if (item.type.isAir || !OvertureAPI.isOvertureItem(item)) return null
        return when (val rebuilt = OvertureAPI.rebuildItem(item, player)) {
            is ItemMutationResult.Success -> rebuilt.itemStack.takeIf {
                !item.isSimilar(it) || item.amount != it.amount
            }
            is ItemMutationResult.Failure -> {
                BlinkLog.warn(
                    SymphonyRuntime.language().text(
                        "console.item-refresh-failed",
                        "player" to player.uniqueId,
                        "slot" to slot,
                        "reason" to rebuilt.reason
                    )
                )
                null
            }
        }
    }

    override fun close() {
        task?.cancel()
        task = null
        pending.clear()
        pendingLoreRefresh.clear()
    }
}
