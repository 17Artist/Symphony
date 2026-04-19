# NMS 适配层设计

## 1. 设计目标

Symphony 需要覆盖 1.18.2 ~ 最新版本，而 Minecraft 在这个跨度内经历了多次底层 API 大改。NMS 适配层的职责是将这些差异封装在版本隔离模块中，让上层业务代码完全不感知版本差异。

NMS 层是 Symphony 的最底层模块，应当最先实现，其他所有系统都依赖它。

## 2. 版本断代分析

### 2.1 关键版本节点

| 版本 | 内部版本 | 重大变更 |
|------|----------|----------|
| 1.18.2 | v1_18_R2 | 基线版本，传统 NBT + UUID AttributeModifier |
| 1.19.4 | v1_19_R3 | 无重大 NMS 变更，Bukkit API 小调整 |
| 1.20.1 | v1_20_R1 | 无重大变更 |
| 1.20.2 | v1_20_R2 | 网络协议重构，配置阶段（Configuration Phase） |
| 1.20.4 | v1_20_R3 | 无重大 NMS 变更 |
| 1.20.5+ | v1_20_R4 | **断代**：Data Components 取代 NBT，物品系统重写 |
| 1.21 | v1_21_R1 | AttributeModifier 构造器变更：UUID → ResourceLocation |
| 1.21.2 | v1_21_R2 | Holder 系统扩展，HolderSet 替代 TagKey |
| 1.21.5 | v1_21_R3 | 武器/工具/护甲改用 Data Components（WEAPON/TOOL/ARMOR） |

### 2.2 两大断代点

**断代一：1.20.5 — Data Components 取代 NBT**

1.20.5 之前：物品自定义数据存储在 `ItemStack.getTag()` 返回的 `CompoundTag` 中。
1.20.5 之后：NBT 被 Data Components 系统取代，`ItemStack` 不再有 `tag` 字段，改用 `DataComponentMap`。

影响范围：
- 物品属性读写（Symphony 的装备属性、词条、宝石槽数据）
- 物品 AttributeModifier 的存储格式
- 自定义数据存储方式

**断代二：1.21 — AttributeModifier 标识符变更**

1.21 之前：`AttributeModifier(UUID, String, double, Operation)`
1.21 之后：`AttributeModifier(ResourceLocation, double, Operation)` — UUID 被废弃，改用 ResourceLocation 作为标识符。

影响范围：
- VanillaAttributeBridge 的属性同步逻辑
- 修改器的创建、查找、移除方式

## 3. 适配器接口设计

### 3.1 核心接口

```kotlin
/**
 * NMS 适配器顶层接口。
 * 每个支持的版本提供一个实现类，由 NMSAdapterFactory 在启动时根据服务器版本自动选择。
 */
interface NMSAdapter {

    /** 当前适配器支持的 NMS 版本标识 */
    val version: String

    // ═══════════════════════════════════════
    // 属性桥接
    // ═══════════════════════════════════════
    
    fun getAttributeBridge(): AttributeBridge

    // ═══════════════════════════════════════
    // 物品数据操作
    // ═══════════════════════════════════════
    
    fun getItemDataAccessor(): ItemDataAccessor

    // ═══════════════════════════════════════
    // 实体操作
    // ═══════════════════════════════════════
    
    fun getEntityAccessor(): EntityAccessor

    // ═══════════════════════════════════════
    // 网络/显示
    // ═══════════════════════════════════════
    
    fun getDisplayAdapter(): DisplayAdapter
}
```

### 3.2 子接口拆分

NMS 操作按职责拆分为 4 个子接口，每个子接口可以独立实现和测试：

