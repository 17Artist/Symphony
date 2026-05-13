package priv.seventeen.artist.symphony.plugin.listener

import org.bukkit.Bukkit
import org.bukkit.entity.Player
import org.bukkit.event.inventory.InventoryClickEvent
import org.bukkit.event.player.PlayerDropItemEvent
import org.bukkit.event.player.PlayerInteractEvent
import org.bukkit.event.player.PlayerItemBreakEvent
import org.bukkit.event.player.PlayerItemHeldEvent
import org.bukkit.event.player.PlayerRespawnEvent
import org.bukkit.event.player.PlayerSwapHandItemsEvent
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
        // 只要可能影响装备槽/主手/副手/Shift 点击，就 markDirty
        if (event.rawSlot in armorSlots || event.isShiftClick || event.slotType.name == "ARMOR") {
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

    /**
     * 右键穿戴装备（比如手持胸甲右键直接穿上）
     */
    @AutoListener
    fun onInteract(event: PlayerInteractEvent) {
        val action = event.action
        if (action.name.startsWith("RIGHT_CLICK")) {
            val player = event.player
            val item = event.item ?: return
            val type = item.type.name
            // 装备类物品可以右键直接穿戴
            if (type.endsWith("_HELMET") || type.endsWith("_CHESTPLATE") ||
                type.endsWith("_LEGGINGS") || type.endsWith("_BOOTS") ||
                type == "ELYTRA" || type == "SHIELD" || type == "TURTLE_HELMET" ||
                type == "CARVED_PUMPKIN" || type == "SKELETON_SKULL" ||
                type == "ZOMBIE_HEAD" || type == "PLAYER_HEAD" || type == "DRAGON_HEAD") {
                Bukkit.getScheduler().runTaskLater(bukkitPlugin, Runnable {
                    AttributeCalculator.markDirty(player)
                }, 1L)
            }
        }
    }

    /**
     * 副手物品切换（F 键）
     */
    @AutoListener
    fun onSwapHands(event: PlayerSwapHandItemsEvent) {
        AttributeCalculator.markDirty(event.player)
    }

    /**
     * 丢弃物品（Q 键）
     */
    @AutoListener
    fun onDropItem(event: PlayerDropItemEvent) {
        Bukkit.getScheduler().runTaskLater(bukkitPlugin, Runnable {
            AttributeCalculator.markDirty(event.player)
        }, 1L)
    }

    /**
     * 物品损坏
     */
    @AutoListener
    fun onItemBreak(event: PlayerItemBreakEvent) {
        Bukkit.getScheduler().runTaskLater(bukkitPlugin, Runnable {
            AttributeCalculator.markDirty(event.player)
        }, 1L)
    }

    /**
     * 重生后装备清空
     */
    @AutoListener
    fun onRespawn(event: PlayerRespawnEvent) {
        Bukkit.getScheduler().runTaskLater(bukkitPlugin, Runnable {
            AttributeCalculator.markDirty(event.player)
        }, 1L)
    }
}

