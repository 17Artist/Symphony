# API 设计

## 1. API 入口

Symphony 通过 Bukkit ServicesManager 暴露 API：

```kotlin
// 获取 API
val api = Bukkit.getServicesManager()
    .getRegistration(SymphonyAPI::class.java)?.provider
    ?: error("Symphony not loaded")

// 或通过静态方法
val api = SymphonyAPI.getInstance()
```

### 主接口

```kotlin
interface SymphonyAPI {
    fun getAttributeManager(): IAttributeManager
    fun getAffixManager(): IAffixManager
    fun getTriggerManager(): ITriggerManager
    fun getSkillProviderManager(): ISkillProviderManager
    fun getGrowthManager(): IGrowthManager
    fun getFormulaEngine(): IFormulaEngine
    fun getPlayerData(player: Player): IPlayerData
    
    // 便捷方法
    fun getAttribute(entity: LivingEntity, attributeId: String): Double
    fun setAttribute(
        entity: LivingEntity, 
        attributeId: String, 
        operation: Operation, 
        value: Double, 
        source: String
    )
    fun removeAttribute(entity: LivingEntity, source: String)
    fun recalculate(entity: LivingEntity)
}
```

## 2. 属性管理器 API

```kotlin
interface IAttributeManager {
    // 属性注册
    fun registerAttribute(attribute: IAttribute)
    fun unregisterAttribute(id: String)
    fun getAttribute(id: String): IAttribute?
    fun getAllAttributes(): Collection<IAttribute>
    fun getAttributesByCategory(category: AttributeType): Collection<IAttribute>
    
    // 属性来源
    fun registerProvider(provider: IAttributeProvider)
    fun unregisterProvider(id: String)
    fun getProviders(): List<IAttributeProvider>
    
    // 属性读取
    fun getValue(entity: LivingEntity, attributeId: String): Double
    fun getValues(entity: LivingEntity): Map<String, Double>
    fun getModifiers(entity: LivingEntity, attributeId: String): List<AttributeModifier>
    
    // 属性修改
    fun addModifier(entity: LivingEntity, modifier: AttributeModifier)
    fun removeModifier(entity: LivingEntity, source: String)
    fun addBuff(entity: LivingEntity, buff: BuffData)
    fun removeBuff(entity: LivingEntity, buffId: String)
    
    // 重算
    fun markDirty(entity: LivingEntity)
    fun recalculate(entity: LivingEntity)
}
```

## 3. 词条管理器 API

```kotlin
interface IAffixManager {
    // 词条定义
    fun registerAffix(affix: IAffix)
    fun getAffix(id: String): IAffix?
    fun getAllAffixes(): Collection<IAffix>
    fun getAffixesByRarity(rarity: AffixRarity): Collection<IAffix>
    
    // 词条池
    fun registerPool(pool: AffixPool)
    fun getPool(id: String): AffixPool?
    
    // 物品词条操作
    fun getAffixes(item: ItemStack): List<AffixInstance>
    fun addAffix(item: ItemStack, instance: AffixInstance): Boolean
    fun removeAffix(item: ItemStack, uuid: UUID): Boolean
    fun clearAffixes(item: ItemStack)
    
    // 随机生成
    fun generateAffixes(poolId: String, rarity: AffixRarity, luck: Double): List<AffixInstance>
    fun applyAffixes(item: ItemStack, affixes: List<AffixInstance>)
    
    // Lore 渲染
    fun renderLore(item: ItemStack)
}
```

## 4. 触发器管理器 API

```kotlin
interface ITriggerManager {
    // 触发器类型注册
    fun registerTrigger(type: TriggerType, displayName: String, contextKeys: List<String>)
    fun getTriggerType(id: String): TriggerType?
    fun getAllTriggerTypes(): Collection<TriggerType>
    
    // 条件注册
    fun registerCondition(type: String, factory: (Map<String, Any>) -> ITriggerCondition)
    
    // 手动触发
    fun dispatch(type: TriggerType, entity: LivingEntity, context: Map<String, Any>)
    
    // 冷却管理
    fun isOnCooldown(entity: LivingEntity, key: String): Boolean
    fun getCooldownRemaining(entity: LivingEntity, key: String): Long
    fun setCooldown(entity: LivingEntity, key: String, duration: Long)
    fun clearCooldown(entity: LivingEntity, key: String)
}
```