```kotlin
// ── 属性桥接 ──
interface AttributeBridge {
    /** 设置实体的原版属性修改器 */
    fun setModifier(
        entity: LivingEntity,
        attribute: String,           // 如 "generic.max_health"
        key: String,                 // Symphony 修改器标识，如 "symphony:max_health"
        amount: Double,
        operation: Int               // 0=ADD, 1=MULTIPLY_BASE, 2=MULTIPLY_TOTAL
    )

    /** 移除指定 key 的修改器 */
    fun removeModifier(entity: LivingEntity, attribute: String, key: String)

    /** 移除 Symphony 设置的所有修改器 */
    fun removeAllSymphonyModifiers(entity: LivingEntity)

    /** 获取实体某个原版属性的基础值 */
    fun getBaseValue(entity: LivingEntity, attribute: String): Double

    /** 获取实体某个原版属性的最终值（含所有修改器） */
    fun getFinalValue(entity: LivingEntity, attribute: String): Double

    /** 检查实体是否拥有指定原版属性 */
    fun hasAttribute(entity: LivingEntity, attribute: String): Boolean

    /** 获取所有可用的原版属性 ID 列表 */
    fun getAvailableAttributes(): List<String>
}

// ── 物品数据操作 ──
interface ItemDataAccessor {
    /** 读取物品自定义数据（JSON 字符串） */
    fun getCustomData(item: ItemStack, key: String): String?

    /** 写入物品自定义数据 */
    fun setCustomData(item: ItemStack, key: String, value: String): ItemStack

    /** 移除物品自定义数据 */
    fun removeCustomData(item: ItemStack, key: String): ItemStack

    /** 检查物品是否有指定自定义数据 */
    fun hasCustomData(item: ItemStack, key: String): Boolean

    /** 获取物品的原版 AttributeModifier 列表 */
    fun getItemAttributeModifiers(item: ItemStack): List<ItemAttributeModifier>

    /** 设置物品的原版 AttributeModifier */
    fun setItemAttributeModifiers(item: ItemStack, modifiers: List<ItemAttributeModifier>): ItemStack

    /** 设置物品 Lore */
    fun setLore(item: ItemStack, lore: List<String>): ItemStack

    /** 设置物品显示名 */
    fun setDisplayName(item: ItemStack, name: String): ItemStack

    /** 设置物品 CustomModelData */
    fun setCustomModelData(item: ItemStack, data: Int): ItemStack

    /** 获取物品的完整序列化数据（用于持久化） */
    fun serializeItem(item: ItemStack): ByteArray

    /** 从序列化数据恢复物品 */
    fun deserializeItem(data: ByteArray): ItemStack
}

data class ItemAttributeModifier(
    val attribute: String,           // 原版属性 ID
    val key: String,                 // 修改器标识
    val amount: Double,
    val operation: Int,              // 0=ADD, 1=MULTIPLY_BASE, 2=MULTIPLY_TOTAL
    val slot: String                 // 装备槽位：mainhand/offhand/head/chest/legs/feet/any
)

// ── 实体操作 ──
interface EntityAccessor {
    /** 冻结实体（设置 TicksFrozen） */
    fun freezeEntity(entity: LivingEntity, ticks: Int)

    /** 击退实体 */
    fun knockback(entity: LivingEntity, strength: Double, dirX: Double, dirZ: Double)

    /** 设置实体无敌帧 */
    fun setNoDamageTicks(entity: LivingEntity, ticks: Int)

    /** 获取实体的真实最大生命值（绕过 Bukkit 缓存） */
    fun getTrueMaxHealth(entity: LivingEntity): Double

    /** 判断实体是否为亡灵类 */
    fun isUndead(entity: LivingEntity): Boolean

    /** 判断实体是否为节肢类 */
    fun isArthropod(entity: LivingEntity): Boolean

    /** 获取实体所在生物群系 ID */
    fun getBiomeKey(entity: LivingEntity): String

    /** 判断实体是否在露天（头顶无遮挡） */
    fun isOutdoor(entity: LivingEntity): Boolean
}

// ── 显示适配 ──
interface DisplayAdapter {
    /** 发送 ActionBar 消息 */
    fun sendActionBar(player: Player, message: String)

    /** 发送 Title */
    fun sendTitle(player: Player, title: String, subtitle: String, fadeIn: Int, stay: Int, fadeOut: Int)

    /** 发送 BossBar（返回 ID 用于后续更新/移除） */
    fun showBossBar(player: Player, text: String, progress: Float, color: String): String

    /** 更新 BossBar */
    fun updateBossBar(id: String, text: String?, progress: Float?)

    /** 移除 BossBar */
    fun removeBossBar(id: String)
}
```

