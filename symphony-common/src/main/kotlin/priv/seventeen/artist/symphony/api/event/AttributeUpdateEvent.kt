package priv.seventeen.artist.symphony.api.event

import org.bukkit.entity.LivingEntity
import org.bukkit.event.Cancellable
import org.bukkit.event.Event
import org.bukkit.event.HandlerList

class AttributeUpdateEvent(
    val entity: LivingEntity,
    val attributeId: String,
    val oldValue: Double,
    val newValue: Double,
    val source: String
) : Event(), Cancellable {
    private var cancelled = false
    override fun isCancelled() = cancelled
    override fun setCancelled(cancel: Boolean) { cancelled = cancel }
    override fun getHandlers() = handlerList
    companion object {
        @JvmStatic val handlerList = HandlerList()
    }
}
