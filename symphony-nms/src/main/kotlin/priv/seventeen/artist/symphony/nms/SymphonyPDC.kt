package priv.seventeen.artist.symphony.nms

import org.bukkit.NamespacedKey
import org.bukkit.inventory.ItemStack
import org.bukkit.persistence.PersistentDataType

/**
 * PDC 操作封装 — 跨版本一致。
 * Symphony 的物品数据（属性、词条、宝石等）全部通过此层读写。
 */
object SymphonyPDC {
    private const val NAMESPACE = "symphony"

    fun getString(item: ItemStack, key: String): String? {
        val meta = item.itemMeta ?: return null
        return meta.persistentDataContainer.get(NamespacedKey(NAMESPACE, key), PersistentDataType.STRING)
    }

    fun setString(item: ItemStack, key: String, value: String): ItemStack {
        val meta = item.itemMeta ?: return item
        meta.persistentDataContainer.set(NamespacedKey(NAMESPACE, key), PersistentDataType.STRING, value)
        item.itemMeta = meta
        return item
    }

    fun getInt(item: ItemStack, key: String): Int? {
        val meta = item.itemMeta ?: return null
        return meta.persistentDataContainer.get(NamespacedKey(NAMESPACE, key), PersistentDataType.INTEGER)
    }

    fun setInt(item: ItemStack, key: String, value: Int): ItemStack {
        val meta = item.itemMeta ?: return item
        meta.persistentDataContainer.set(NamespacedKey(NAMESPACE, key), PersistentDataType.INTEGER, value)
        item.itemMeta = meta
        return item
    }

    fun remove(item: ItemStack, key: String): ItemStack {
        val meta = item.itemMeta ?: return item
        meta.persistentDataContainer.remove(NamespacedKey(NAMESPACE, key))
        item.itemMeta = meta
        return item
    }

    fun has(item: ItemStack, key: String): Boolean {
        val meta = item.itemMeta ?: return false
        return meta.persistentDataContainer.has(NamespacedKey(NAMESPACE, key), PersistentDataType.STRING)
    }
}
