# 事件与 API 参考

Symphony 所有公开 API 与事件钩子。API 入口统一走 `SymphonyAPI.getInstance()`。

## 事件

### 伤害流水线（三段式）

每次 `EntityDamageEvent`（含 `EntityDamageByEntityEvent`）会依次发布三个 Symphony 事件，允许第三方插件在不同阶段介入：

| # | 事件                        | 时机                | 可修改                                |
|---|---------------------------|-------------------|------------------------------------|
| 1 | `SymphonyPreDamageEvent`  | 暴击判定后、减伤前         | `baseDamage`、`isCritical`          |
| 2 | `SymphonyMitigationEvent` | 防御/穿透/格挡应用后、元素伤害前 | `finalPhysical`                    |
| 3 | `SymphonyDamageEvent`     | 元素伤害合并后、实际施加前     | `finalDamage`、`elementDamages`     |

`SymphonyMitigationEvent.reductionPercent` / `isCritical` / `blocked` 字段是**只读信息**（声明为 `val`）：减伤计算在事件发布时已完成，仅供监听器分析。要调整减伤幅度请直接改 `finalPhysical`。

三者任一被取消都会中止伤害。

### 资源事件

| 事件                         | 可取消 | 可改 amount | 用途              |
|----------------------------|-----|-----------|-----------------|
| `SymphonyHealEvent`        | ✓   | ✓         | 反治疗环境、回血倍率 buff |
| `SymphonyManaConsumeEvent` | ✓   | ✓         | 反魔 debuff、零消耗法术 |

### 词条生命周期

| 事件                  | 可取消 | 说明           |
|---------------------|-----|--------------|
| `AffixEquipEvent`   | ✗   | 词条因装备变化进入激活态 |
| `AffixUnequipEvent` | ✗   | 词条因装备变化进入失活态 |
| `AffixTriggerEvent` | ✓   | 词条触发器执行前     |

### Buff 生命周期

| 事件                | 可取消 | 可改                   | 说明               |
|-------------------|-----|----------------------|------------------|
| `BuffApplyEvent`  | ✓   | `value`、`durationMs` | Buff 加入前，允许调整    |
| `BuffExpireEvent` | ✗   | —                    | Buff 移出 runtime 列表前 |

`BuffExpireEvent` 字段 `reason` 枚举：

| reason | 触发场景 |
|--------|----------|
| `TIMEOUT` | `RuntimeTickTask` 检测到 `expireTime` 已过 |
| `MANUAL_REMOVE` | `/sym player buff clear`、`SymphonyAPI.removeAttribute`、`symphony.attribute.remove` 脚本桥、天赋失活清理 |
| `REPLACED` | 同 `source` + `attribute` 的旧 buff 被新 buff 替换（命令 buff add / 词条 ATTRIBUTE_BUFF / API setAttribute） |
| `PLAYER_QUIT` | 玩家退出服务器，所有 buff 在 `PlayerQuitEvent` 处一并发布后清理 |

### 触发器与状态层

| 事件                       | 可取消 | 说明                                       |
|--------------------------|-----|------------------------------------------|
| `TriggerDispatchEvent`   | ✓   | 全局触发器派发前，可整批拦截                           |
| `StatusLayerChangeEvent` | ✗   | 状态层数增减（STACK / UNSTACK / EXPIRE / CLEAR） |

### 属性、成长、技能

| 事件                     | 可取消 | 说明                                           |
|------------------------|-----|----------------------------------------------|
| `AttributeUpdateEvent` | ✓   | 属性值变化时，附带 `source`                           |
| `EnhanceEvent`         | ✓   | 装备强化结束，附带 `EnhanceResult`                    |
| `GemInsertEvent`       | ✓   | 宝石镶嵌（`GemManager.insertGem(player, ...)` 重载） |
| `LevelChangeEvent`     | ✓   | 玩家等级变化                                       |
| `RuneActivateEvent`    | ✓   | 符文激活                                         |
| `SkillCastEvent`       | ✓   | 技能施放前                                        |

## API 扩展点

### 属性监听器（编程式，非 Bukkit 事件）

```kotlin
SymphonyAPI.getInstance().getAttributeManager().addListener(object : AttributeListener {
    override val attributeIds = setOf("physical_damage", "critical_chance")
    override fun onChange(entity, id, old, new) {
        // 按 id 过滤，低开销
    }
})
```

对比 `AttributeUpdateEvent`：监听器走直接引用，不反射、不走 Bukkit 事件派发、可按 id 精确订阅。高频场景（每 tick recalc）优先用这个。

