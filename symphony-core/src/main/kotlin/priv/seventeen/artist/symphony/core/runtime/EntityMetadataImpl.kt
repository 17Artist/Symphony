package priv.seventeen.artist.symphony.core.runtime

import org.bukkit.entity.Entity
import priv.seventeen.artist.symphony.api.IEntityMetadata
import java.util.UUID
import java.util.concurrent.ConcurrentHashMap

/**
 * [IEntityMetadata] 的内存实现。
 *
 * 结构：uuid → namespace → key → value。实体下线时由 [PlayerQuitListener]（或外部）
 * 调用 [clearAll] 回收。
 */
object EntityMetadataImpl : IEntityMetadata {
    private val store = ConcurrentHashMap<UUID, ConcurrentHashMap<String, ConcurrentHashMap<String, Any?>>>()

    override fun set(entity: Entity, namespace: String, key: String, value: Any?) =
        set(entity.uniqueId, namespace, key, value)

    override fun set(uuid: UUID, namespace: String, key: String, value: Any?) {
        val ns = store.computeIfAbsent(uuid) { ConcurrentHashMap() }
            .computeIfAbsent(namespace) { ConcurrentHashMap() }
        if (value == null) ns.remove(key) else ns[key] = value
    }

    override fun get(entity: Entity, namespace: String, key: String): Any? =
        get(entity.uniqueId, namespace, key)

    override fun get(uuid: UUID, namespace: String, key: String): Any? =
        store[uuid]?.get(namespace)?.get(key)

    override fun remove(entity: Entity, namespace: String, key: String): Any? =
        remove(entity.uniqueId, namespace, key)

    override fun remove(uuid: UUID, namespace: String, key: String): Any? =
        store[uuid]?.get(namespace)?.remove(key)

    override fun all(uuid: UUID, namespace: String): Map<String, Any?> =
        store[uuid]?.get(namespace)?.toMap() ?: emptyMap()

    override fun clear(uuid: UUID, namespace: String) {
        store[uuid]?.remove(namespace)
    }

    override fun clearAll(uuid: UUID) {
        store.remove(uuid)
    }
}
