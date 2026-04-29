package priv.seventeen.artist.symphony.plugin.listener

import org.bukkit.Bukkit
import org.bukkit.entity.Player
import org.bukkit.event.inventory.InventoryClickEvent
import org.bukkit.event.player.PlayerItemHeldEvent
import priv.seventeen.artist.blink.bukkitPlugin
import priv.seventeen.artist.blink.event.AutoListener
import priv.seventeen.artist.symphony.api.SymphonyAPI
import priv.seventeen.artist.symphony.api.event.AffixEquipEvent
import priv.seventeen.artist.symphony.api.event.AffixUnequipEvent
import priv.seventeen.artist.symphony.api.trigger.TriggerType
import priv.seventeen.artist.symphony.core.attribute.AttributeCalculator
import priv.seventeen.artist.symphony.core.trigger.TriggerDispatcher

object EquipmentListener {

    @AutoListener
    fun onItemHeld(event: PlayerItemHeldEvent) {
        val player = event.player
        val oldItem = player.inventory.getItem(event.previousSlot)
        val newItem = player.inventory.getItem(event.newSlot)
        AttributeCalculator.markDirty(player)
        TriggerDispatcher.dispatch(TriggerType.ON_HOLD, player) {
            set("item", newItem?.type?.name ?: "AIR")
            set("previousItem", oldItem?.type?.name ?: "AIR")
        }
    }

    @AutoListener
    fun onInventoryClick(event: InventoryClickEvent) {
        val player = event.whoClicked as? Player ?: return
        val armorSlots = setOf(36, 37, 38, 39, 40)
        if (event.rawSlot in armorSlots || event.isShiftClick) {
            // 记录变更前的装备状态
            val oldItem = event.currentItem
            val oldItemName = oldItem?.type?.name ?: "AIR"
            // 变更前收集旧物品上的词条（用于卸下事件）
            val oldAffixes = if (oldItem != null) {
                SymphonyAPI.getInstance().getAffixManager()?.getAffixes(oldItem)
            } else emptyList()
            Bukkit.getScheduler().runTaskLater(bukkitPlugin, Runnable {
                AttributeCalculator.markDirty(player)
                // 装备变更触发器
                if (event.rawSlot in armorSlots) {
                    val newItemStack = player.inventory.getItem(event.rawSlot)
                    val newItemName = newItemStack?.type?.name ?: "AIR"
                    if (oldItemName != "AIR" && oldItem != null) {
                        // 发布词条卸下事件
                        for (affix in oldAffixes) {
                            Bukkit.getPluginManager().callEvent(AffixUnequipEvent(player, oldItem, affix))
                        }
                        TriggerDispatcher.dispatch(TriggerType.ON_UNEQUIP, player) {
                            set("item", oldItemName)
                            set("slot", event.rawSlot)
                        }
                    }
                    if (newItemName != "AIR" && newItemStack != null) {
                        // 发布词条装上事件
                        val newAffixes = SymphonyAPI.getInstance()?.getAffixManager()?.getAffixes(newItemStack) ?: emptyList()
                        for (affix in newAffixes) {
                            Bukkit.getPluginManager().callEvent(AffixEquipEvent(player, newItemStack, affix))
                        }
                        TriggerDispatcher.dispatch(TriggerType.ON_EQUIP, player) {
                            set("item", newItemName)
                            set("slot", event.rawSlot)
                        }
                    }
                }
            }, 1L)
        }
    }
}

