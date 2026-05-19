# 数据存储设计

## 1. 数据分类总览

Symphony 的数据分为三大类：

| 类型      | 存储位置   | 生命周期   | 说明                         |
|---------|--------|--------|----------------------------|
| 物品数据    | 物品 PDC | 跟随物品   | 属性、词条、宝石、强化、套装 ID          |
| 玩家持久数据  | 文件/数据库 | 永久     | 等级、经验、符文、天赋、统计、配置          |
| 玩家运行时数据 | 内存     | 会话级/临时 | Buff、临时词条、状态层、元素附着、冷却、战斗状态 |

关键设计：运行时数据和持久数据分离。Buff、临时词条等有过期时间的数据在内存中管理，下线时根据策略决定是否持久化（未过期的保存，已过期的丢弃）。

## 2. 物品数据

物品数据存储在 PDC 中，跟随物品本身，不在玩家数据里。

### 2.1 PDC 键定义

```kotlin
object SymphonyKeys {
    val ATTRIBUTES = NamespacedKey(plugin, "attributes")       // JSON: 属性修改器列表
    val AFFIXES = NamespacedKey(plugin, "affixes")             // JSON: 词条实例列表
    val GEM_SLOTS = NamespacedKey(plugin, "gem_slots")         // JSON: 宝石槽数据
    val ENHANCE_LEVEL = NamespacedKey(plugin, "enhance_level") // Integer: 强化等级
    val SET_ID = NamespacedKey(plugin, "set_id")               // String: 套装 ID
    val RARITY = NamespacedKey(plugin, "rarity")               // String: 稀有度
    val ITEM_LEVEL = NamespacedKey(plugin, "item_level")       // Integer: 物品等级
    val ITEM_UUID = NamespacedKey(plugin, "item_uuid")         // String: 物品唯一 ID（追踪用）
}
```

### 2.2 数据格式

属性数据：
```json
{
  "modifiers": [
    { "attr": "physical_damage", "op": "FLAT", "value": 25.0, "source": "base" },
    { "attr": "critical_chance", "op": "FLAT", "value": 0.05, "source": "base" }
  ]
}
```

词条数据：
```json
{
  "affixes": [
    {
      "uuid": "550e8400-e29b-41d4-a716-446655440000",
      "id": "fire_strike",
      "level": 3,
      "params": { "chance": 20, "damage": 50 }
    }
  ]
}
```

宝石槽数据：
```json
{
  "slots": [
    { "index": 0, "gem": "ruby", "level": 2 },
    { "index": 1, "gem": null },
    { "index": 2, "locked": true }
  ]
}
```

## 3. 玩家数据完整结构

玩家数据是 Symphony 最复杂的数据结构，分为持久层和运行时层。

```kotlin
/**
 * 玩家数据根结构。
 * persistent 部分会被序列化到存储后端。
 * runtime 部分只存在于内存中，下线时按策略决定是否持久化。
 */
class SymphonyPlayerData(val uuid: UUID) {

    // ═══════════════════════════════════════
    // 持久数据（存储到文件/数据库）
    // ═══════════════════════════════════════
    val persistent = PersistentData()

    // ═══════════════════════════════════════
    // 运行时数据（仅内存，下线时按策略处理）
    // ═══════════════════════════════════════
    val runtime = RuntimeData()

    var dirty: Boolean = false
}
```

### 3.1 持久数据（PersistentData）

