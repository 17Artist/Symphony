package priv.seventeen.artist.symphony.plugin.listener

import org.bukkit.Bukkit
import org.bukkit.entity.Player
import org.bukkit.event.inventory.InventoryClickEvent
import org.bukkit.event.player.PlayerItemHeldEvent
import priv.seventeen.artist.blink.bukkitPlugin
import priv.seventeen.artist.blink.event.AutoListener
import priv.seventeen.artist.symphony.core.attribute.AttributeCalculator

object EquipmentListener {

    @AutoListener
    fun onItemHeld(event: PlayerItemHeldEvent) {
        AttributeCalculator.markDirty(event.player)
    }

    @AutoListener
    fun onInventoryClick(event: InventoryClickEvent) {
        val player = event.whoClicked as? Player ?: return
        val armorSlots = setOf(36, 37, 38, 39, 40)
        if (event.rawSlot in armorSlots || event.isShiftClick) {
            Bukkit.getScheduler().runTaskLater(bukkitPlugin, Runnable {
                AttributeCalculator.markDirty(player)
            }, 1L)
        }
    }
}

