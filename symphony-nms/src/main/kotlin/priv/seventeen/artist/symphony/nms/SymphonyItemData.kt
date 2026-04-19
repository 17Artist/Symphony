package priv.seventeen.artist.symphony.nms

import org.bukkit.inventory.ItemStack

/**
 * Symphony 物品数据操作统一入口。
 * 优先使用 NMSAdapter（Asteroid ItemTag），回退到 PDC。
 */
object SymphonyItemData {

    fun getString(item: ItemStack, key: String): String? {
        return if (NMSAdapterFactory.isInitialized()) {
            NMSAdapterFactory.get().getItemDataAccessor().getCustomData(item, key)
        } else {
            SymphonyPDC.getString(item, key)
        }
    }

    fun setString(item: ItemStack, key: String, value: String): ItemStack {
        return if (NMSAdapterFactory.isInitialized()) {
            NMSAdapterFactory.get().getItemDataAccessor().setCustomData(item, key, value)
        } else {
            SymphonyPDC.setString(item, key, value)
        }
    }

    fun getInt(item: ItemStack, key: String): Int? {
        return if (NMSAdapterFactory.isInitialized()) {
            NMSAdapterFactory.get().getItemDataAccessor().getCustomData(item, key)?.toIntOrNull()
        } else {
            SymphonyPDC.getInt(item, key)
        }
    }

    fun setInt(item: ItemStack, key: String, value: Int): ItemStack {
        return if (NMSAdapterFactory.isInitialized()) {
            NMSAdapterFactory.get().getItemDataAccessor().setCustomData(item, key, value.toString())
        } else {
            SymphonyPDC.setInt(item, key, value)
        }
    }

    fun remove(item: ItemStack, key: String): ItemStack {
        return if (NMSAdapterFactory.isInitialized()) {
            NMSAdapterFactory.get().getItemDataAccessor().removeCustomData(item, key)
        } else {
            SymphonyPDC.remove(item, key)
        }
    }

    fun has(item: ItemStack, key: String): Boolean {
        return if (NMSAdapterFactory.isInitialized()) {
            NMSAdapterFactory.get().getItemDataAccessor().hasCustomData(item, key)
        } else {
            SymphonyPDC.has(item, key)
        }
    }
}
