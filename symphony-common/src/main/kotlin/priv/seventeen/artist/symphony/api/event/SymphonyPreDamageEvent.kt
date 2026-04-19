package priv.seventeen.artist.symphony.api.event

import org.bukkit.entity.LivingEntity
import org.bukkit.event.Cancellable
import org.bukkit.event.Event
import org.bukkit.event.HandlerList

/**
 * 伤害流水线 — 暴击判定后、减伤前。
 *
 * 阶段顺序：
 *   Pre（此事件）→ Mitigation → PostApply
 *
 * 可修改字段：[baseDamage] / [isCritical]；
 * 取消会中断整个伤害流程（victim 不受伤）。
 */
class SymphonyPreDamageEvent(
    val attacker: LivingEntity,
    val victim: LivingEntity,
    var baseDamage: Double,
    var isCritical: Boolean,
    val damageType: String
) : Event(), Cancellable {
    private var cancelled = false
    override fun isCancelled() = cancelled
    override fun setCancelled(cancel: Boolean) { cancelled = cancel }
    override fun getHandlers() = handlerList
    companion object { @JvmStatic val handlerList = HandlerList() }
}