### 3.3 工厂与版本检测

```kotlin
object NMSAdapterFactory {
    private lateinit var adapter: NMSAdapter

    fun initialize() {
        val version = detectNMSVersion()
        adapter = when {
            version.startsWith("v1_18") -> NMS_v1_18_R2()
            version.startsWith("v1_19") -> NMS_v1_19_R3()
            version.startsWith("v1_20_R1") || version.startsWith("v1_20_R2") || version.startsWith("v1_20_R3") -> NMS_v1_20_R3()
            version.startsWith("v1_20_R4") || version.startsWith("v1_20_R5") -> NMS_v1_20_R4()  // Data Components 断代
            version.startsWith("v1_21_R1") -> NMS_v1_21_R1()
            version.startsWith("v1_21_R2") || version.startsWith("v1_21_R3") -> NMS_v1_21_R3()
            else -> {
                logger.warning("未知 NMS 版本: $version，尝试使用最新适配器")
                NMS_v1_21_R3()  // fallback 到最新
            }
        }
        logger.info("NMS 适配器已加载: ${adapter.version}")
    }

    fun get(): NMSAdapter = adapter

    private fun detectNMSVersion(): String {
        val packageName = Bukkit.getServer().javaClass.packageName
        // org.bukkit.craftbukkit.v1_21_R1 → v1_21_R1
        return packageName.split(".").find { it.startsWith("v1_") }
            ?: detectByBukkitVersion()  // 1.20.5+ 可能没有版本包名
    }

    private fun detectByBukkitVersion(): String {
        val bukkitVersion = Bukkit.getBukkitVersion()  // 如 "1.21.2-R0.1-SNAPSHOT"
        val parts = bukkitVersion.split("-")[0].split(".")
        val major = parts[0].toInt()
        val minor = parts[1].toInt()
        val patch = parts.getOrNull(2)?.toIntOrNull() ?: 0
        return when {
            major == 1 && minor == 18 -> "v1_18_R2"
            major == 1 && minor == 19 -> "v1_19_R3"
            major == 1 && minor == 20 && patch < 5 -> "v1_20_R3"
            major == 1 && minor == 20 -> "v1_20_R4"
            major == 1 && minor == 21 && patch < 2 -> "v1_21_R1"
            else -> "v1_21_R3"
        }
    }
}
```

## 4. AttributeBridge 版本差异与实现

这是 NMS 层最核心的部分，直接影响 Symphony 属性系统的原版同步。

### 4.1 版本差异对照

| 操作 | 1.18.2 ~ 1.20.4 | 1.21+ |
|------|-----------------|-------|
| 创建修改器 | `new AttributeModifier(UUID, name, amount, op)` | `new AttributeModifier(ResourceLocation, amount, op)` |
| 修改器标识 | UUID（随机生成或固定） | ResourceLocation（如 `symphony:max_health`） |
| 查找修改器 | 遍历 `getModifiers()` 匹配 UUID | 通过 `getModifier(ResourceLocation)` 直接查找 |
| 移除修改器 | `removeModifier(AttributeModifier)` | `removeModifier(ResourceLocation)` |
| 属性注册表 | `Registry.ATTRIBUTE` | `BuiltInRegistries.ATTRIBUTE` / `Holder<Attribute>` |
| 属性引用 | 直接 `Attribute` 对象 | `Holder<Attribute>` 包装 |

### 4.2 1.18 ~ 1.20 实现要点

