package priv.seventeen.artist.symphony.core.attribute

import java.util.UUID
import java.util.concurrent.ConcurrentHashMap

/**
 * 属性缓存 — 惰性重算。
 *
 * 两级 dirty：
 * - 全脏（[markDirty(uuid)]）：整实体下一次读取时全量 recalc。
 * - 局部脏（[markDirty(uuid, attrId)]）：仅该属性 + 其反向依赖者会被重算；其它沿用缓存。
 *
 * 反向依赖 [DependencyIndex] 由 [AttributeRegistry] 注册时同步刷新。
 */
object AttributeCache {
    private val cache = ConcurrentHashMap<UUID, MutableMap<String, Double>>()
    private val fullDirty = ConcurrentHashMap.newKeySet<UUID>()
    private val partialDirty = ConcurrentHashMap<UUID, MutableSet<String>>()

    fun get(entityId: UUID, attributeId: String): Double? = cache[entityId]?.get(attributeId)

    /** 返回属性 Map 的副本（防御性拷贝）。给到外部 API / 脚本桥用。 */
    fun getAll(entityId: UUID): Map<String, Double> = cache[entityId]?.toMap() ?: emptyMap()

    /**
     * 返回属性 Map 的**只读视图**，零拷贝。仅供 [AttributeCalculator] 等内部
     * 主线程使用，调用方**必须保证不修改返回值**（修改会污染缓存）。
     * 用于高频路径（每秒 N 次重算）避免 toMap 开销。
     */
    fun getAllView(entityId: UUID): Map<String, Double> = cache[entityId] ?: emptyMap()

    fun set(entityId: UUID, attributeId: String, value: Double) {
        cache.getOrPut(entityId) { ConcurrentHashMap() }[attributeId] = value
    }

    fun setAll(entityId: UUID, values: Map<String, Double>) {
        cache[entityId] = ConcurrentHashMap(values)
    }

    /** 全实体标脏 — 下次 recalc 全量 */
    fun markDirty(entityId: UUID) {
        fullDirty.add(entityId)
    }

    /** 单属性标脏 — 自动叠加其反向依赖者 */
    fun markDirty(entityId: UUID, attributeId: String) {
        if (entityId in fullDirty) return
        val set = partialDirty.computeIfAbsent(entityId) { ConcurrentHashMap.newKeySet() }
        set.add(attributeId)
        set.addAll(DependencyIndex.dependents(attributeId))
    }

    fun isDirty(entityId: UUID): Boolean =
        entityId in fullDirty || partialDirty[entityId]?.isNotEmpty() == true

    /** 取出局部脏属性集合；若全脏返回 null（调用方应做全量计算）。 */
    fun getDirtyAttributes(entityId: UUID): Set<String>? {
        if (entityId in fullDirty) return null
        return partialDirty[entityId]?.toSet() ?: emptySet()
    }

    fun clearDirty(entityId: UUID) {
        fullDirty.remove(entityId)
        partialDirty.remove(entityId)
    }

    fun invalidate(entityId: UUID) {
        cache.remove(entityId)
        fullDirty.remove(entityId)
        partialDirty.remove(entityId)
    }

    fun clear() {
        cache.clear()
        fullDirty.clear()
        partialDirty.clear()
    }
}