```kotlin
class PersistentData {

    // ── 等级与经验 ──
    var level: Int = 1
    var exp: Long = 0

    // ── 符文系统 ──
    val runes: MutableMap<String, RuneData> = mutableMapOf()
    val runeFragments: MutableMap<String, Int> = mutableMapOf()

    // ── 天赋门 ──
    val unlockedTalents: MutableSet<String> = mutableSetOf()           // 已解锁的天赋门 ID
    val selectedTalents: MutableMap<String, String> = mutableMapOf()   // 槽位ID → 选择的天赋ID

    // ── 虚拟套装 ──
    val virtualSets: MutableMap<String, VirtualSetData> = mutableMapOf()

    // ── 词条共鸣 ──
    val resonanceOverrides: MutableMap<String, Boolean> = mutableMapOf()  // 手动开关的共鸣

    // ── 属性点分配（如果启用自由加点系统） ──
    val allocatedPoints: MutableMap<String, Int> = mutableMapOf()      // 属性ID → 已分配点数
    var freePoints: Int = 0                                            // 未分配的自由点数

    // ── 统计数据 ──
    val statistics: PlayerStatistics = PlayerStatistics()

    // ── 玩家偏好设置 ──
    val preferences: PlayerPreferences = PlayerPreferences()

    // ── 持久化的未过期 Buff ──
    // 下线时未过期的 Buff 保存在这里，上线时恢复到 runtime
    val savedBuffs: MutableList<SavedBuffData> = mutableListOf()

    // ── 持久化的临时词条 ──
    // 同上，下线时未过期的临时词条保存在这里
    val savedTempAffixes: MutableList<SavedTempAffixData> = mutableListOf()
}

data class RuneData(
    val runeId: String,
    var level: Int,
    var active: Boolean
)

data class VirtualSetData(
    val setId: String,
    var pieces: Int,                   // 虚拟件数
    val source: String,                // 来源标识（如 "potion:dragon_set_potion"）
    val expireTime: Long               // 过期时间戳，-1 = 永久
)

data class PlayerStatistics(
    var totalDamageDealt: Double = 0.0,
    var totalDamageTaken: Double = 0.0,
    var totalKills: Int = 0,
    var totalDeaths: Int = 0,
    var highestCombo: Int = 0,
    var totalPlayTime: Long = 0,
    var totalReactionsTriggered: Int = 0,
    var totalAffixesTriggered: Int = 0,
    var highestDamageDealt: Double = 0.0
)

data class PlayerPreferences(
    var showDamageNumbers: Boolean = true,
    var showStatusBars: Boolean = true,
    var showEnvironmentIndicator: Boolean = true,
    var showResonanceProgress: Boolean = true,
    var combatLogEnabled: Boolean = false
)

data class SavedBuffData(
    val id: String,
    val attribute: String,
    val operation: String,             // "FLAT" | "PERCENT"
    val value: Double,
    val expireTime: Long,              // 绝对时间戳
    val source: String,
    val remainingDuration: Long        // 剩余毫秒（下线时计算）
)

data class SavedTempAffixData(
    val uuid: String,
    val affixId: String,
    val level: Int,
    val params: Map<String, Any>,
    val source: String,
    val remainingDuration: Long
)
```

### 3.2 运行时数据（RuntimeData）