```kotlin
class AttributeBridge_Legacy : AttributeBridge {
    // Symphony 使用固定 UUID 命名空间，通过 key 生成确定性 UUID
    // 这样同一个 key 总是对应同一个 UUID，可以精确查找和移除
    private fun keyToUUID(key: String): UUID {
        return UUID.nameUUIDFromBytes(key.toByteArray(Charsets.UTF_8))
    }

    override fun setModifier(entity: LivingEntity, attribute: String, key: String, amount: Double, operation: Int) {
        val nmsEntity = (entity as CraftLivingEntity).handle
        val nmsAttribute = getVanillaAttribute(attribute) ?: return
        val instance = nmsEntity.getAttribute(nmsAttribute) ?: return

        val uuid = keyToUUID(key)
        // 先移除旧的
        instance.getModifier(uuid)?.let { instance.removeModifier(it) }
        // 添加新的
        val op = when (operation) {
            0 -> AttributeModifier.Operation.ADDITION
            1 -> AttributeModifier.Operation.MULTIPLY_BASE
            else -> AttributeModifier.Operation.MULTIPLY_TOTAL
        }
        instance.addTransientModifier(AttributeModifier(uuid, key, amount, op))
    }

    override fun removeModifier(entity: LivingEntity, attribute: String, key: String) {
        val nmsEntity = (entity as CraftLivingEntity).handle
        val nmsAttribute = getVanillaAttribute(attribute) ?: return
        val instance = nmsEntity.getAttribute(nmsAttribute) ?: return
        val uuid = keyToUUID(key)
        instance.getModifier(uuid)?.let { instance.removeModifier(it) }
    }

    private fun getVanillaAttribute(id: String): Attribute? {
        val resourceLocation = ResourceLocation(id.replace("generic.", "minecraft:generic."))
        return Registry.ATTRIBUTE.get(resourceLocation)
    }
}
```

### 4.3 1.21+ 实现要点

```kotlin
class AttributeBridge_Modern : AttributeBridge {
    override fun setModifier(entity: LivingEntity, attribute: String, key: String, amount: Double, operation: Int) {
        val nmsEntity = (entity as CraftLivingEntity).handle
        val holder = getAttributeHolder(attribute) ?: return
        val instance = nmsEntity.getAttribute(holder) ?: return

        val location = ResourceLocation.parse(key)  // "symphony:max_health"
        // 1.21+ 可以直接通过 ResourceLocation 移除
        instance.removeModifier(location)
        // 添加新的
        val op = when (operation) {
            0 -> AttributeModifier.Operation.ADD_VALUE
            1 -> AttributeModifier.Operation.ADD_MULTIPLIED_BASE
            else -> AttributeModifier.Operation.ADD_MULTIPLIED_TOTAL
        }
        instance.addTransientModifier(AttributeModifier(location, amount, op))
    }

    override fun removeModifier(entity: LivingEntity, attribute: String, key: String) {
        val nmsEntity = (entity as CraftLivingEntity).handle
        val holder = getAttributeHolder(attribute) ?: return
        val instance = nmsEntity.getAttribute(holder) ?: return
        instance.removeModifier(ResourceLocation.parse(key))
    }

    private fun getAttributeHolder(id: String): Holder<Attribute>? {
        val location = ResourceLocation.parse(id)
        return BuiltInRegistries.ATTRIBUTE.get(location)
            ?.let { BuiltInRegistries.ATTRIBUTE.wrapAsHolder(it) }
    }
}
```

### 4.4 Operation 枚举映射

| Symphony | 1.18~1.20 NMS | 1.21+ NMS | 效果 |
|----------|---------------|-----------|------|
| 0 (FLAT) | `ADDITION` | `ADD_VALUE` | base + value |
| 1 (PERCENT_BASE) | `MULTIPLY_BASE` | `ADD_MULTIPLIED_BASE` | base × (1 + value) |
| 2 (PERCENT_TOTAL) | `MULTIPLY_TOTAL` | `ADD_MULTIPLIED_TOTAL` | total × (1 + value) |

## 5. ItemDataAccessor 版本差异与实现

物品数据操作是第二大断代点。1.20.5 的 Data Components 系统彻底改变了物品数据的存储方式。

### 5.1 版本差异对照

