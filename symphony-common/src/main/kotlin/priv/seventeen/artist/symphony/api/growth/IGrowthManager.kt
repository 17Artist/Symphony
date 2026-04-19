package priv.seventeen.artist.symphony.api.growth

import org.bukkit.entity.Player
import org.bukkit.inventory.ItemStack

interface IGrowthManager {
    // Level
    fun getLevel(player: Player): Int
    fun setLevel(player: Player, level: Int)
    fun addExp(player: Player, amount: Long, source: String)
    fun getExp(player: Player): Long
    fun getRequiredExp(level: Int): Long

    // Gem
    fun getGemSlots(item: ItemStack): List<GemSlot>
    fun insertGem(item: ItemStack, slotIndex: Int, gemId: String, gemLevel: Int): Boolean
    fun removeGem(item: ItemStack, slotIndex: Int): Boolean
    fun unlockSlot(item: ItemStack, slotIndex: Int): Boolean

    // Rune
    fun activateRune(player: Player, runeId: String, level: Int): Boolean
    fun deactivateRune(player: Player, runeId: String)
    fun addFragments(player: Player, runeId: String, amount: Int)
    fun getFragments(player: Player, runeId: String): Int

    // Enhance
    fun getEnhanceLevel(item: ItemStack): Int
    fun enhance(player: Player, item: ItemStack, protections: List<ItemStack>): EnhanceResult
    fun setEnhanceLevel(item: ItemStack, level: Int)

    // Set
    fun getSetId(item: ItemStack): String?
    fun getActiveSets(player: Player): Map<String, Int>
}
