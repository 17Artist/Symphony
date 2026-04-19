package priv.seventeen.artist.symphony.nms

import org.bukkit.Bukkit
import org.bukkit.ChatColor
import org.bukkit.NamespacedKey
import org.bukkit.attribute.Attribute
import org.bukkit.attribute.AttributeModifier as BukkitAttributeModifier
import org.bukkit.entity.LivingEntity
import org.bukkit.entity.Monster
import org.bukkit.entity.Player
import org.bukkit.inventory.ItemStack
import org.bukkit.persistence.PersistentDataType
import java.util.UUID

/**
 * Bukkit API 兜底适配器。
 * 不使用 NMS，仅通过 Bukkit API 实现基础功能。
 * 后续可替换为 Asteroid 实现。
 */
class BukkitFallbackAdapter : NMSAdapter {
    override val version = "bukkit-fallback"

    override fun getAttributeBridge() = FallbackAttributeBridge()
    override fun getItemDataAccessor() = FallbackItemDataAccessor()
    override fun getEntityAccessor() = FallbackEntityAccessor()
    override fun getDisplayAdapter() = FallbackDisplayAdapter()
}

class FallbackAttributeBridge : AttributeBridge {
    private fun keyToUUID(key: String): UUID = UUID.nameUUIDFromBytes(key.toByteArray(Charsets.UTF_8))

    private fun resolveAttribute(attribute: String): Attribute? {
        return try {
            val name = attribute.replace("generic.", "GENERIC_")
                .replace(".", "_")
                .uppercase()
            Attribute.valueOf(name)
        } catch (e: Exception) { null }
    }

    override fun setModifier(entity: LivingEntity, attribute: String, key: String, amount: Double, operation: Int) {
        val attr = resolveAttribute(attribute) ?: return
        val instance = entity.getAttribute(attr) ?: return
        val uuid = keyToUUID(key)
        val op = when (operation) {
            0 -> BukkitAttributeModifier.Operation.ADD_NUMBER
            1 -> BukkitAttributeModifier.Operation.ADD_SCALAR
            else -> BukkitAttributeModifier.Operation.MULTIPLY_SCALAR_1
        }
        // Remove existing
        instance.modifiers.filter { it.uniqueId == uuid }.forEach { instance.removeModifier(it) }
        instance.addModifier(BukkitAttributeModifier(uuid, key, amount, op))
    }

    override fun removeModifier(entity: LivingEntity, attribute: String, key: String) {
        val attr = resolveAttribute(attribute) ?: return
        val instance = entity.getAttribute(attr) ?: return
        val uuid = keyToUUID(key)
        instance.modifiers.filter { it.uniqueId == uuid }.forEach { instance.removeModifier(it) }
    }

    override fun removeAllSymphonyModifiers(entity: LivingEntity) {
        for (attr in Attribute.values()) {
            val instance = entity.getAttribute(attr) ?: continue
            instance.modifiers.filter { it.name.startsWith("symphony:") }.forEach {
                instance.removeModifier(it)
            }
        }
    }

    override fun getBaseValue(entity: LivingEntity, attribute: String): Double {
        val attr = resolveAttribute(attribute) ?: return 0.0
        return entity.getAttribute(attr)?.baseValue ?: 0.0
    }

    override fun getFinalValue(entity: LivingEntity, attribute: String): Double {
        val attr = resolveAttribute(attribute) ?: return 0.0
        return entity.getAttribute(attr)?.value ?: 0.0
    }

    override fun hasAttribute(entity: LivingEntity, attribute: String): Boolean {
        val attr = resolveAttribute(attribute) ?: return false
        return entity.getAttribute(attr) != null
    }

    override fun getAvailableAttributes(): List<String> {
        return Attribute.values().map { it.name.lowercase().replace("_", ".") }
    }
}

class FallbackItemDataAccessor : ItemDataAccessor {
    private val namespace = "symphony"

    override fun getCustomData(item: ItemStack, key: String): String? {
        val meta = item.itemMeta ?: return null
        val nk = NamespacedKey(namespace, key)
        return meta.persistentDataContainer.get(nk, PersistentDataType.STRING)
    }

