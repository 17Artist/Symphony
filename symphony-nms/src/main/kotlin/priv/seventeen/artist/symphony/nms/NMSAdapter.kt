package priv.seventeen.artist.symphony.nms

import org.bukkit.entity.LivingEntity
import org.bukkit.entity.Player
import org.bukkit.inventory.ItemStack

/**
 * NMS 适配器顶层接口。
 * 每个支持的版本提供一个实现类。
 */
interface NMSAdapter {
    val version: String
    fun getAttributeBridge(): AttributeBridge
    fun getItemDataAccessor(): ItemDataAccessor
    fun getEntityAccessor(): EntityAccessor
    fun getDisplayAdapter(): DisplayAdapter
}

interface AttributeBridge {
    fun setModifier(entity: LivingEntity, attribute: String, key: String, amount: Double, operation: Int)
    fun removeModifier(entity: LivingEntity, attribute: String, key: String)
    fun removeAllSymphonyModifiers(entity: LivingEntity)
    fun getBaseValue(entity: LivingEntity, attribute: String): Double
    fun getFinalValue(entity: LivingEntity, attribute: String): Double
    fun hasAttribute(entity: LivingEntity, attribute: String): Boolean
    fun getAvailableAttributes(): List<String>
}

interface ItemDataAccessor {
    fun getCustomData(item: ItemStack, key: String): String?
    fun setCustomData(item: ItemStack, key: String, value: String): ItemStack
    fun removeCustomData(item: ItemStack, key: String): ItemStack
    fun hasCustomData(item: ItemStack, key: String): Boolean
    fun setLore(item: ItemStack, lore: List<String>): ItemStack
    fun setDisplayName(item: ItemStack, name: String): ItemStack
    fun setCustomModelData(item: ItemStack, data: Int): ItemStack
}

data class ItemAttributeModifier(
    val attribute: String,
    val key: String,
    val amount: Double,
    val operation: Int,
    val slot: String
)

interface EntityAccessor {
    fun freezeEntity(entity: LivingEntity, ticks: Int)
    fun knockback(entity: LivingEntity, strength: Double, dirX: Double, dirZ: Double)
    fun setNoDamageTicks(entity: LivingEntity, ticks: Int)
    fun getTrueMaxHealth(entity: LivingEntity): Double
    fun isUndead(entity: LivingEntity): Boolean
    fun isArthropod(entity: LivingEntity): Boolean
    fun getBiomeKey(entity: LivingEntity): String
    fun isOutdoor(entity: LivingEntity): Boolean
}

interface DisplayAdapter {
    fun sendActionBar(player: Player, message: String)
    fun sendTitle(player: Player, title: String, subtitle: String, fadeIn: Int, stay: Int, fadeOut: Int)
}
