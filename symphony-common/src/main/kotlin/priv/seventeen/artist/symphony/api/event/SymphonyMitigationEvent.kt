package priv.seventeen.artist.symphony.api.event

import org.bukkit.entity.LivingEntity
import org.bukkit.event.Cancellable
import org.bukkit.event.Event
import org.bukkit.event.HandlerList

/**
 * 伤害流水线 — 减伤阶段（护甲/穿透/伤害减免/格挡）计算后，元素附加伤害前。
 *
 * 可修改 [finalPhysical] / [reductionPercent]；取消视为不受伤。
 */
class SymphonyMitigationEvent(
    val attacker: LivingEntity,
    val victim: LivingEntity,
    val baseDamage: Double,
    var finalPhysical: Double,
    var reductionPercent: Double,
    val isCritical: Boolean,
    val blocked: Boolean
) : Event(), Cancellable {
    private var cancelled = false
    override fun isCancelled() = cancelled
    override fun setCancelled(cancel: Boolean) { cancelled = cancel }
    override fun getHandlers() = handlerList
    companion object { @JvmStatic val handlerList = HandlerList() }
}
