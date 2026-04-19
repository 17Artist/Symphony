package priv.seventeen.artist.symphony.core.attribute

import priv.seventeen.artist.symphony.api.attribute.IAttributeProvider
import java.util.concurrent.CopyOnWriteArrayList

/**
 * 属性来源注册表 — 按优先级排序。
 */
object AttributeProviderRegistry {
    private val providers = CopyOnWriteArrayList<IAttributeProvider>()

    fun register(provider: IAttributeProvider) {
        providers.add(provider)
        providers.sortBy { it.priority }
    }

    fun unregister(id: String) {
        providers.removeIf { it.id == id }
    }

    fun getAll(): List<IAttributeProvider> = providers.toList()

    fun get(id: String): IAttributeProvider? = providers.find { it.id == id }

    fun clear() {
        providers.clear()
    }
}
