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

package priv.seventeen.artist.symphony.bukkit.listener

import org.bukkit.entity.Player
import org.bukkit.event.EventPriority
import org.bukkit.event.entity.EntityPickupItemEvent
import org.bukkit.event.entity.PlayerDeathEvent
import org.bukkit.event.inventory.InventoryClickEvent
import org.bukkit.event.inventory.InventoryCloseEvent
import org.bukkit.event.inventory.InventoryDragEvent
import org.bukkit.event.player.PlayerDropItemEvent
import org.bukkit.event.player.PlayerItemBreakEvent
import org.bukkit.event.player.PlayerItemHeldEvent
import org.bukkit.event.player.PlayerRespawnEvent
import org.bukkit.event.player.PlayerSwapHandItemsEvent
import priv.seventeen.artist.blink.event.AutoListener
import priv.seventeen.artist.symphony.bukkit.runtime.SymphonyRuntime

object EquipmentChangeListener {
    @JvmStatic
    @AutoListener(priority = EventPriority.MONITOR, ignoreCancelled = true)
    fun onInventoryClick(event: InventoryClickEvent) = mark(event.whoClicked as? Player)

    @JvmStatic
    @AutoListener(priority = EventPriority.MONITOR, ignoreCancelled = true)
    fun onInventoryDrag(event: InventoryDragEvent) = mark(event.whoClicked as? Player)

    @JvmStatic
    @AutoListener(priority = EventPriority.MONITOR)
    fun onInventoryClose(event: InventoryCloseEvent) = mark(event.player as? Player)

    @JvmStatic
    @AutoListener(priority = EventPriority.MONITOR, ignoreCancelled = true)
    fun onHeld(event: PlayerItemHeldEvent) = mark(event.player)

    @JvmStatic
    @AutoListener(priority = EventPriority.MONITOR, ignoreCancelled = true)
    fun onSwap(event: PlayerSwapHandItemsEvent) = mark(event.player)

    @JvmStatic
    @AutoListener(priority = EventPriority.MONITOR, ignoreCancelled = true)
    fun onDrop(event: PlayerDropItemEvent) = mark(event.player)

    @JvmStatic
    @AutoListener(priority = EventPriority.MONITOR)
    fun onBreak(event: PlayerItemBreakEvent) = mark(event.player)

    @JvmStatic
    @AutoListener(priority = EventPriority.MONITOR, ignoreCancelled = true)
    fun onPickup(event: EntityPickupItemEvent) = mark(event.entity as? Player, refreshLore = true)

    @JvmStatic
    @AutoListener(priority = EventPriority.MONITOR)
    fun onRespawn(event: PlayerRespawnEvent) = mark(event.player)

    @JvmStatic
    @AutoListener(priority = EventPriority.MONITOR)
    fun onDeath(event: PlayerDeathEvent) = mark(event.entity)

    private fun mark(player: Player?, refreshLore: Boolean = false) {
        if (player != null) SymphonyRuntime.equipmentOrNull()?.mark(player, refreshLore)
    }
}