### 自定义条件类型

```kotlin
SymphonyAPI.getInstance().getTriggerManager().registerConditionType(
    "ABOVE_Y",
    ICustomCondition { cond, ctx, params ->
        val y = (cond["value"] as? Number)?.toDouble() ?: 0.0
        ctx.entity.location.y > y
    }
)
```

词条 YAML 里即可使用：

```yaml
triggers:
  - type: ON_ATTACK
    conditions:
      - type: ABOVE_Y
        value: 100
```

### 自定义 Action Handler

```kotlin
SymphonyAPI.getInstance().getAffixManager().registerActionHandler(
    "MY_ACTION",
    object : IActionHandler {
        override fun execute(params, context, affix) {
            val target = context.target ?: return
            // 自定义行为
        }
    }
)
```

### 自定义元素反应

```kotlin
SymphonyAPI.getInstance().registerReaction(
    id = "thunderstorm",
    displayName = "雷暴",
    trigger = "lightning",
    aura = "electro_infused",
    reactionType = "DOT_AOE",
    multiplier = 2.0,
    radius = 5.0,
    ticks = 40
)
```

### 跨插件实体元数据

```kotlin
val meta = SymphonyAPI.getInstance().getMetadata()
meta.set(entity, "bleed", "stacks", 5)
meta.set(entity, "bleed", "expire_time", System.currentTimeMillis() + 5000)
val stacks = meta.get(entity, "bleed", "stacks") as? Int ?: 0
```

避免 Bukkit `MetadataValue` 的生命周期陷阱；实体下线自动回收。

### 属性流水线解析

```kotlin
val explain = SymphonyAPI.getInstance().explain(player, "physical_damage") ?: return
println("${explain.displayName} = ${explain.finalValue}")
for (c in explain.contributions) {
    println("  ${c.providerId}: ${c.operation} ${c.value}")
}
```

与 `/sym explain` 同源，供外部 UI / 诊断工具复用。

### 快照 / Diff

```kotlin
val before = api.snapshot(player)
// ... 某些操作 ...
val after = api.snapshot(player)
for ((id, pair) in api.diff(before, after)) {
    println("$id: ${pair.first} → ${pair.second}")
}
```

## 子 Manager 速查

| Manager                 | 关键方法                                                                                                              |
|-------------------------|-------------------------------------------------------------------------------------------------------------------|
| `IAttributeManager`     | `registerAttribute` / `registerProvider` / `getValue` / `getValues` / `markDirty` / `recalculate` / `addListener` |
| `IAffixManager`         | `registerAffix` / `getAffixes` / `addAffix` / `removeAffix` / `clearAffixes` / `generateAffixes` / `registerPool` / `getPool` / `registerActionHandler` |
| `ITriggerManager`       | `dispatch` / `registerConditionType` / `isOnCooldown` / `setCooldown`                                             |
| `ISkillProviderManager` | `registerProvider` / `castSkill` / `hasSkill`                                                                     |
| `IGrowthManager`        | `getLevel` / `addExp` / `getExp` / `getRequiredExp` / `insertGem` / `removeGem` / `unlockSlot` / `enhance` / `activateRune` / `deactivateRune` / `addFragments` / `getFragments` / `getActiveSets` |

完整签名见源码 `symphony-common/src/main/kotlin/priv/seventeen/artist/symphony/api/`。

## MythicMobs 怪物属性集成

在 MM mob 配置文件 `plugins/MythicMobs/Mobs/*.yml` 里直接写 `Symphony:` 段，怪物生成时自动套属性与词条：

```yaml
BanditBoss:
  Type: ZOMBIE
  Display: '&c山贼头目'
  Health: 200
  Symphony:
    attributes:
      physical_damage: 40
      physical_defense: 20
      max_health: 200
      critical_chance: 15%      # 带百分号 → PERCENT 叠加
      fire_resistance: 30%
    affixes:
      - bleed_on_hit             # 简写：affixId，默认 level=1
      - id: fire_aura
        level: 2
        params:
          radius: 5
```

- 解析时机：`MythicMobSpawnEvent` 触发，通过 compileOnly API 直接读取配置 `Symphony` 段
- 数据存活：怪物 UUID 一一对应；`MythicMobDeathEvent` 自动清理
- 属性通路：`MythicMobAttributeProvider`（优先级 150，异步安全）
- 词条通路：注入 `AffixManagerImpl.collectEntityAffixes`，走 `AffixPassiveProvider` 和触发器系统

MM 未安装时整套逻辑静默跳过，不影响 Symphony 本体运行。
