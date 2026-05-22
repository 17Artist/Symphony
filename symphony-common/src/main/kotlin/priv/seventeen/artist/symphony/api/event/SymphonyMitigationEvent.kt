package priv.seventeen.artist.symphony.api.event

import org.bukkit.entity.LivingEntity
import org.bukkit.event.Cancellable
import org.bukkit.event.Event
import org.bukkit.event.HandlerList

/**
 * 伤害流水线 — 减伤阶段（护甲/穿透/伤害减免/格挡）计算后，元素附加伤害前。
 *
 * 可修改 [finalPhysical]；取消视为不受伤。
 *
 * [reductionPercent] / [isCritical] / [blocked] 仅作只读信息：事件发布时减伤计算
 * 已完成，监听器修改这些字段对最终伤害**无影响**。要调整减伤幅度请直接改
 * [finalPhysical]。
 */
class SymphonyMitigationEvent(
    val attacker: LivingEntity,
    val victim: LivingEntity,
    val baseDamage: Double,
    var finalPhysical: Double,
    val reductionPercent: Double,
    val isCritical: Boolean,
    val blocked: Boolean
) : Event(), Cancellable {
    private var cancelled = false
    override fun isCancelled() = cancelled
    override fun setCancelled(cancel: Boolean) { cancelled = cancel }
    override fun getHandlers() = handlerList
    companion object { @JvmStatic val handlerList = HandlerList() }
}
