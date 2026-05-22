package priv.seventeen.artist.symphony.core.data

import org.bukkit.Bukkit
import org.bukkit.entity.LivingEntity
import priv.seventeen.artist.symphony.api.event.BuffExpireEvent
import priv.seventeen.artist.symphony.core.attribute.AttributeCache

/**
 * 集中处理 [ActiveBuff] 的移除并发布 [BuffExpireEvent]。
 *
 * 历史调用点直接 `activeBuffs.removeIf` / `clear()` 不会发事件，新代码请走这里。
 * 旧调用点逐步迁移即可。
 */
object BuffOps {

    /** 按谓词移除并对每条移除的 buff 发布 [BuffExpireEvent]。返回移除数量。 */
    fun removeWhere(
        entity: LivingEntity,
        runtime: RuntimeData,
        reason: BuffExpireEvent.ExpireReason,
        predicate: (ActiveBuff) -> Boolean
    ): Int {
        if (runtime.activeBuffs.isEmpty()) return 0
        var removed = 0
        val iter = runtime.activeBuffs.iterator()
        while (iter.hasNext()) {
            val buff = iter.next()
            if (predicate(buff)) {
                iter.remove()
                removed++
                Bukkit.getPluginManager().callEvent(
                    BuffExpireEvent(entity, buff.id, buff.attribute, reason)
                )
            }
        }
        if (removed > 0) AttributeCache.markDirty(entity.uniqueId)
        return removed
    }

    /** 清空全部 buff 并对每条发布事件。 */
    fun clearAll(
        entity: LivingEntity,
        runtime: RuntimeData,
        reason: BuffExpireEvent.ExpireReason
    ): Int = removeWhere(entity, runtime, reason) { true }
}
