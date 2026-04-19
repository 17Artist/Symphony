package priv.seventeen.artist.symphony.api.event

import org.bukkit.entity.Player
import org.bukkit.event.Cancellable
import org.bukkit.event.Event
import org.bukkit.event.HandlerList
import org.bukkit.inventory.ItemStack
import priv.seventeen.artist.symphony.api.growth.EnhanceResult

class EnhanceEvent(
    val player: Player,
    val item: ItemStack,
    val oldLevel: Int,
    val newLevel: Int,
    val result: EnhanceResult
) : Event(), Cancellable {
    private var cancelled = false
    override fun isCancelled() = cancelled
    override fun setCancelled(cancel: Boolean) { cancelled = cancel }
    override fun getHandlers() = handlerList
    companion object {
        @JvmStatic val handlerList = HandlerList()
    }
}
