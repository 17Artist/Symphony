package priv.seventeen.artist.symphony.api.event

import org.bukkit.entity.LivingEntity
import org.bukkit.event.Cancellable
import org.bukkit.event.Event
import org.bukkit.event.HandlerList

/**
 * 治疗事件 — amount 可在监听器修改，可取消（反治 debuff、无法回血环境）。
 */
class SymphonyHealEvent(
    val target: LivingEntity,
    val source: LivingEntity?,
    var amount: Double,
    val reason: String
) : Event(), Cancellable {
    private var cancelled = false
    override fun isCancelled() = cancelled
    override fun setCancelled(cancel: Boolean) { cancelled = cancel }
    override fun getHandlers() = handlerList
    companion object { @JvmStatic val handlerList = HandlerList() }
}
