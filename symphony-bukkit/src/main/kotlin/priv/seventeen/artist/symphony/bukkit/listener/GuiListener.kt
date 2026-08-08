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
import org.bukkit.event.entity.PlayerDeathEvent
import org.bukkit.event.inventory.InventoryClickEvent
import org.bukkit.event.inventory.InventoryCloseEvent
import org.bukkit.event.inventory.InventoryDragEvent
import org.bukkit.event.player.PlayerDropItemEvent
import org.bukkit.event.player.PlayerKickEvent
import org.bukkit.event.player.PlayerQuitEvent
import org.bukkit.event.player.PlayerSwapHandItemsEvent
import priv.seventeen.artist.blink.event.AutoListener
import priv.seventeen.artist.symphony.bukkit.runtime.SymphonyRuntime

object GuiListener {
    @JvmStatic
    @AutoListener(priority = EventPriority.HIGHEST, ignoreCancelled = false)
    fun onClick(event: InventoryClickEvent) {
        SymphonyRuntime.guiOrNull()?.handleClick(event)
    }

    @JvmStatic
    @AutoListener(priority = EventPriority.HIGHEST, ignoreCancelled = false)
    fun onDrag(event: InventoryDragEvent) {
        SymphonyRuntime.guiOrNull()?.handleDrag(event)
    }

    @JvmStatic
    @AutoListener(priority = EventPriority.MONITOR)
    fun onClose(event: InventoryCloseEvent) {
        SymphonyRuntime.guiOrNull()?.handleClose(event)
    }

    @JvmStatic
    @AutoListener(priority = EventPriority.HIGHEST, ignoreCancelled = false)
    fun onDrop(event: PlayerDropItemEvent) {
        if (SymphonyRuntime.guiOrNull()?.hasSession(event.player.uniqueId) == true) event.isCancelled = true
    }

    @JvmStatic
    @AutoListener(priority = EventPriority.HIGHEST, ignoreCancelled = false)
    fun onSwap(event: PlayerSwapHandItemsEvent) {
        if (SymphonyRuntime.guiOrNull()?.hasSession(event.player.uniqueId) == true) event.isCancelled = true
    }

    @JvmStatic
    @AutoListener(priority = EventPriority.MONITOR)
    fun onQuit(event: PlayerQuitEvent) = cleanup(event.player)

    @JvmStatic
    @AutoListener(priority = EventPriority.MONITOR, ignoreCancelled = true)
    fun onKick(event: PlayerKickEvent) = cleanup(event.player)

    @JvmStatic
    @AutoListener(priority = EventPriority.MONITOR)
    fun onDeath(event: PlayerDeathEvent) {
        SymphonyRuntime.guiOrNull()?.handleDeath(event)
    }

    private fun cleanup(player: Player) {
        SymphonyRuntime.guiOrNull()?.closeSession(player.uniqueId, false)
    }
}
