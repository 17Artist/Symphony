package priv.seventeen.artist.symphony.api.event

import org.bukkit.entity.Player
import org.bukkit.event.Cancellable
import org.bukkit.event.Event
import org.bukkit.event.HandlerList

/**
 * 法力消耗事件 — 可取消（反魔 buff / 法力无消耗环境），amount 可修改。
 */
class SymphonyManaConsumeEvent(
    val player: Player,
    var amount: Double,
    val reason: String
) : Event(), Cancellable {
    private var cancelled = false
    override fun isCancelled() = cancelled
    override fun setCancelled(cancel: Boolean) { cancelled = cancel }
    override fun getHandlers() = handlerList
    companion object { @JvmStatic val handlerList = HandlerList() }
}