```kotlin
class RuntimeData {

    // ── 活跃 Buff ──
    val activeBuffs: MutableList<ActiveBuff> = mutableListOf()

    // ── 临时词条（药水/技能/脚本赋予的，不在物品上） ──
    val tempAffixes: MutableList<TempAffix> = mutableListOf()

    // ── 虚拟套装（运行时激活的，合并持久层的 virtualSets） ──
    val activeVirtualSets: MutableMap<String, VirtualSetRuntime> = mutableMapOf()

    // ── 状态层（流血/冻伤/电荷等叠层数据） ──
    // key = 状态层 ID，value = 该实体身上的叠层数据
    val statusStacks: MutableMap<String, StatusStackData> = mutableMapOf()

    // ── 元素附着 ──
    val elementAuras: MutableMap<String, ElementAuraData> = mutableMapOf()

    // ── 冷却 ──
    val cooldowns: MutableMap<String, Long> = mutableMapOf()           // key → 过期时间戳

    // ── 战斗状态 ──
    var inCombat: Boolean = false
    var combatStartTime: Long = 0
    var lastCombatActionTime: Long = 0
    var comboCount: Int = 0
    var lastComboTime: Long = 0

    // ── 动态触发器（天赋门/交互网络在运行时注册的） ──
    val dynamicTriggers: MutableMap<String, DynamicTriggerData> = mutableMapOf()

    // ── 词条共鸣激活状态（运行时计算结果） ──
    val activeResonances: MutableSet<String> = mutableSetOf()

    // ── 环境修正器缓存（当前生效的环境修正） ──
    val activeEnvironmentModifiers: MutableSet<String> = mutableSetOf()

    // ── 属性交互网络运行时状态 ──
    val activeThresholds: MutableSet<String> = mutableSetOf()          // 已激活的阈值效果
    val activeAmplifiers: MutableSet<String> = mutableSetOf()          // 已激活的条件增幅

    // ── 法力值（运行时资源，不持久化） ──
    var currentMana: Double = 0.0
    var currentHealth: Double = 0.0    // 缓存，避免频繁读取 Bukkit API

    // ── 护盾 ──
    val activeShields: MutableList<ShieldData> = mutableListOf()
}

// ── 运行时数据子结构 ──

data class ActiveBuff(
    val id: String,                    // 唯一标识
    val attribute: String,
    val operation: Operation,
    val value: Double,
    val expireTime: Long,              // 绝对时间戳，-1 = 永久
    val source: String,                // 来源标识
    val stackable: Boolean = false,    // 是否可叠加
    val maxStacks: Int = 1,
    var currentStacks: Int = 1
) {
    fun isExpired(): Boolean = expireTime != -1L && System.currentTimeMillis() > expireTime
    fun remainingMs(): Long = if (expireTime == -1L) Long.MAX_VALUE else expireTime - System.currentTimeMillis()
}

data class TempAffix(
    val uuid: UUID,                    // 实例唯一 ID
    val affixId: String,               // 词条定义 ID
    val level: Int,
    val params: Map<String, Any>,      // 等级参数快照
    val source: String,                // 来源（如 "potion:fire_elixir", "skill:enchant_weapon"）
    val expireTime: Long,              // -1 = 永久（直到手动移除）
    val slot: TempAffixSlot            // 附着位置
) {
    fun isExpired(): Boolean = expireTime != -1L && System.currentTimeMillis() > expireTime
}

enum class TempAffixSlot {
    PLAYER,        // 附着在玩家身上（不依赖装备）
    MAIN_HAND,     // 附着在主手武器上（临时附魔效果）
    ARMOR,         // 附着在全身护甲上
    ANY            // 全局生效
}

data class VirtualSetRuntime(
    val setId: String,
    var totalPieces: Int,              // 虚拟件数（可能来自多个来源叠加）
    val sources: MutableMap<String, VirtualSetSource> = mutableMapOf()
)

data class VirtualSetSource(
    val source: String,
    val pieces: Int,
    val expireTime: Long
)

data class StatusStackData(
    val statusId: String,
    var stacks: Int,
    val stackTimestamps: MutableList<Long>,  // 每层的过期时间戳（INDIVIDUAL 模式）
    var lastRefreshTime: Long,               // 最后刷新时间（REFRESH 模式）
    val appliedBy: UUID?                     // 施加者 UUID（用于计算伤害时引用施加者属性）
)

data class ElementAuraData(
    val element: String,
    var gauge: Double,                 // 当前元素量
    val applyTime: Long,               // 附着时间
    val duration: Long,                // 总持续时间
    val appliedBy: UUID?               // 施加者
) {
    fun isExpired(): Boolean = System.currentTimeMillis() > applyTime + duration
    fun remainingGauge(decayRate: Double): Double {
        val elapsed = (System.currentTimeMillis() - applyTime) / 1000.0
        return maxOf(0.0, gauge - elapsed * decayRate)
    }
}

data class DynamicTriggerData(
    val triggerId: String,
    val triggerType: String,
    val source: String,                // 注册来源（如 "talent:precise_strike"）
    val conditions: List<Map<String, Any>>,
    val actions: List<Map<String, Any>>
)

data class ShieldData(
    val id: String,
    var amount: Double,                // 剩余护盾量
    val maxAmount: Double,
    val expireTime: Long,
    val source: String,
    val element: String?               // 元素护盾（只吸收对应元素伤害）
) {
    fun isExpired(): Boolean = expireTime != -1L && System.currentTimeMillis() > expireTime
}
```

## 4. 运行时数据的使用场景

### 4.1 临时词条（TempAffix）

临时词条不存在于物品上，而是直接附着在玩家身上。来源包括：

