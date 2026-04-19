package priv.seventeen.artist.symphony.api.event

import org.bukkit.entity.LivingEntity
import org.bukkit.event.Event
import org.bukkit.event.HandlerList

/** Buff 过期 / 被移除事件。不可取消；通知用。 */
class BuffExpireEvent(
    val target: LivingEntity,
    val buffId: String,
    val attributeId: String,
    val reason: ExpireReason
) : Event() {
    enum class ExpireReason { TIMEOUT, MANUAL_REMOVE, REPLACED, PLAYER_QUIT }
    override fun getHandlers() = handlerList
    companion object { @JvmStatic val handlerList = HandlerList() }
}
