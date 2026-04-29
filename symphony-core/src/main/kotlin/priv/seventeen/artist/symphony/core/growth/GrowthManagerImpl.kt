package priv.seventeen.artist.symphony.core.growth

import org.bukkit.entity.Player
import org.bukkit.inventory.ItemStack
import priv.seventeen.artist.symphony.api.growth.*
import priv.seventeen.artist.symphony.core.growth.level.LevelManager
import priv.seventeen.artist.symphony.core.growth.gem.GemManager
import priv.seventeen.artist.symphony.core.growth.rune.RuneManager
import priv.seventeen.artist.symphony.core.growth.enhance.EnhanceManager
import priv.seventeen.artist.symphony.core.growth.set.SetManager

class GrowthManagerImpl : IGrowthManager {
    val levelManager = LevelManager()
    val gemManager = GemManager()
    val runeManager = RuneManager()
    val enhanceManager = EnhanceManager()
    val setManager = SetManager()

    override fun getLevel(player: Player) = levelManager.getLevel(player)
    override fun setLevel(player: Player, level: Int) = levelManager.setLevel(player, level)
    override fun addExp(player: Player, amount: Long, source: String) = levelManager.addExp(player, amount, source)
    override fun getExp(player: Player) = levelManager.getExp(player)
    override fun getRequiredExp(level: Int) = levelManager.getRequiredExp(level)

    override fun getGemSlots(item: ItemStack) = gemManager.getGemSlots(item)
    override fun insertGem(item: ItemStack, slotIndex: Int, gemId: String, gemLevel: Int) = gemManager.insertGem(item, slotIndex, gemId, gemLevel)
    /** 带 Player 参数的版本，会触发 GemInsertEvent */
    fun insertGem(player: Player, item: ItemStack, slotIndex: Int, gemId: String, gemLevel: Int) = gemManager.insertGem(player, item, slotIndex, gemId, gemLevel)
    override fun removeGem(item: ItemStack, slotIndex: Int) = gemManager.removeGem(item, slotIndex)
    override fun unlockSlot(item: ItemStack, slotIndex: Int) = gemManager.unlockSlot(item, slotIndex)

    override fun activateRune(player: Player, runeId: String, level: Int) = runeManager.activateRune(player, runeId, level)
    override fun deactivateRune(player: Player, runeId: String) = runeManager.deactivateRune(player, runeId)
    override fun addFragments(player: Player, runeId: String, amount: Int) = runeManager.addFragments(player, runeId, amount)
    override fun getFragments(player: Player, runeId: String) = runeManager.getFragments(player, runeId)

    override fun getEnhanceLevel(item: ItemStack) = enhanceManager.getEnhanceLevel(item)
    override fun enhance(player: Player, item: ItemStack, protections: List<ItemStack>) = enhanceManager.enhance(player, item, protections)
    override fun setEnhanceLevel(item: ItemStack, level: Int) = enhanceManager.setEnhanceLevel(item, level)

    override fun getSetId(item: ItemStack) = setManager.getSetId(item)
    override fun getActiveSets(player: Player) = setManager.detectSets(player)
}
