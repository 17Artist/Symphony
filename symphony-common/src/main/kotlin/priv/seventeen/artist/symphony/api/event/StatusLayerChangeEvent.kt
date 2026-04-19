package priv.seventeen.artist.symphony.api.event

import org.bukkit.entity.LivingEntity
import org.bukkit.event.Event
import org.bukkit.event.HandlerList

/** 状态层数变化事件（叠加/扣除/到期）。 */
class StatusLayerChangeEvent(
    val entity: LivingEntity,
    val statusId: String,
    val oldStacks: Int,
    val newStacks: Int,
    val reason: Reason
) : Event() {
    enum class Reason { STACK, UNSTACK, EXPIRE, CLEAR }
    val delta: Int get() = newStacks - oldStacks
    override fun getHandlers() = handlerList
    companion object { @JvmStatic val handlerList = HandlerList() }
}