    override fun setCustomData(item: ItemStack, key: String, value: String): ItemStack {
        val meta = item.itemMeta ?: return item
        val nk = NamespacedKey(namespace, key)
        meta.persistentDataContainer.set(nk, PersistentDataType.STRING, value)
        item.itemMeta = meta
        return item
    }

    override fun removeCustomData(item: ItemStack, key: String): ItemStack {
        val meta = item.itemMeta ?: return item
        val nk = NamespacedKey(namespace, key)
        meta.persistentDataContainer.remove(nk)
        item.itemMeta = meta
        return item
    }

    override fun hasCustomData(item: ItemStack, key: String): Boolean {
        val meta = item.itemMeta ?: return false
        val nk = NamespacedKey(namespace, key)
        return meta.persistentDataContainer.has(nk, PersistentDataType.STRING)
    }

    override fun setLore(item: ItemStack, lore: List<String>): ItemStack {
        val meta = item.itemMeta ?: return item
        meta.lore = lore.map { ChatColor.translateAlternateColorCodes('&', it) }
        item.itemMeta = meta
        return item
    }

    override fun setDisplayName(item: ItemStack, name: String): ItemStack {
        val meta = item.itemMeta ?: return item
        meta.setDisplayName(ChatColor.translateAlternateColorCodes('&', name))
        item.itemMeta = meta
        return item
    }

    override fun setCustomModelData(item: ItemStack, data: Int): ItemStack {
        val meta = item.itemMeta ?: return item
        meta.setCustomModelData(data)
        item.itemMeta = meta
        return item
    }
}

class FallbackEntityAccessor : EntityAccessor {
    override fun freezeEntity(entity: LivingEntity, ticks: Int) {
        entity.freezeTicks = ticks
    }

    override fun knockback(entity: LivingEntity, strength: Double, dirX: Double, dirZ: Double) {
        entity.velocity = entity.velocity.add(org.bukkit.util.Vector(dirX * strength, 0.3 * strength, dirZ * strength))
    }

    override fun setNoDamageTicks(entity: LivingEntity, ticks: Int) {
        entity.noDamageTicks = ticks
    }

    override fun getTrueMaxHealth(entity: LivingEntity): Double {
        return entity.getAttribute(Attribute.GENERIC_MAX_HEALTH)?.value ?: 20.0
    }

    override fun isUndead(entity: LivingEntity): Boolean {
        return entity.type.name in setOf("ZOMBIE", "SKELETON", "WITHER_SKELETON", "WITHER",
            "ZOMBIE_VILLAGER", "HUSK", "DROWNED", "STRAY", "PHANTOM", "ZOMBIFIED_PIGLIN",
            "ZOGLIN", "SKELETON_HORSE", "ZOMBIE_HORSE")
    }

    override fun isArthropod(entity: LivingEntity): Boolean {
        return entity.type.name in setOf("SPIDER", "CAVE_SPIDER", "BEE", "SILVERFISH", "ENDERMITE")
    }

    override fun getBiomeKey(entity: LivingEntity): String {
        return entity.location.block.biome.name.lowercase()
    }

    override fun isOutdoor(entity: LivingEntity): Boolean {
        val highestY = entity.location.world?.getHighestBlockYAt(entity.location) ?: return false
        return highestY <= entity.location.blockY
    }
}

class FallbackDisplayAdapter : DisplayAdapter {
    override fun sendActionBar(player: Player, message: String) {
        player.spigot().sendMessage(
            net.md_5.bungee.api.ChatMessageType.ACTION_BAR,
            net.md_5.bungee.api.chat.TextComponent(ChatColor.translateAlternateColorCodes('&', message))
        )
    }

    override fun sendTitle(player: Player, title: String, subtitle: String, fadeIn: Int, stay: Int, fadeOut: Int) {
        player.sendTitle(
            ChatColor.translateAlternateColorCodes('&', title),
            ChatColor.translateAlternateColorCodes('&', subtitle),
            fadeIn, stay, fadeOut
        )
    }
}