| 来源       | 示例                                                                   | 过期策略    |
|----------|----------------------------------------------------------------------|---------|
| 药水       | 喝下「烈焰药剂」→ 获得临时词条「烈焰打击 Lv.2」持续 5 分钟                                   | 定时过期    |
| 技能       | 释放「附魔武器」→ 主手临时获得「雷电附魔」词条 30 秒                                        | 定时过期    |
| Buff 技能  | 队友给你施加「祝福」→ 获得「神圣护佑」词条                                               | 定时过期    |
| 任务/活动    | 进入副本 → 获得「副本增幅」词条                                                    | 离开副本时移除 |
| API / 脚本 | 通过 `PlayerDataManager` 内部 API 操作 | 调用方控制   |

临时词条与物品词条享有完全相同的能力：触发器、被动属性、技能调用。区别只是它不在物品上，而是在玩家的 `runtime.tempAffixes` 列表中。

> 注：临时词条操作当前未暴露到 Aria 脚本层，需通过 Kotlin API（`PlayerDataManager.getOrCreate(uuid).runtime.tempAffixes`）直接操作。

### 4.2 虚拟套装（VirtualSet）

虚拟套装让玩家不需要穿戴实际装备就能获得套装效果。

| 来源       | 示例                                                                      | 说明         |
|----------|-------------------------------------------------------------------------|------------|
| 药水       | 「屠龙者精华」→ 激活屠龙者套装 2 件效果，持续 10 分钟                                         | 定时过期       |
| 符文       | 激活「屠龙者符文」→ 视为穿戴 1 件屠龙者套装                                                | 永久（符文激活期间） |
| 技能       | 「套装幻影」→ 临时获得指定套装的满件效果                                                   | 定时过期       |
| 成就       | 完成「屠龙者」成就 → 永久获得 1 件虚拟套装件数                                              | 永久         |
| API / 脚本 | 通过 `PlayerDataManager` 内部 API 操作 | 调用方控制      |

虚拟件数与实际装备件数叠加计算：

```
实际穿戴的屠龙者装备：2 件
虚拟套装（药水）：+2 件
虚拟套装（符文）：+1 件
─────────────────────
总计：5 件 → 激活 2件/3件/4件 效果，差 1 件激活满套
```

> 注：虚拟套装操作当前未暴露到 Aria 脚本层，需通过 Kotlin API（`PlayerDataManager.getOrCreate(uuid).persistent.virtualSets`）直接操作。

### 4.3 状态层（StatusStack）

状态层数据跟随目标实体，不跟随攻击者。

```
玩家 A 对怪物 B 叠加了 3 层流血
玩家 C 对怪物 B 叠加了 2 层流血
→ 怪物 B 身上有 5 层流血（来自不同攻击者）
→ 每层的伤害按各自施加者的属性计算
```

状态层数据存储在目标实体的运行时数据中（怪物用 `EntityRuntimeData`，玩家用 `RuntimeData`）。

### 4.4 元素附着（ElementAura）

元素附着也跟随目标实体。一个实体最多同时有 2 种元素附着（可配置）。

```
怪物身上：水元素 (gauge=0.8, 剩余 5 秒) + 冰元素 (gauge=0.5, 剩余 3 秒)
→ 此时用火攻击 → 触发蒸发（火+水）→ 消耗水元素 0.5 gauge
→ 水元素剩余 gauge=0.3
```

### 4.5 护盾（Shield）

护盾是一种特殊的运行时数据，在受到伤害时优先消耗。

> 注：护盾操作当前未暴露到 Aria 脚本层。`ShieldData` 类存在于内部实现中，通过 `DamageListener` 在伤害管线中自动消耗。

### 4.6 战斗状态

```
玩家攻击/被攻击 → inCombat = true, lastCombatActionTime = now
    ↓
每 tick 检查：now - lastCombatActionTime > combatTimeout?
    ├── 否 → 保持战斗状态
    └── 是 → inCombat = false → 触发 ON_LEAVE_COMBAT

连击计数：
    攻击 → comboCount++, lastComboTime = now
    now - lastComboTime > comboTimeout → comboCount = 0
```

## 5. 持久化策略

### 5.1 什么数据需要持久化

