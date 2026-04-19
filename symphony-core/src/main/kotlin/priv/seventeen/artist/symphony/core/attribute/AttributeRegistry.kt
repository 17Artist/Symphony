package priv.seventeen.artist.symphony.core.attribute

import java.util.concurrent.ConcurrentHashMap

/**
 * 属性注册表 — 由 Aria 脚本填充，reload 时重建。
 */
object AttributeRegistry {
    private val attributes = ConcurrentHashMap<String, AttributeDefinition>()

    fun register(attr: AttributeDefinition) {
        attributes[attr.id] = attr
        DependencyIndex.rebuild(attributes.values)
    }

    fun unregister(id: String) {
        attributes.remove(id)
        DependencyIndex.rebuild(attributes.values)
    }

    fun get(id: String): AttributeDefinition? = attributes[id]

    fun getAll(): Collection<AttributeDefinition> = attributes.values

    fun getByCategory(category: String): Collection<AttributeDefinition> {
        return attributes.values.filter { it.category == category }
    }

    fun getByTag(tag: String): Collection<AttributeDefinition> {
        return attributes.values.filter { tag in it.tags }
    }

    fun exists(id: String): Boolean = attributes.containsKey(id)

    fun clear() {
        attributes.clear()
        DependencyIndex.clear()
    }

    fun ids(): Set<String> = attributes.keys.toSet()
}