## 5. 技能提供者管理器 API

```kotlin
interface ISkillProviderManager {
    fun registerProvider(provider: ISkillProvider)
    fun unregisterProvider(id: String)
    fun getProvider(id: String): ISkillProvider?
    fun getAllProviders(): Collection<ISkillProvider>
    
    fun castSkill(providerId: String, skillId: String, level: Int, context: SkillContext): Boolean
    fun hasSkill(providerId: String, skillId: String): Boolean
    fun getSkillInfo(providerId: String, skillId: String, level: Int): SkillInfo?
}
```

## 6. 成长系统 API

```kotlin
interface IGrowthManager {
    // 等级
    fun getLevel(player: Player): Int
    fun setLevel(player: Player, level: Int)
    fun addExp(player: Player, amount: Long, source: String)
    fun getExp(player: Player): Long
    fun getRequiredExp(level: Int): Long
    
    // 宝石
    fun getGemSlots(item: ItemStack): List<GemSlot>
    fun insertGem(item: ItemStack, slotIndex: Int, gemId: String, gemLevel: Int): Boolean
    fun removeGem(item: ItemStack, slotIndex: Int): Boolean
    fun unlockSlot(item: ItemStack, slotIndex: Int): Boolean
    fun synthesizeGems(gems: List<ItemStack>): ItemStack?
    
    // 符文
    fun getRunes(player: Player): Map<String, RuneData>
    fun activateRune(player: Player, runeId: String, level: Int): Boolean
    fun deactivateRune(player: Player, runeId: String)
    fun addFragments(player: Player, runeId: String, amount: Int)
    fun getFragments(player: Player, runeId: String): Int
    
    // 强化
    fun getEnhanceLevel(item: ItemStack): Int
    fun enhance(player: Player, item: ItemStack, protections: List<ItemStack>): EnhanceResult
    fun setEnhanceLevel(item: ItemStack, level: Int)
    
    // 套装
    fun getSetId(item: ItemStack): String?
    fun getActiveSets(player: Player): Map<String, Int>
    fun getSetBonuses(setId: String, pieceCount: Int): SetBonus?
}

enum class EnhanceResult {
    SUCCESS, FAILURE, DESTROYED, MAX_LEVEL, INVALID_MATERIAL
}
```

## 7. 事件系统

所有事件继承 Bukkit Event，支持 Cancellable：

```kotlin
// 属性变更事件
class AttributeUpdateEvent(
    val entity: LivingEntity,
    val attributeId: String,
    val oldValue: Double,
    val newValue: Double,
    val source: String
) : Event(), Cancellable

// 词条触发事件
class AffixTriggerEvent(
    val entity: LivingEntity,
    val affix: AffixInstance,
    val triggerType: TriggerType,
    val context: ITriggerContext
) : Event(), Cancellable

// Symphony 伤害计算事件（在原版伤害事件之后）
class SymphonyDamageEvent(
    val attacker: LivingEntity,
    val victim: LivingEntity,
    val rawDamage: Double,
    var finalDamage: Double,
    val damageType: DamageType,
    val isCritical: Boolean,
    val elementDamages: MutableMap<String, Double>
) : Event(), Cancellable

// 强化事件
class EnhanceEvent(
    val player: Player,
    val item: ItemStack,
    val oldLevel: Int,
    val newLevel: Int,
    val result: EnhanceResult
) : Event(), Cancellable

// 技能释放事件
class SkillCastEvent(
    val caster: LivingEntity,
    val providerId: String,
    val skillId: String,
    val level: Int,
    val context: SkillContext
) : Event(), Cancellable

// 等级变更事件
class LevelChangeEvent(
    val player: Player,
    val oldLevel: Int,
    val newLevel: Int
) : Event(), Cancellable

// 宝石镶嵌事件
class GemInsertEvent(
    val player: Player,
    val item: ItemStack,
    val slotIndex: Int,
    val gemId: String,
    val gemLevel: Int
) : Event(), Cancellable

// 符文激活事件
class RuneActivateEvent(
    val player: Player,
    val runeId: String,
    val level: Int
) : Event(), Cancellable
```

