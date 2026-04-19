package priv.seventeen.artist.symphony.api.attribute

import org.bukkit.entity.LivingEntity

/**
 * 编程式属性变更订阅器 — 不走 Bukkit 事件系统，开销更低、可按属性 id 过滤。
 *
 * 用法：
 * ```
 * SymphonyAPI.getAttributeManager().addListener(object : AttributeListener {
 *     override val attributeIds = setOf("physical_damage", "critical_chance")
 *     override fun onChange(entity, attrId, oldValue, newValue) { ... }
 * })
 * ```
 */
interface AttributeListener {
    /** 感兴趣的属性 id 集合；空集合 = 监听所有属性。 */
    val attributeIds: Set<String> get() = emptySet()
    fun onChange(entity: LivingEntity, attributeId: String, oldValue: Double, newValue: Double)
}
