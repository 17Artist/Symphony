package priv.seventeen.artist.symphony.api.event

import org.bukkit.entity.LivingEntity
import org.bukkit.event.Event
import org.bukkit.event.HandlerList
import org.bukkit.inventory.ItemStack
import priv.seventeen.artist.symphony.api.affix.AffixInstance

/** 装备栏变动导致词条"卸下"时触发（进入失活态）。 */
class AffixUnequipEvent(
    val entity: LivingEntity,
    val item: ItemStack?,
    val affix: AffixInstance
) : Event() {
    override fun getHandlers() = handlerList
    companion object { @JvmStatic val handlerList = HandlerList() }
}