| 数据     | 持久化？ | 策略                                |
|--------|------|-----------------------------------|
| 等级/经验  | 是    | 始终保存                              |
| 符文/碎片  | 是    | 始终保存                              |
| 天赋门解锁  | 是    | 始终保存                              |
| 属性点分配  | 是    | 始终保存                              |
| 统计数据   | 是    | 始终保存                              |
| 偏好设置   | 是    | 始终保存                              |
| 虚拟套装   | 是    | 保存未过期的，上线时恢复                      |
| Buff   | 条件   | 下线时未过期且 `remainingMs > 30000` 的保存 |
| 临时词条   | 条件   | 下线时未过期且 `remainingMs > 60000` 的保存 |
| 冷却     | 条件   | 下线时未过期的保存                         |
| 状态层    | 否    | 下线清除（战斗状态不跨会话）                    |
| 元素附着   | 否    | 下线清除                              |
| 战斗状态   | 否    | 下线清除                              |
| 连击计数   | 否    | 下线清除                              |
| 动态触发器  | 否    | 上线时由天赋门/交互网络重新注册                  |
| 共鸣激活状态 | 否    | 上线时重新计算                           |
| 护盾     | 否    | 下线清除                              |

### 5.2 下线保存流程

```
PlayerQuitEvent
    ↓
┌─ 清理已过期数据
├─ 将未过期的 Buff 转存到 persistent.savedBuffs
│   └─ 计算 remainingDuration = expireTime - now
│   └─ 过滤掉 remainingDuration < 30000 的（不值得保存）
├─ 将未过期的临时词条转存到 persistent.savedTempAffixes
│   └─ 过滤掉 remainingDuration < 60000 的
├─ 将未过期的虚拟套装同步到 persistent.virtualSets
├─ 将未过期的冷却保存
├─ 清空 runtime 数据
├─ 标记 dirty
└─ 异步保存 persistent 到存储后端
```

### 5.3 上线恢复流程

```
PlayerJoinEvent
    ↓
┌─ 异步加载 persistent 数据
├─ 恢复 savedBuffs → runtime.activeBuffs
│   └─ 重算 expireTime = now + remainingDuration
│   └─ 过滤掉已过期的
├─ 恢复 savedTempAffixes → runtime.tempAffixes
│   └─ 同上
├─ 恢复 virtualSets → runtime.activeVirtualSets
│   └─ 过滤掉已过期的
├─ 恢复冷却数据
├─ 清空 savedBuffs / savedTempAffixes（已恢复到 runtime）
├─ 重新计算天赋门状态 → 注册动态触发器
├─ 重新计算词条共鸣状态
├─ 初始化法力值 = max_mana
├─ 标记属性 dirty → 触发全量重算
└─ 同步原版属性
```

## 6. 存储后端

### 6.1 接口定义

```kotlin
interface StorageProvider {
    fun initialize()
    fun shutdown()
    fun loadPlayer(uuid: UUID): PersistentData?
    fun savePlayer(uuid: UUID, data: PersistentData)
    fun saveAll(dataMap: Map<UUID, PersistentData>)
    fun deletePlayer(uuid: UUID)
    fun exists(uuid: UUID): Boolean
}
```

### 6.2 YAML 存储（默认）

```
plugins/Symphony/data/players/
├── 550e8400-e29b-41d4-a716-446655440000.yml
└── ...
```

```yaml
uuid: 550e8400-e29b-41d4-a716-446655440000
level: 25
exp: 12500
free_points: 3
allocated_points:
  strength: 10
  dexterity: 8
runes:
  berserker:
    level: 2
    active: true
  guardian:
    level: 1
    active: false
rune_fragments:
  berserker: 15
  guardian: 8
unlocked_talents: ["precise_strike", "immovable"]
selected_talents:
  tier_1: "precise_strike"
virtual_sets:
  dragon_slayer:
    pieces: 1
    source: "achievement:dragon_slayer"
    expire_time: -1
saved_buffs:
  - id: "str_potion"
    attribute: "strength"
    operation: "FLAT"
    value: 20.0
    source: "potion:strength_elixir"
    remaining_duration: 180000
saved_temp_affixes:
  - uuid: "a1b2c3d4-..."
    affix_id: "fire_strike"
    level: 2
    params:
      chance: 15
      damage: 35
    source: "potion:fire_elixir"
    remaining_duration: 240000
statistics:
  total_damage_dealt: 125000.0
  total_kills: 342
  total_deaths: 15
  highest_combo: 12
  total_reactions_triggered: 89
preferences:
  show_damage_numbers: true
  show_status_bars: true
  combat_log_enabled: false
```