| 操作 | 1.18.2 ~ 1.20.4 (NBT 时代) | 1.20.5+ (Data Components 时代) |
|------|---------------------------|-------------------------------|
| 自定义数据存储 | `ItemStack.getTag().putString(key, value)` | `ItemStack.set(DataComponents.CUSTOM_DATA, CustomData)` |
| 自定义数据读取 | `ItemStack.getTag().getString(key)` | `ItemStack.get(DataComponents.CUSTOM_DATA)?.copyTag()` |
| 属性修改器 | NBT `AttributeModifiers` 列表 | `DataComponents.ATTRIBUTE_MODIFIERS` 组件 |
| Lore | NBT `display.Lore` | `DataComponents.LORE` 组件 |
| 显示名 | NBT `display.Name` | `DataComponents.CUSTOM_NAME` 组件 |
| CustomModelData | NBT `CustomModelData` | `DataComponents.CUSTOM_MODEL_DATA` 组件 |
| PDC | `ItemMeta.PersistentDataContainer` | 仍然可用（Bukkit 层封装） |

### 5.2 Symphony 的存储策略

Symphony 的物品数据（属性、词条、宝石槽等）优先使用 Bukkit PDC API，因为 PDC 在所有版本上都有一致的接口。只有在 PDC 无法满足需求时才降级到 NMS 层。

```
优先级：
1. Bukkit PDC API（跨版本一致，推荐）
2. Bukkit ItemMeta API（Lore、DisplayName、CustomModelData）
3. NMS ItemDataAccessor（仅在需要直接操作原版组件时使用）
```

### 5.3 PDC 兼容层

```kotlin
/**
 * PDC 操作封装 — 跨版本一致，不需要 NMS。
 * Symphony 的物品数据（属性、词条、宝石等）全部通过此层读写。
 */
object SymphonyPDC {
    private val NAMESPACE = "symphony"

    fun getString(item: ItemStack, key: String): String? {
        val meta = item.itemMeta ?: return null
        val namespacedKey = NamespacedKey(NAMESPACE, key)
        return meta.persistentDataContainer.get(namespacedKey, PersistentDataType.STRING)
    }

    fun setString(item: ItemStack, key: String, value: String): ItemStack {
        val meta = item.itemMeta ?: return item
        val namespacedKey = NamespacedKey(NAMESPACE, key)
        meta.persistentDataContainer.set(namespacedKey, PersistentDataType.STRING, value)
        item.itemMeta = meta
        return item
    }

    fun getInt(item: ItemStack, key: String): Int? {
        val meta = item.itemMeta ?: return null
        val namespacedKey = NamespacedKey(NAMESPACE, key)
        return meta.persistentDataContainer.get(namespacedKey, PersistentDataType.INTEGER)
    }

    fun setInt(item: ItemStack, key: String, value: Int): ItemStack {
        val meta = item.itemMeta ?: return item
        val namespacedKey = NamespacedKey(NAMESPACE, key)
        meta.persistentDataContainer.set(namespacedKey, PersistentDataType.INTEGER, value)
        item.itemMeta = meta
        return item
    }

    fun remove(item: ItemStack, key: String): ItemStack {
        val meta = item.itemMeta ?: return item
        val namespacedKey = NamespacedKey(NAMESPACE, key)
        meta.persistentDataContainer.remove(namespacedKey)
        item.itemMeta = meta
        return item
    }

    fun has(item: ItemStack, key: String): Boolean {
        val meta = item.itemMeta ?: return false
        val namespacedKey = NamespacedKey(NAMESPACE, key)
        return meta.persistentDataContainer.has(namespacedKey)
    }
}
```

### 5.4 NMS 层物品操作（仅在必要时使用）

需要 NMS 的场景：
- 设置物品的原版 AttributeModifier（影响客户端 Tooltip 显示）
- 物品序列化/反序列化（跨版本数据迁移）
- 读取其他插件通过 NMS 写入的数据

