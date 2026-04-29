package priv.seventeen.artist.symphony.nms

import org.bukkit.Bukkit
import org.bukkit.ChatColor
import org.bukkit.attribute.Attribute
import org.bukkit.attribute.AttributeModifier as BukkitAttributeModifier
import org.bukkit.boss.BarColor
import org.bukkit.boss.BarStyle
import org.bukkit.boss.BossBar
import org.bukkit.entity.LivingEntity
import org.bukkit.entity.Player
import org.bukkit.inventory.EquipmentSlot
import org.bukkit.inventory.ItemStack
import org.bukkit.util.Vector
import org.bukkit.util.io.BukkitObjectOutputStream
import org.bukkit.util.io.BukkitObjectInputStream
import net.md_5.bungee.api.ChatMessageType
import net.md_5.bungee.api.chat.TextComponent
import priv.seventeen.artist.asteroid.AsteroidAPI
import priv.seventeen.artist.asteroid.item.ItemTag
import priv.seventeen.artist.asteroid.item.ItemTagData
import java.io.ByteArrayOutputStream
import java.io.ByteArrayInputStream
import java.util.UUID

/**
 * Asteroid NMS 适配器 — 使用 Blink 的 Asteroid 模块实现跨版本 NMS 操作。
 * 覆盖 MC 1.18.2 ~ 最新版本，API 对调用方透明。
 */
class AsteroidNMSAdapter : NMSAdapter {
    override val version = "asteroid-${AsteroidAPI.getMcVersion()}"

    override fun getAttributeBridge() = AsteroidAttributeBridge()
    override fun getItemDataAccessor() = AsteroidItemDataAccessor()
    override fun getEntityAccessor() = AsteroidEntityAccessor()
    override fun getDisplayAdapter() = AsteroidDisplayAdapter()
}

/**
 * 属性桥接 — 委托给 Asteroid AttributeBridge。
 * 自动处理 1.20.4-（UUID）和 1.21+（ResourceLocation）的差异。
 */
class AsteroidAttributeBridge : AttributeBridge {
    private val bridge get() = AsteroidAPI.getAttributeBridge()

    override fun setModifier(entity: LivingEntity, attribute: String, key: String, amount: Double, operation: Int) {
        val attrId = resolveAttributeId(attribute)
        bridge.setModifier(entity, attrId, key, amount, operation)
    }

    override fun removeModifier(entity: LivingEntity, attribute: String, key: String) {
        val attrId = resolveAttributeId(attribute)
        bridge.removeModifier(entity, attrId, key)
    }

    override fun removeAllSymphonyModifiers(entity: LivingEntity) {
        bridge.removeAllModifiers(entity, "symphony:")
    }

    override fun getBaseValue(entity: LivingEntity, attribute: String): Double {
        val attrId = resolveAttributeId(attribute)
        return bridge.getBaseValue(entity, attrId)
    }

    override fun getFinalValue(entity: LivingEntity, attribute: String): Double {
        val attrId = resolveAttributeId(attribute)
        return bridge.getFinalValue(entity, attrId)
    }

    override fun hasAttribute(entity: LivingEntity, attribute: String): Boolean {
        val attrId = resolveAttributeId(attribute)
        return bridge.hasAttribute(entity, attrId)
    }

    override fun getAvailableAttributes(): List<String> {
        return bridge.getAvailableAttributes()
    }

    /**
     * 将 Symphony 的属性 ID 格式转换为当前版本的 Minecraft 属性 ID。
     * Symphony 使用 "generic.max_health" 格式，
     * Asteroid 需要 "minecraft:generic.max_health"（1.20.4-）或 "minecraft:max_health"（1.21+）。
     */
    private fun resolveAttributeId(id: String): String {
        if (id.startsWith("minecraft:")) return id

        // 尝试在可用属性列表中查找匹配
        val available = bridge.getAvailableAttributes()

        // 直接匹配 minecraft:xxx
        val withPrefix = "minecraft:$id"
        if (withPrefix in available) return withPrefix

        // 尝试去掉 generic. 前缀匹配（1.21+ 格式）
        if (id.startsWith("generic.")) {
            val short = "minecraft:${id.removePrefix("generic.")}"
            if (short in available) return short
        }

        // 尝试加上 generic. 前缀匹配（1.20.4- 格式）
        if (!id.startsWith("generic.")) {
            val long = "minecraft:generic.$id"
            if (long in available) return long
        }

        // 回退：直接加 minecraft: 前缀
        return withPrefix
    }
}