### 6.3 SQL 存储（SQLite / MySQL）

SQL 后端只维护一张表，整份 `PersistentData` 以 JSON 存在 `data` 列里。读写两端都走 Gson，省掉了列映射维护成本，升级字段时也不用写迁移。

```sql
CREATE TABLE symphony_players (
    uuid VARCHAR(36) PRIMARY KEY,
    data TEXT NOT NULL
);
```

MySQL 连接参数在 `config.yml` 里配置：

```yaml
storage-type: mysql

storage:
  sqlite:
    file: data/symphony.db     # SQLite 时才用
  mysql:
    host: localhost
    port: 3306
    database: symphony
    username: root
    password: ""
```

插件启动时如果驱动（`org.sqlite.JDBC` / `com.mysql.cj.jdbc.Driver`）不在 classpath 上，会打印错误并自动降级到 YAML，不会让服务器起不来。

## 7. 缓存架构

### 7.1 三层缓存

```
┌─────────────────────────────────────────────────┐
│ L1: 属性缓存 (AttributeCache)                    │
│     ConcurrentHashMap<UUID, Map<String, Double>> │
│     惰性重算：dirty 标记 → 下次读取时重算          │
│     生命周期：实体存在期间                         │
└─────────────────────────────────────────────────┘
                    ↑ 读取
┌─────────────────────────────────────────────────┐
│ L2: 玩家数据缓存 (PlayerDataCache)               │
│     ConcurrentHashMap<UUID, SymphonyPlayerData>  │
│     包含 persistent + runtime 两部分              │
│     生命周期：玩家在线期间                         │
└─────────────────────────────────────────────────┘
                    ↑ 加载/保存
┌─────────────────────────────────────────────────┐
│ L3: 存储后端 (StorageProvider)                    │
│     YAML / SQLite / MySQL                        │
│     只存储 persistent 部分                        │
│     异步读写                                      │
└─────────────────────────────────────────────────┘
```

### 7.2 实体运行时数据（非玩家）

怪物等非玩家实体也需要运行时数据（状态层、元素附着），但不需要持久化：

```kotlin
object EntityRuntimeCache {
    private val cache = ConcurrentHashMap<UUID, EntityRuntimeData>()

    fun get(entityId: UUID): EntityRuntimeData {
        return cache.getOrPut(entityId) { EntityRuntimeData() }
    }

    fun remove(entityId: UUID) {
        cache.remove(entityId)
    }

    // 定期清理已卸载实体的数据
    fun cleanup() {
        cache.keys.removeIf { uuid ->
            Bukkit.getEntity(uuid) == null
        }
    }
}

class EntityRuntimeData {
    val statusStacks: MutableMap<String, StatusStackData> = mutableMapOf()
    val elementAuras: MutableMap<String, ElementAuraData> = mutableMapOf()
    val activeShields: MutableList<ShieldData> = mutableListOf()
    val cooldowns: MutableMap<String, Long> = mutableMapOf()
}
```

### 7.3 PlayerDataManager