```kotlin
// 1.18~1.20：NBT 方式设置物品属性修改器
class ItemDataAccessor_Legacy : ItemDataAccessor {
    override fun setItemAttributeModifiers(item: ItemStack, modifiers: List<ItemAttributeModifier>): ItemStack {
        val nmsItem = CraftItemStack.asNMSCopy(item)
        val tag = nmsItem.getOrCreateTag()
        val list = ListTag()
        for (mod in modifiers) {
            val compound = CompoundTag()
            compound.putString("AttributeName", mod.attribute)
            compound.putString("Name", mod.key)
            compound.putDouble("Amount", mod.amount)
            compound.putInt("Operation", mod.operation)
            compound.putIntArray("UUID", uuidToIntArray(keyToUUID(mod.key)))
            compound.putString("Slot", mod.slot)
            list.add(compound)
        }
        tag.put("AttributeModifiers", list)
        return CraftItemStack.asBukkitCopy(nmsItem)
    }
}

// 1.20.5+：Data Components 方式
class ItemDataAccessor_Modern : ItemDataAccessor {
    override fun setItemAttributeModifiers(item: ItemStack, modifiers: List<ItemAttributeModifier>): ItemStack {
        val nmsItem = CraftItemStack.asNMSCopy(item)
        val entries = modifiers.map { mod ->
            val holder = getAttributeHolder(mod.attribute)
            val modifier = AttributeModifier(
                ResourceLocation.parse(mod.key),
                mod.amount,
                mapOperation(mod.operation)
            )
            val slot = mapEquipmentSlotGroup(mod.slot)
            ItemAttributeModifiers.Entry(holder, modifier, slot)
        }
        nmsItem.set(DataComponents.ATTRIBUTE_MODIFIERS, ItemAttributeModifiers(entries, true))
        return CraftItemStack.asBukkitCopy(nmsItem)
    }
}
```

### 5.5 物品数据迁移

当服务器从低版本升级到高版本时，物品上的 NBT 数据需要迁移到 Data Components。Symphony 提供自动迁移机制：

```kotlin
class ItemDataMigrator {
    fun migrateIfNeeded(item: ItemStack): ItemStack {
        // 检查是否有旧版 NBT 格式的 Symphony 数据
        val accessor = NMSAdapterFactory.get().getItemDataAccessor()
        
        // 旧版数据在 NBT 的 "symphony:*" 键下
        // 新版数据在 PDC 的 "symphony:*" 键下
        // 如果检测到旧版数据且 PDC 中没有对应数据 → 执行迁移
        
        if (hasLegacyData(item) && !hasModernData(item)) {
            return migrateLegacyToModern(item)
        }
        return item
    }
}
```

## 6. EntityAccessor 与 DisplayAdapter

这两个子接口的版本差异相对较小，主要是 API 签名变化。

### 6.1 EntityAccessor 版本差异

| 操作 | 1.18~1.20 | 1.21+ |
|------|-----------|-------|
| 冻结实体 | `entity.setTicksFrozen(ticks)` | 同左 |
| 生物群系获取 | `entity.level.getBiome(pos).unwrapKey()` | `entity.level().getBiome(pos).unwrapKey()` |
| 亡灵判定 | `entity instanceof Monster` + 类型检查 | `entity.getType().is(EntityTypeTags.UNDEAD)` |
| 节肢判定 | 硬编码类型列表 | `entity.getType().is(EntityTypeTags.ARTHROPOD)` |
| 露天判定 | `level.canSeeSky(pos)` | 同左 |

### 6.2 DisplayAdapter 版本差异

| 操作 | 1.18~1.19 | 1.20+ |
|------|-----------|-------|
| ActionBar | `player.spigot().sendMessage(ChatMessageType.ACTION_BAR, ...)` | Bukkit `player.sendActionBar(Component)` |
| Title | NMS `ClientboundSetTitleTextPacket` | Bukkit `player.showTitle(Title)` |
| BossBar | Bukkit `BossBar` API | 同左（跨版本一致） |

1.20+ 的 Bukkit API 已经足够完善，大部分显示操作不再需要 NMS。Symphony 的 DisplayAdapter 在高版本上可以直接委托给 Bukkit API：

```kotlin
class DisplayAdapter_Modern : DisplayAdapter {
    override fun sendActionBar(player: Player, message: String) {
        player.sendActionBar(Component.text(ChatColor.translateAlternateColorCodes('&', message)))
    }

    override fun sendTitle(player: Player, title: String, subtitle: String, fadeIn: Int, stay: Int, fadeOut: Int) {
        player.showTitle(Title.title(
            Component.text(ChatColor.translateAlternateColorCodes('&', title)),
            Component.text(ChatColor.translateAlternateColorCodes('&', subtitle)),
            Title.Times.times(
                Duration.ofMillis(fadeIn * 50L),
                Duration.ofMillis(stay * 50L),
                Duration.ofMillis(fadeOut * 50L)
            )
        ))
    }
}
```