/**
 * 物品数据操作 — 使用 Asteroid ItemTag 实现跨版本 NBT/DataComponent 操作。
 */
class AsteroidItemDataAccessor : ItemDataAccessor {

    override fun getCustomData(item: ItemStack, key: String): String? {
        val tag = ItemTag.fromItemStack(item)
        return tag.getDeep("symphony.$key")?.asString()
    }

    override fun setCustomData(item: ItemStack, key: String, value: String): ItemStack {
        val tag = ItemTag.fromItemStack(item)
        tag.putDeep("symphony.$key", ItemTagData.of(value))
        return tag.saveTo(item)
    }

    override fun removeCustomData(item: ItemStack, key: String): ItemStack {
        val tag = ItemTag.fromItemStack(item)
        tag.removeDeep("symphony.$key")
        return tag.saveTo(item)
    }

    override fun hasCustomData(item: ItemStack, key: String): Boolean {
        val tag = ItemTag.fromItemStack(item)
        return tag.getDeep("symphony.$key") != null
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

/**
 * 实体操作 — 使用 Asteroid EntityNMS。
 */
class AsteroidEntityAccessor : EntityAccessor {
    override fun freezeEntity(entity: LivingEntity, ticks: Int) {
        entity.freezeTicks = ticks
    }

    override fun knockback(entity: LivingEntity, strength: Double, dirX: Double, dirZ: Double) {
        entity.velocity = entity.velocity.add(
            Vector(dirX * strength, 0.3 * strength, dirZ * strength)
        )
    }

    override fun setNoDamageTicks(entity: LivingEntity, ticks: Int) {
        entity.noDamageTicks = ticks
    }

    override fun getTrueMaxHealth(entity: LivingEntity): Double {
        return try {
            AsteroidAPI.getAttributeBridge().getFinalValue(entity, resolveHealthAttr())
        } catch (e: Exception) {
            entity.getAttribute(Attribute.GENERIC_MAX_HEALTH)?.value ?: 20.0
        }
    }

    override fun isUndead(entity: LivingEntity): Boolean {
        return entity.type.name in UNDEAD_TYPES
    }

    override fun isArthropod(entity: LivingEntity): Boolean {
        return entity.type.name in ARTHROPOD_TYPES
    }

    override fun getBiomeKey(entity: LivingEntity): String {
        return entity.location.block.biome.name.lowercase()
    }

    override fun isOutdoor(entity: LivingEntity): Boolean {
        val loc = entity.location
        return (loc.world?.getHighestBlockYAt(loc) ?: 0) <= loc.blockY
    }

    private fun resolveHealthAttr(): String {
        val available = AsteroidAPI.getAttributeBridge().getAvailableAttributes()
        return when {
            "minecraft:max_health" in available -> "minecraft:max_health"
            "minecraft:generic.max_health" in available -> "minecraft:generic.max_health"
            else -> "minecraft:generic.max_health"
        }
    }

    companion object {
        private val UNDEAD_TYPES = setOf(
            "ZOMBIE", "SKELETON", "WITHER_SKELETON", "WITHER",
            "ZOMBIE_VILLAGER", "HUSK", "DROWNED", "STRAY", "PHANTOM",
            "ZOMBIFIED_PIGLIN", "ZOGLIN", "SKELETON_HORSE", "ZOMBIE_HORSE"
        )
        private val ARTHROPOD_TYPES = setOf(
            "SPIDER", "CAVE_SPIDER", "BEE", "SILVERFISH", "ENDERMITE"
        )
    }
}

/**
 * 显示适配 — 使用 Bukkit API（高版本已足够完善）。
 */
class AsteroidDisplayAdapter : DisplayAdapter {
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
