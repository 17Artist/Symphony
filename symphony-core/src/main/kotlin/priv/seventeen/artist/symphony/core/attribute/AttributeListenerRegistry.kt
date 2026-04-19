package priv.seventeen.artist.symphony.core.attribute

import org.bukkit.entity.LivingEntity
import priv.seventeen.artist.blink.BlinkLog
import priv.seventeen.artist.symphony.api.attribute.AttributeListener
import java.util.concurrent.CopyOnWriteArrayList

/**
 * 编程式属性监听器注册表 — 与 Bukkit 事件系统并行。
 *
 * 派发由 [AttributeCalculator] 在属性值变更时调用 [fire]。
 * 按监听器的 [AttributeListener.attributeIds] 过滤：空集合 = 全监听。
 */
object AttributeListenerRegistry {
    private val listeners = CopyOnWriteArrayList<AttributeListener>()

    fun add(listener: AttributeListener) { listeners.addIfAbsent(listener) }
    fun remove(listener: AttributeListener) { listeners.remove(listener) }
    fun clear() { listeners.clear() }

    fun fire(entity: LivingEntity, attributeId: String, oldValue: Double, newValue: Double) {
        for (l in listeners) {
            if (l.attributeIds.isNotEmpty() && attributeId !in l.attributeIds) continue
            try { l.onChange(entity, attributeId, oldValue, newValue) } catch (e: Exception) {
                BlinkLog.warn("AttributeListener 异常: ${e.message}")
            }
        }
    }
}