## 7. 构建配置

### 7.1 多模块 Gradle 结构

```
symphony/
├── symphony-common/              # 纯 API，无 NMS 依赖
├── symphony-core/                # 业务逻辑，依赖 common
├── symphony-nms/                 # NMS 适配层
│   ├── nms-api/                  # 适配器接口定义（NMSAdapter + 子接口）
│   ├── nms-v1_18/                # 1.18.2 实现
│   ├── nms-v1_19/                # 1.19.x 实现
│   ├── nms-v1_20_legacy/         # 1.20.1~1.20.4 实现（NBT 时代最后一版）
│   ├── nms-v1_20_modern/         # 1.20.5+ 实现（Data Components 断代）
│   ├── nms-v1_21/                # 1.21~1.21.1 实现（ResourceLocation 修改器）
│   └── nms-v1_21_3/              # 1.21.2+ 实现（Holder 系统）
└── symphony-plugin/              # Blink 插件入口，Shadow 打包
```

### 7.2 依赖关系

```kotlin
// symphony-nms/nms-api/build.gradle.kts
dependencies {
    compileOnly("org.spigotmc:spigot-api:1.18.2-R0.1-SNAPSHOT")
}

// symphony-nms/nms-v1_18/build.gradle.kts
dependencies {
    implementation(project(":symphony-nms:nms-api"))
    compileOnly("org.spigotmc:spigot:1.18.2-R0.1-SNAPSHOT")  // 需要 NMS 类
}

// symphony-nms/nms-v1_21/build.gradle.kts
dependencies {
    implementation(project(":symphony-nms:nms-api"))
    compileOnly("org.spigotmc:spigot:1.21-R0.1-SNAPSHOT")
}

// symphony-core/build.gradle.kts
dependencies {
    implementation(project(":symphony-common"))
    implementation(project(":symphony-nms:nms-api"))
    // 不直接依赖具体 NMS 实现，运行时通过 Factory 加载
}

// symphony-plugin/build.gradle.kts (Shadow 打包)
dependencies {
    implementation(project(":symphony-common"))
    implementation(project(":symphony-core"))
    implementation(project(":symphony-nms:nms-api"))
    // 所有 NMS 实现模块都打包进最终 JAR
    implementation(project(":symphony-nms:nms-v1_18"))
    implementation(project(":symphony-nms:nms-v1_19"))
    implementation(project(":symphony-nms:nms-v1_20_legacy"))
    implementation(project(":symphony-nms:nms-v1_20_modern"))
    implementation(project(":symphony-nms:nms-v1_21"))
    implementation(project(":symphony-nms:nms-v1_21_3"))
}
```

### 7.3 类加载隔离

NMS 实现类在运行时按需加载，避免在不支持的版本上触发 `ClassNotFoundException`：

```kotlin
// NMSAdapterFactory 中使用反射加载
private fun loadAdapter(className: String): NMSAdapter {
    return Class.forName(className)
        .getDeclaredConstructor()
        .newInstance() as NMSAdapter
}

// 映射表
private val adapterMap = mapOf(
    "v1_18" to "symphony.nms.v1_18.NMS_v1_18_R2",
    "v1_19" to "symphony.nms.v1_19.NMS_v1_19_R3",
    "v1_20_legacy" to "symphony.nms.v1_20_legacy.NMS_v1_20_R3",
    "v1_20_modern" to "symphony.nms.v1_20_modern.NMS_v1_20_R4",
    "v1_21" to "symphony.nms.v1_21.NMS_v1_21_R1",
    "v1_21_3" to "symphony.nms.v1_21_3.NMS_v1_21_R3"
)
```

这样即使 JAR 中包含所有版本的实现类，也只有当前版本对应的类会被实际加载。

## 8. 实现优先级与开发指南

### 8.1 实现顺序

NMS 层应按以下顺序实现，每完成一步都可以独立测试：

