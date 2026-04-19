package priv.seventeen.artist.symphony.core.attribute

import java.util.concurrent.ConcurrentHashMap

/**
 * 属性反向依赖索引：dep → 谁依赖了 dep（直接 + 传递闭包）。
 *
 * 由 [AttributeRegistry] 在 register / clear 时维护。
 * 当某属性被局部标脏时，[AttributeCache.markDirty(uuid, attrId)] 会查此表
 * 把所有受影响的派生属性一并加入 dirty 集合。
 */
object DependencyIndex {
    // 直接反向：dep → {依赖 dep 的属性}
    private val direct = ConcurrentHashMap<String, MutableSet<String>>()
    // 传递闭包缓存：dep → 所有传递依赖者
    private val closureCache = ConcurrentHashMap<String, Set<String>>()

    fun rebuild(allDefs: Collection<AttributeDefinition>) {
        direct.clear()
        closureCache.clear()
        for (def in allDefs) {
            for (dep in def.dependsOn) {
                direct.computeIfAbsent(dep) { ConcurrentHashMap.newKeySet() }.add(def.id)
            }
        }
    }

    /** 返回 dep 的所有传递依赖者（含直接 + 间接），不含 dep 自身。 */
    fun dependents(dep: String): Set<String> = closureCache.computeIfAbsent(dep) {
        val result = mutableSetOf<String>()
        val stack = ArrayDeque<String>()
        stack.add(dep)
        while (stack.isNotEmpty()) {
            val cur = stack.removeLast()
            for (parent in direct[cur].orEmpty()) {
                if (result.add(parent)) stack.add(parent)
            }
        }
        result
    }

    fun clear() {
        direct.clear()
        closureCache.clear()
    }
}
