package priv.seventeen.artist.symphony.api.event

import org.bukkit.entity.LivingEntity
import org.bukkit.event.Cancellable
import org.bukkit.event.Event
import org.bukkit.event.HandlerList
import priv.seventeen.artist.symphony.api.trigger.TriggerType

/** 触发器派发前事件 — 可取消，允许插件在全局拦截某类触发器。 */
class TriggerDispatchEvent(
    val type: TriggerType,
    val entity: LivingEntity,
    val context: Map<String, Any?>
) : Event(), Cancellable {
    private var cancelled = false
    override fun isCancelled() = cancelled
    override fun setCancelled(cancel: Boolean) { cancelled = cancel }
    override fun getHandlers() = handlerList
    companion object { @JvmStatic val handlerList = HandlerList() }
}