```
第一步：nms-api（接口定义）
    ↓ 定义所有接口和数据类，不涉及任何 NMS 代码
    ↓ 可以编写单元测试的 Mock 实现

第二步：nms-v1_21_3（最新版本实现）
    ↓ 先让最新版本跑通，作为参考实现
    ↓ 验证接口设计是否合理

第三步：nms-v1_21（1.21~1.21.1）
    ↓ 与 v1_21_3 差异最小，主要是 Holder 相关调整

第四步：nms-v1_20_modern（1.20.5+）
    ↓ Data Components 断代，需要重写物品数据操作

第五步：nms-v1_20_legacy（1.20.1~1.20.4）
    ↓ NBT 时代最后一版，与 1.18/1.19 类似

第六步：nms-v1_19 + nms-v1_18
    ↓ 最老的版本，差异主要在 API 签名
```

### 8.2 测试策略

```
单元测试（不需要服务器）：
├── Mock NMSAdapter 实现
├── 测试 NMSAdapterFactory 版本检测逻辑
├── 测试 SymphonyPDC 读写
└── 测试 ItemDataMigrator 迁移逻辑

集成测试（需要对应版本的测试服务器）：
├── 每个 NMS 版本一个测试环境
├── 测试 AttributeBridge：设置/移除/查询修改器
├── 测试 ItemDataAccessor：物品数据读写
├── 测试 EntityAccessor：实体操作
└── 测试跨版本数据迁移
```

### 8.3 新版本适配流程

当 Minecraft 发布新版本时：

1. 检查 Spigot/Paper 的 NMS 包名是否变化
2. 检查 `AttributeModifier` 构造器是否变化
3. 检查 `DataComponents` 是否有新增/移除
4. 检查 `Attribute` 注册表是否变化
5. 如果变化较小 → 在最新的 NMS 模块中兼容
6. 如果有断代级变化 → 新建 NMS 模块

### 8.4 原版属性 ID 参考

Symphony 的 `vanilla_binding` 使用的原版属性 ID：

| 原版属性 ID | 说明 | 可用版本 |
|-------------|------|----------|
| `generic.max_health` | 最大生命值 | 全版本 |
| `generic.movement_speed` | 移动速度 | 全版本 |
| `generic.attack_damage` | 攻击伤害 | 全版本 |
| `generic.attack_speed` | 攻击速度 | 全版本 |
| `generic.armor` | 护甲值 | 全版本 |
| `generic.armor_toughness` | 护甲韧性 | 全版本 |
| `generic.knockback_resistance` | 击退抗性 | 全版本 |
| `generic.luck` | 幸运 | 全版本 |
| `generic.max_absorption` | 最大吸收 | 1.20.2+ |
| `generic.block_interaction_range` | 方块交互距离 | 1.20.5+ |
| `generic.entity_interaction_range` | 实体交互距离 | 1.20.5+ |
| `generic.block_break_speed` | 方块破坏速度 | 1.20.5+ |
| `generic.fall_damage_multiplier` | 摔落伤害倍率 | 1.20.5+ |
| `generic.gravity` | 重力 | 1.20.5+ |
| `generic.jump_strength` | 跳跃力度 | 1.20.5+ |
| `generic.safe_fall_distance` | 安全坠落距离 | 1.20.5+ |
| `generic.scale` | 实体缩放 | 1.20.5+ |
| `generic.step_height` | 台阶高度 | 1.20.5+ |
| `generic.burning_time` | 燃烧时间 | 1.21+ |
| `generic.explosion_knockback_resistance` | 爆炸击退抗性 | 1.21+ |
| `generic.mining_efficiency` | 挖掘效率 | 1.21+ |
| `generic.movement_efficiency` | 移动效率 | 1.21+ |
| `generic.oxygen_bonus` | 氧气加成 | 1.21+ |
| `generic.sneaking_speed` | 潜行速度 | 1.21+ |
| `generic.submerged_mining_speed` | 水下挖掘速度 | 1.21+ |
| `generic.sweeping_damage_ratio` | 横扫伤害比例 | 1.21+ |
| `generic.water_movement_efficiency` | 水中移动效率 | 1.21+ |

属性脚本中的 `vanilla_binding` 应当检查当前版本是否支持该属性，不支持时静默跳过。
