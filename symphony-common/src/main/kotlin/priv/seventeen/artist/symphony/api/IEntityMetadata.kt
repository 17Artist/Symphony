package priv.seventeen.artist.symphony.api

import org.bukkit.entity.Entity
import java.util.UUID

/**
 * 跨插件实体元数据共享 API。
 *
 * 常见用途：标记"流血 5 层"、"燃烧剩余 3 秒"等状态，让其他插件读/写而不走
 * Bukkit MetadataValue（后者对 UUID 不友好且有生命周期问题）。
 *
 * 实体下线时自动清理该实体的所有 namespace。
 */
interface IEntityMetadata {
    fun set(entity: Entity, namespace: String, key: String, value: Any?)
    fun set(uuid: UUID, namespace: String, key: String, value: Any?)

    fun get(entity: Entity, namespace: String, key: String): Any?
    fun get(uuid: UUID, namespace: String, key: String): Any?

    fun remove(entity: Entity, namespace: String, key: String): Any?
    fun remove(uuid: UUID, namespace: String, key: String): Any?

    /** 返回某命名空间下所有键值对的只读视图。 */
    fun all(uuid: UUID, namespace: String): Map<String, Any?>

    /** 清空某命名空间的所有键。 */
    fun clear(uuid: UUID, namespace: String)

    /** 清空该实体的全部元数据（实体下线时调用）。 */
    fun clearAll(uuid: UUID)
}
