package priv.seventeen.artist.symphony.api.event

import org.bukkit.entity.LivingEntity
import org.bukkit.event.Cancellable
import org.bukkit.event.Event
import org.bukkit.event.HandlerList

class SymphonyDamageEvent(
    val attacker: LivingEntity,
    val victim: LivingEntity,
    val rawDamage: Double,
    var finalDamage: Double,
    val damageType: String,
    val isCritical: Boolean,
    val elementDamages: MutableMap<String, Double> = mutableMapOf()
) : Event(), Cancellable {
    private var cancelled = false
    override fun isCancelled() = cancelled
    override fun setCancelled(cancel: Boolean) { cancelled = cancel }
    override fun getHandlers() = handlerList
    companion object {
        @JvmStatic val handlerList = HandlerList()
    }
}
