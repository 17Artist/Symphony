package priv.seventeen.artist.symphony.api.event

import org.bukkit.entity.LivingEntity
import org.bukkit.event.Cancellable
import org.bukkit.event.Event
import org.bukkit.event.HandlerList

/** Buff 应用事件 — 新 buff 加入前触发，可取消阻止应用；value/duration 可改。 */
class BuffApplyEvent(
    val target: LivingEntity,
    val buffId: String,
    val attributeId: String,
    var value: Double,
    var durationMs: Long,
    val source: String
) : Event(), Cancellable {
    private var cancelled = false
    override fun isCancelled() = cancelled
    override fun setCancelled(cancel: Boolean) { cancelled = cancel }
    override fun getHandlers() = handlerList
    companion object { @JvmStatic val handlerList = HandlerList() }
}
