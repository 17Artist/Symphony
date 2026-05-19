package priv.seventeen.artist.symphony.core.growth.gem

import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import org.bukkit.Bukkit
import org.bukkit.entity.Player
import org.bukkit.inventory.ItemStack
import priv.seventeen.artist.symphony.api.event.GemInsertEvent
import priv.seventeen.artist.symphony.api.growth.GemSlot
import priv.seventeen.artist.symphony.nms.SymphonyItemData

class GemManager {
    private val gson = Gson()

    fun getGemSlots(item: ItemStack): List<GemSlot> {
        val json = SymphonyItemData.getString(item, "gem_slots") ?: return emptyList()
        return try {
            val type = object : TypeToken<List<GemSlotData>>() {}.type
            val data: List<GemSlotData> = gson.fromJson(json, type)
            data.map { GemSlot(it.index, it.gem_id, it.gem_level, it.locked) }
        } catch (e: Exception) { emptyList() }
    }

    fun insertGem(item: ItemStack, slotIndex: Int, gemId: String, gemLevel: Int): Boolean {
        val slots = getGemSlots(item).toMutableList()
        val slot = slots.find { it.index == slotIndex } ?: return false
        if (slot.locked || slot.gemId != null) return false
        slots[slots.indexOf(slot)] = slot.copy(gemId = gemId, gemLevel = gemLevel)
        saveGemSlots(item, slots)
        return true
    }

    /** 带玩家上下文的镶嵌入口，会广播 GemInsertEvent；事件被取消时不写入。 */
    fun insertGem(player: Player, item: ItemStack, slotIndex: Int, gemId: String, gemLevel: Int): Boolean {
        val event = GemInsertEvent(player, item, slotIndex, gemId, gemLevel)
        Bukkit.getPluginManager().callEvent(event)
        if (event.isCancelled) return false
        return insertGem(item, slotIndex, gemId, gemLevel)
    }

    fun removeGem(item: ItemStack, slotIndex: Int): Boolean {
        val slots = getGemSlots(item).toMutableList()
        val slot = slots.find { it.index == slotIndex } ?: return false
        if (slot.gemId == null) return false
        slots[slots.indexOf(slot)] = slot.copy(gemId = null, gemLevel = 0)
        saveGemSlots(item, slots)
        return true
    }

    fun unlockSlot(item: ItemStack, slotIndex: Int): Boolean {
        var slots = getGemSlots(item).toMutableList()
        // 如果物品没有宝石槽数据，自动初始化默认槽位（3 个 locked 槽）
        if (slots.isEmpty()) {
            slots = (0..2).map { GemSlot(it, null, 0, true) }.toMutableList()
        }
        val slot = slots.find { it.index == slotIndex } ?: run {
            // 索引超出现有范围，扩展到该索引
            if (slotIndex < 0 || slotIndex > 5) return false
            for (i in slots.size..slotIndex) {
                slots.add(GemSlot(i, null, 0, true))
            }
            slots.find { it.index == slotIndex } ?: return false
        }
        if (!slot.locked) return false
        slots[slots.indexOf(slot)] = slot.copy(locked = false)
        saveGemSlots(item, slots)
        return true
    }

    /**
     * 为物品初始化指定数量的已解锁宝石槽。
     * 会覆盖现有的 gem_slots 数据。
     */
    fun initSlots(item: ItemStack, count: Int) {
        val slots = (0 until count.coerceIn(1, 6)).map { GemSlot(it, null, 0, false) }
        saveGemSlots(item, slots)
    }

    private fun saveGemSlots(item: ItemStack, slots: List<GemSlot>) {
        val data = slots.map { GemSlotData(it.index, it.gemId, it.gemLevel, it.locked) }
        SymphonyItemData.setString(item, "gem_slots", gson.toJson(data))
    }

    private data class GemSlotData(val index: Int, val gem_id: String?, val gem_level: Int, val locked: Boolean)
}