## 8. PlaceholderAPI 集成

```
%symphony_attribute_<属性ID>%          — 属性值
%symphony_attribute_<属性ID>_base%     — 属性基础值（不含修改器）
%symphony_level%                       — 等级
%symphony_exp%                         — 当前经验
%symphony_exp_required%                — 升级所需经验
%symphony_exp_percent%                 — 经验百分比
%symphony_exp_bar%                     — 经验进度条
%symphony_mana%                        — 当前法力
%symphony_mana_max%                    — 最大法力
%symphony_mana_percent%                — 法力百分比
%symphony_enhance_level%               — 主手装备强化等级
%symphony_enhance_level_<slot>%        — 指定槽位强化等级
%symphony_rune_<符文ID>_level%         — 符文等级
%symphony_rune_<符文ID>_active%        — 符文是否激活
%symphony_rune_<符文ID>_fragments%     — 符文碎片数量
%symphony_set_<套装ID>_pieces%         — 套装已穿戴件数
%symphony_combat_power%                — 战斗力评分
%symphony_stat_damage_dealt%           — 累计造成伤害
%symphony_stat_kills%                  — 累计击杀数
```

## 9. 命令系统

所有命令都可简写 `/sym`，需要 `symphony.admin` 权限。命令分为 `player`（玩家数据）和 `item`（主手物品）两大组。

### 玩家操作

```
/sym player attr get <玩家> <属性ID>                — 查询属性值
/sym player attr set <玩家> <属性ID> <值> [FLAT|PERCENT] — 永久设置属性
/sym player attr list <玩家>                        — 列出所有属性
/sym player buff add <玩家> <属性ID> <值> [FLAT|PERCENT] [秒] — 添加临时 Buff
/sym player buff list <玩家>                        — 列出活跃 Buff
/sym player buff clear <玩家>                       — 清除所有 Buff
/sym player level get <玩家>                        — 查询等级
/sym player level set <玩家> <等级>                  — 设置等级
/sym player rune activate <玩家> <符文ID> [等级]     — 激活符文
/sym player rune fragment <玩家> <符文ID> <数量>     — 给予符文碎片
```

### 物品操作（主手物品）

```
/sym item attr add <属性ID> <值> [FLAT|PERCENT]     — 添加物品属性
/sym item attr remove <属性ID>                      — 移除物品属性
/sym item attr list                                 — 列出物品属性
/sym item attr clear                                — 清空物品属性
/sym item affix add <词条ID> [等级]                  — 添加词条
/sym item affix list                                — 列出词条
/sym item affix clear                               — 清除词条
/sym item affix generate <词条池ID>                  — 从词条池生成
/sym item enhance get                               — 查询强化等级
/sym item enhance set <等级>                         — 设置强化等级
/sym item gem list <装备槽>                          — 列出宝石槽
/sym item gem insert <装备槽> <索引> <宝石ID> [等级]  — 镶嵌宝石
/sym item gem remove <装备槽> <索引>                  — 移除宝石
/sym item gem unlock <装备槽> <索引>                  — 解锁宝石槽
/sym item set list                                  — 列出激活套装
/sym item set mark <装备槽> <套装ID>                  — 标记套装件
/sym item set unmark <装备槽>                        — 取消套装标记
```

### 通用命令

```
/sym reload                                         — 重载配置
/sym explain <属性ID> [玩家]                         — 属性计算流水线明细
/sym debug [玩家]                                    — 运行时状态概览
/sym menu                                           — 打开属性面板
```

slot 参数接受 `MAIN / OFF / HELMET / CHEST / LEGS / BOOTS`（或对应的 `HAND / OFF_HAND / HEAD / CHESTPLATE / LEGGINGS / FEET`），大小写不敏感。
