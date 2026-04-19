package priv.seventeen.artist.symphony.api.event

import org.bukkit.entity.Player
import org.bukkit.event.Cancellable
import org.bukkit.event.Event
import org.bukkit.event.HandlerList
import org.bukkit.inventory.ItemStack

class GemInsertEvent(
    val player: Player,
    val item: ItemStack,
    val slotIndex: Int,
    val gemId: String,
    val gemLevel: Int
) : Event(), Cancellable {
    private var cancelled = false
    override fun isCancelled() = cancelled
    override fun setCancelled(cancel: Boolean) { cancelled = cancel }
    override fun getHandlers() = handlerList
    companion object {
        @JvmStatic val handlerList = HandlerList()
    }
}