```kotlin
class PlayerDataManager {
    private val cache = ConcurrentHashMap<UUID, SymphonyPlayerData>()
    private val dirtySet = ConcurrentHashMap.newKeySet<UUID>()

    // 获取玩家数据（缓存优先）
    fun getData(uuid: UUID): SymphonyPlayerData {
        return cache.getOrPut(uuid) {
            val persistent = storageProvider.loadPlayer(uuid) ?: PersistentData()
            SymphonyPlayerData(uuid).also { it.persistent.copyFrom(persistent) }
        }
    }

    fun markDirty(uuid: UUID) { dirtySet.add(uuid) }

    // 定时保存脏数据
    fun saveAllDirty() {
        val toSave = dirtySet.mapNotNull { uuid ->
            dirtySet.remove(uuid)
            cache[uuid]?.let { uuid to it.persistent }
        }.toMap()
        if (toSave.isNotEmpty()) {
            Bukkit.getScheduler().runTaskAsynchronously(plugin) {
                storageProvider.saveAll(toSave)
            }
        }
    }

    // 玩家上线
    fun onPlayerJoin(uuid: UUID) {
        Bukkit.getScheduler().runTaskAsynchronously(plugin) {
            val persistent = storageProvider.loadPlayer(uuid) ?: PersistentData()
            Bukkit.getScheduler().runTask(plugin) {
                val data = SymphonyPlayerData(uuid)
                data.persistent.copyFrom(persistent)
                restoreRuntimeFromPersistent(data)  // 恢复 Buff/临时词条/虚拟套装
                cache[uuid] = data
                AttributeCache.markDirty(uuid)      // 触发属性全量重算
            }
        }
    }

    // 玩家下线
    fun onPlayerQuit(uuid: UUID) {
        val data = cache.remove(uuid) ?: return
        dirtySet.remove(uuid)
        persistRuntimeData(data)  // 将未过期的运行时数据转存到 persistent
        Bukkit.getScheduler().runTaskAsynchronously(plugin) {
            storageProvider.savePlayer(uuid, data.persistent)
        }
        AttributeCache.invalidate(uuid)
    }

    private fun restoreRuntimeFromPersistent(data: SymphonyPlayerData) {
        val now = System.currentTimeMillis()
        // 恢复 Buff
        for (saved in data.persistent.savedBuffs) {
            val expireTime = now + saved.remainingDuration
            if (expireTime > now) {
                data.runtime.activeBuffs.add(ActiveBuff(
                    id = saved.id, attribute = saved.attribute,
                    operation = Operation.valueOf(saved.operation),
                    value = saved.value, expireTime = expireTime,
                    source = saved.source
                ))
            }
        }
        data.persistent.savedBuffs.clear()

        // 恢复临时词条
        for (saved in data.persistent.savedTempAffixes) {
            val expireTime = now + saved.remainingDuration
            if (expireTime > now) {
                data.runtime.tempAffixes.add(TempAffix(
                    uuid = UUID.fromString(saved.uuid), affixId = saved.affixId,
                    level = saved.level, params = saved.params,
                    source = saved.source, expireTime = expireTime,
                    slot = TempAffixSlot.PLAYER
                ))
            }
        }
        data.persistent.savedTempAffixes.clear()

        // 恢复虚拟套装
        for ((setId, setData) in data.persistent.virtualSets) {
            if (setData.expireTime == -1L || setData.expireTime > now) {
                data.runtime.activeVirtualSets[setId] = VirtualSetRuntime(
                    setId = setId, totalPieces = setData.pieces,
                    sources = mutableMapOf(setData.source to VirtualSetSource(
                        setData.source, setData.pieces, setData.expireTime
                    ))
                )
            }
        }
    }

    private fun persistRuntimeData(data: SymphonyPlayerData) {
        val now = System.currentTimeMillis()
        // 当前实现中持久化阈值在 PlayerDataManager 中硬编码
        val buffThreshold = 30_000L
        val affixThreshold = 60_000L

        // 保存未过期 Buff
        data.persistent.savedBuffs.clear()
        for (buff in data.runtime.activeBuffs) {
            val remaining = buff.remainingMs()
            if (remaining > buffThreshold) {
                data.persistent.savedBuffs.add(SavedBuffData(
                    id = buff.id, attribute = buff.attribute,
                    operation = buff.operation.name, value = buff.value,
                    expireTime = buff.expireTime, source = buff.source,
                    remainingDuration = remaining
                ))
            }
        }

        // 保存未过期临时词条
        data.persistent.savedTempAffixes.clear()
        for (affix in data.runtime.tempAffixes) {
            val remaining = if (affix.expireTime == -1L) Long.MAX_VALUE
                           else affix.expireTime - now
            if (remaining > affixThreshold) {
                data.persistent.savedTempAffixes.add(SavedTempAffixData(
                    uuid = affix.uuid.toString(), affixId = affix.affixId,
                    level = affix.level, params = affix.params,
                    source = affix.source, remainingDuration = remaining
                ))
            }
        }
    }
}
```

