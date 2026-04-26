package priv.seventeen.artist.symphony.api.affix

import org.bukkit.inventory.ItemStack

interface IAffixManager {
    fun registerAffix(affix: IAffix)
    fun getAffix(id: String): IAffix?
    fun getAllAffixes(): Collection<IAffix>
    fun getAffixesByRarity(rarity: AffixRarity): Collection<IAffix>

    fun getAffixes(item: ItemStack): List<AffixInstance>
    fun addAffix(item: ItemStack, instance: AffixInstance): Boolean
    fun removeAffix(item: ItemStack, uuid: java.util.UUID): Boolean
    fun clearAffixes(item: ItemStack)

    fun generateAffixes(poolId: String, rarity: AffixRarity, luck: Double): List<AffixInstance>
    fun applyAffixes(item: ItemStack, affixes: List<AffixInstance>)

    /** 注册自定义 action 类型（供词条 YAML / Aria 使用）。 */
    fun registerActionHandler(type: String, handler: IActionHandler)
    fun unregisterActionHandler(type: String)
    fun getRegisteredActionTypes(): Set<String>
}
