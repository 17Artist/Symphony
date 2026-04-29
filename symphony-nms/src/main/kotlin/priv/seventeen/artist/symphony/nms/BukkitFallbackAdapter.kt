package priv.seventeen.artist.symphony.nms

import org.bukkit.Bukkit
import org.bukkit.ChatColor
import org.bukkit.NamespacedKey
import org.bukkit.attribute.Attribute
import org.bukkit.attribute.AttributeModifier as BukkitAttributeModifier
import org.bukkit.boss.BarColor
import org.bukkit.boss.BarStyle
import org.bukkit.boss.BossBar
import org.bukkit.entity.LivingEntity
import org.bukkit.entity.Monster
import org.bukkit.entity.Player
import org.bukkit.inventory.EquipmentSlot
import org.bukkit.inventory.ItemStack
import org.bukkit.persistence.PersistentDataType
import org.bukkit.util.Vector
import org.bukkit.util.io.BukkitObjectOutputStream
import org.bukkit.util.io.BukkitObjectInputStream
import net.md_5.bungee.api.ChatMessageType
import net.md_5.bungee.api.chat.TextComponent
import java.io.ByteArrayOutputStream
import java.io.ByteArrayInputStream
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

    override fun getItemAttributeModifiers(item: ItemStack): List<ItemAttributeModifier> {
        val meta = item.itemMeta ?: return emptyList()
        val modifiers = mutableListOf<ItemAttributeModifier>()
        if (meta.hasAttributeModifiers()) {
            meta.attributeModifiers?.entries()?.forEach { entry ->
                val attr = entry.key
                val mod = entry.value
                modifiers.add(ItemAttributeModifier(
                    attribute = attr.key.toString(),
                    key = mod.name,
                    amount = mod.amount,
                    operation = mod.operation.ordinal,
                    slot = mod.slot?.name?.lowercase() ?: "any"
                ))
            }
        }
        return modifiers
    }

    override fun setItemAttributeModifiers(item: ItemStack, modifiers: List<ItemAttributeModifier>): ItemStack {
        val meta = item.itemMeta ?: return item
        // 清除现有修改器
        meta.attributeModifiers?.keySet()?.forEach { attr ->
            meta.removeAttributeModifier(attr)
        }
        for (mod in modifiers) {
            val attr = Attribute.values().find { 
                it.key.toString() == mod.attribute || it.name.equals(mod.attribute, ignoreCase = true)
            } ?: continue
            val op = BukkitAttributeModifier.Operation.values().getOrNull(mod.operation)
                ?: BukkitAttributeModifier.Operation.ADD_NUMBER
            val slot = runCatching { EquipmentSlot.valueOf(mod.slot.uppercase()) }.getOrNull()
            val bukMod = if (slot != null) {
                BukkitAttributeModifier(UUID.randomUUID(), mod.key, mod.amount, op, slot)
            } else {
                BukkitAttributeModifier(UUID.randomUUID(), mod.key, mod.amount, op)
            }
            meta.addAttributeModifier(attr, bukMod)
        }
        item.itemMeta = meta
        return item
    }

    override fun serializeItem(item: ItemStack): ByteArray {
        val outputStream = ByteArrayOutputStream()
        BukkitObjectOutputStream(outputStream).use { it.writeObject(item) }
        return outputStream.toByteArray()
    }

    override fun deserializeItem(data: ByteArray): ItemStack {
        val inputStream = ByteArrayInputStream(data)
        BukkitObjectInputStream(inputStream).use {
            return it.readObject() as ItemStack
        }
    }
}

class FallbackEntityAccessor : EntityAccessor {
    override fun freezeEntity(entity: LivingEntity, ticks: Int) {
        entity.freezeTicks = ticks
    }

    override fun knockback(entity: LivingEntity, strength: Double, dirX: Double, dirZ: Double) {
        entity.velocity = entity.velocity.add(Vector(dirX * strength, 0.3 * strength, dirZ * strength))
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
    private val bossBars = mutableMapOf<String, BossBar>()

    override fun sendActionBar(player: Player, message: String) {
        player.spigot().sendMessage(
            ChatMessageType.ACTION_BAR,
            TextComponent(ChatColor.translateAlternateColorCodes('&', message))
        )
    }

    override fun sendTitle(player: Player, title: String, subtitle: String, fadeIn: Int, stay: Int, fadeOut: Int) {
        player.sendTitle(
            ChatColor.translateAlternateColorCodes('&', title),
            ChatColor.translateAlternateColorCodes('&', subtitle),
            fadeIn, stay, fadeOut
        )
    }

    override fun showBossBar(player: Player, id: String, title: String, progress: Double, color: String) {
        val barColor = runCatching { BarColor.valueOf(color.uppercase()) }.getOrDefault(BarColor.WHITE)
        val bar = Bukkit.createBossBar(ChatColor.translateAlternateColorCodes('&', title), barColor, BarStyle.SOLID)
        bar.progress = progress.coerceIn(0.0, 1.0)
        bar.addPlayer(player)
        bossBars["${player.uniqueId}:$id"]?.removeAll()
        bossBars["${player.uniqueId}:$id"] = bar
    }

    override fun updateBossBar(player: Player, id: String, title: String?, progress: Double?, color: String?) {
        val bar = bossBars["${player.uniqueId}:$id"] ?: return
        title?.let { bar.setTitle(ChatColor.translateAlternateColorCodes('&', it)) }
        progress?.let { bar.progress = it.coerceIn(0.0, 1.0) }
        color?.let { c -> runCatching { bar.color = BarColor.valueOf(c.uppercase()) } }
    }

    override fun removeBossBar(player: Player, id: String) {
        bossBars.remove("${player.uniqueId}:$id")?.removeAll()
    }
}