## 8. 运行时数据的 Tick 管理

运行时数据中有大量需要定时检查的内容（Buff 过期、状态层衰减、元素附着衰减等）。统一由一个 `RuntimeTickTask` 管理：

```kotlin
class RuntimeTickTask : BukkitRunnable() {
    private var tickCount = 0L

    override fun run() {
        tickCount++
        val now = System.currentTimeMillis()

        for (player in Bukkit.getOnlinePlayers()) {
            val data = PlayerDataManager.getData(player.uniqueId)
            var attributeDirty = false

            // ── 每 tick：战斗状态检查 ──
            if (data.runtime.inCombat) {
                if (now - data.runtime.lastCombatActionTime > combatTimeout) {
                    data.runtime.inCombat = false
                    TriggerDispatcher.dispatch(TriggerType.ON_LEAVE_COMBAT, player) {
                        set("combatDuration", now - data.runtime.combatStartTime)
                    }
                }
            }

            // ── 每 tick：连击超时检查 ──
            if (data.runtime.comboCount > 0 && now - data.runtime.lastComboTime > comboTimeout) {
                data.runtime.comboCount = 0
            }

            // ── 每 20 tick (1秒)：Buff 过期检查 ──
            if (tickCount % 20 == 0L) {
                val expiredBuffs = data.runtime.activeBuffs.filter { it.isExpired() }
                if (expiredBuffs.isNotEmpty()) {
                    data.runtime.activeBuffs.removeAll(expiredBuffs)
                    attributeDirty = true
                }
            }

            // ── 每 20 tick：临时词条过期检查 ──
            if (tickCount % 20 == 0L) {
                val expiredAffixes = data.runtime.tempAffixes.filter { it.isExpired() }
                if (expiredAffixes.isNotEmpty()) {
                    data.runtime.tempAffixes.removeAll(expiredAffixes)
                    attributeDirty = true
                }
            }

            // ── 每 20 tick：虚拟套装过期检查 ──
            if (tickCount % 20 == 0L) {
                for ((setId, setRuntime) in data.runtime.activeVirtualSets) {
                    val expiredSources = setRuntime.sources.filter { (_, src) ->
                        src.expireTime != -1L && src.expireTime < now
                    }
                    for ((sourceId, _) in expiredSources) {
                        setRuntime.sources.remove(sourceId)
                    }
                    setRuntime.totalPieces = setRuntime.sources.values.sumOf { it.pieces }
                }
                // 移除件数为 0 的虚拟套装
                data.runtime.activeVirtualSets.entries.removeIf { it.value.totalPieces <= 0 }
                attributeDirty = true
            }

            // ── 每 20 tick：护盾过期检查 ──
            if (tickCount % 20 == 0L) {
                data.runtime.activeShields.removeIf { it.isExpired() || it.amount <= 0 }
            }

            // ── 每 20 tick：法力恢复 ──
            if (tickCount % 20 == 0L) {
                val maxMana = AttributeCache.get(player.uniqueId, "max_mana") ?: 100.0
                val manaRegen = AttributeCache.get(player.uniqueId, "mana_regen") ?: 0.0
                data.runtime.currentMana = minOf(maxMana, data.runtime.currentMana + manaRegen)
            }

            // ── 如果有属性变更 → 标记重算 ──
            if (attributeDirty) {
                AttributeCache.markDirty(player.uniqueId)
            }
        }

        // ── 每 20 tick：非玩家实体的状态层/元素附着衰减 ──
        if (tickCount % 20 == 0L) {
            EntityRuntimeCache.cleanup()
        }
    }
}
```

### 9. Aria 脚本 API

完整的 Aria 脚本 API 参见 [脚本集成文档](07-script-integration.md#2-symphony-aria-命名空间)。

数据操作相关的常用 API：

| 命名空间 | 常用方法 |
|---------|---------|
| `symphony.attribute` | `get / getRaw / buff / modify / remove / recalculate` |
| `symphony.growth` | `getLevel / addExp / insertGem / enhance / activateRune` |
| `symphony.status` | `addStacks / getStacks / clearStacks / hasStatus / setImmune` |
| `symphony.trigger` | `isOnCooldown / setCooldown` |
