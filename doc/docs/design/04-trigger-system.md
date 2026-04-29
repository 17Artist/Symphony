# 触发器系统设计

## 1. 设计理念

触发器系统是 Symphony 的事件驱动核心，参考 MythicMobs 的触发器机制但更加通用化。它是连接游戏事件与词条/技能效果的桥梁。

核心流程：`游戏事件 → 触发器匹配 → 条件检查 → 动作执行`

## 2. 触发器类型注册

触发器类型采用注册表模式，支持内置类型和自定义扩展：

```kotlin
// 触发器类型（可扩展）
class TriggerType private constructor(val id: String) {
    companion object {
        private val registry = ConcurrentHashMap<String, TriggerType>()
        
        fun of(id: String): TriggerType = registry[id] 
            ?: error("Unknown trigger type: $id")
        
        fun register(id: String): TriggerType {
            val type = TriggerType(id)
            registry[id] = type
            return type
        }
        
        fun custom(id: String) = register(id)
        
        // 内置类型
        val ON_ATTACK = register("ON_ATTACK")
        val ON_DEFEND = register("ON_DEFEND")
        // ... 其他内置类型
    }
}
```

## 3. 内置触发器

### 攻击/伤害类

| 触发器 ID               | 说明      | 上下文变量                                                    |
|----------------------|---------|----------------------------------------------------------|
| `ON_ATTACK`          | 攻击实体时   | attacker, victim, damage, damageType, isCritical, weapon |
| `ON_ATTACK_CRITICAL` | 暴击时     | attacker, victim, damage, critMultiplier                 |
| `ON_KILL`            | 击杀实体时   | killer, victim, damage                                   |
| `ON_DAMAGE`          | 造成最终伤害时 | attacker, victim, finalDamage, rawDamage                 |
| `ON_MELEE_ATTACK`    | 近战攻击时   | attacker, victim, damage, weapon                         |
| `ON_RANGED_ATTACK`   | 远程攻击时   | attacker, victim, damage, projectile                     |

### 防御/受伤类

| 触发器 ID          | 说明       | 上下文变量                                        |
|-----------------|----------|----------------------------------------------|
| `ON_DEFEND`     | 被攻击时     | attacker, victim, damage, damageType         |
| `ON_DAMAGED`    | 受到伤害后    | attacker, victim, finalDamage, currentHealth |
| `ON_BLOCK`      | 格挡成功时    | attacker, victim, blockedDamage              |
| `ON_DODGE`      | 闪避成功时    | attacker, victim, dodgedDamage               |
| `ON_DEATH`      | 死亡时      | victim, killer, damage                       |
| `ON_LOW_HEALTH` | 生命值低于阈值时 | entity, currentHealth, maxHealth, threshold  |

### 移动/交互类

| 触发器 ID           | 说明    | 上下文变量                       |
|------------------|-------|-----------------------------|
| `ON_MOVE`        | 移动时   | entity, from, to, speed     |
| `ON_JUMP`        | 跳跃时   | entity, location            |
| `ON_SNEAK`       | 潜行切换时 | entity, isSneaking          |
| `ON_SPRINT`      | 疾跑切换时 | entity, isSprinting         |
| `ON_INTERACT`    | 交互时   | player, action, block, item |
| `ON_RIGHT_CLICK` | 右键点击时 | player, item, block         |
| `ON_LEFT_CLICK`  | 左键点击时 | player, item, block         |

### 装备/物品类

| 触发器 ID          | 说明      | 上下文变量                      |
|-----------------|---------|----------------------------|
| `ON_EQUIP`      | 装备穿戴时   | player, item, slot         |
| `ON_UNEQUIP`    | 装备卸下时   | player, item, slot         |
| `ON_HOLD`       | 手持物品切换时 | player, item, previousItem |
| `ON_CONSUME`    | 消耗物品时   | player, item               |
| `ON_BREAK_ITEM` | 物品耐久耗尽时 | player, item               |

### 周期/状态类

| 触发器 ID            | 说明     | 上下文变量                       |
|-------------------|--------|-----------------------------|
| `ON_TIMER`        | 定时触发   | entity, interval, tickCount |
| `ON_ENTER_COMBAT` | 进入战斗时  | entity                      |
| `ON_LEAVE_COMBAT` | 脱离战斗时  | entity, combatDuration      |
| `ON_LEVEL_UP`     | 升级时    | player, oldLevel, newLevel  |
| `ON_RESPAWN`      | 重生时    | player                      |
| `ON_JOIN`         | 加入服务器时 | player                      |
| `ON_QUIT`         | 退出服务器时 | player                      |

### 技能/法力类

| 触发器 ID          | 说明    | 上下文变量                         |
|-----------------|-------|-------------------------------|
| `ON_SKILL_CAST` | 释放技能时 | caster, skill, level, targets |
| `ON_SKILL_HIT`  | 技能命中时 | caster, victim, skill, damage |
| `ON_MANA_USE`   | 消耗法力时 | entity, amount, remaining     |
| `ON_MANA_FULL`  | 法力充满时 | entity                        |

## 4. 触发器上下文

```kotlin
interface ITriggerContext {
    val triggerType: TriggerType
    val entity: LivingEntity
    val target: LivingEntity?
    val timestamp: Long
    val location: Location
    
    fun <T> get(key: String): T?
    fun set(key: String, value: Any)
    fun has(key: String): Boolean
    
    // 便捷属性
    val damage: Double? get() = get("damage")
    val item: ItemStack? get() = get("item")
    val isCritical: Boolean get() = get("isCritical") ?: false
}
```

## 5. 触发条件系统

### 5.1 内置条件

| 条件类型              | 说明        | 参数                             |
|-------------------|-----------|--------------------------------|
| `CHANCE`          | 概率判定      | value (0~100)                  |
| `COOLDOWN`        | 冷却时间      | value (毫秒)                     |
| `HEALTH_ABOVE`    | 生命值高于     | value (百分比 0~100)              |
| `HEALTH_BELOW`    | 生命值低于     | value (百分比 0~100)              |
| `MANA_ABOVE`      | 法力值高于     | value (百分比 0~100)              |
| `MANA_BELOW`      | 法力值低于     | value (百分比 0~100)              |
| `HAS_PERMISSION`  | 拥有权限      | value (权限节点)                   |
| `IN_WORLD`        | 在指定世界     | value (世界名)                    |
| `IN_BIOME`        | 在指定生物群系   | value (生物群系 ID)                |
| `IS_SNEAKING`     | 正在潜行      | —                              |
| `IS_SPRINTING`    | 正在疾跑      | —                              |
| `IS_FLYING`       | 正在飞行      | —                              |
| `HOLDING_TYPE`    | 手持物品类型    | value (Material 名)             |
| `WEARING_SET`     | 穿戴套装      | value (套装 ID)                  |
| `HAS_AFFIX`       | 拥有指定词条    | value (词条 ID)                  |
| `ATTRIBUTE_ABOVE` | 属性值高于     | attribute, value               |
| `ATTRIBUTE_BELOW` | 属性值低于     | attribute, value               |
| `LEVEL_RANGE`     | 等级范围      | min, max                       |
| `DAMAGE_TYPE`     | 伤害类型匹配    | value (physical/magic/fire...) |
| `TARGET_TYPE`     | 目标类型      | value (PLAYER/MOB/BOSS...)     |
| `SCRIPT`          | Aria 脚本条件 | code (返回 boolean)              |

### 5.2 条件组合

支持 `AND`、`OR`、`NOT` 逻辑组合，可嵌套：

```yaml
conditions:
  - type: AND
    children:
      - type: CHANCE
        value: 30
      - type: COOLDOWN
        value: 5000
      - type: OR
        children:
          - type: HEALTH_BELOW
            value: 50
          - type: HAS_AFFIX
            value: "berserker"
```

条件求值采用短路策略：AND 遇到 false 立即返回，OR 遇到 true 立即返回。

### 5.3 条件接口

```kotlin
interface ITriggerCondition {
    val type: String
    fun evaluate(context: ITriggerContext, params: Map<String, Any>): Boolean
}

// 组合条件
class AndCondition(val children: List<ITriggerCondition>) : ITriggerCondition {
    override val type = "AND"
    override fun evaluate(context: ITriggerContext, params: Map<String, Any>): Boolean {
        return children.all { it.evaluate(context, params) }
    }
}
```

## 6. 触发器分发器

`TriggerDispatcher` 负责监听 Bukkit 事件并分发到词条系统：

```kotlin
object TriggerDispatcher {
    
    fun dispatch(
        triggerType: TriggerType,
        entity: LivingEntity,
        contextBuilder: TriggerContext.Builder.() -> Unit
    ) {
        val context = TriggerContext.Builder(triggerType, entity)
            .apply(contextBuilder)
            .build()
        
        // 获取实体所有装备上的词条
        val affixes = collectEntityAffixes(entity)
        
        for (affix in affixes) {
            val definition = AffixManager.getDefinition(affix.affixId) ?: continue
            
            for (trigger in definition.triggers) {
                if (trigger.type != triggerType) continue
                
                // 评估条件
                if (!evaluateConditions(trigger.conditions, context, affix.parameters)) continue
                
                // 发布事件（允许取消）
                val event = AffixTriggerEvent(entity, affix, triggerType, context)
                Bukkit.getPluginManager().callEvent(event)
                if (event.isCancelled) continue
                
                // 执行动作
                executeActions(trigger.actions, context, affix)
            }
        }
    }
}
```

## 7. 自定义触发器

其他插件可通过 API 注册和触发自定义触发器：

```kotlin
// 注册自定义触发器类型
TriggerManager.register(
    TriggerType.custom("myplugin:on_combo"),
    displayName = "连击触发",
    contextKeys = listOf("comboCount", "totalDamage")
)

// 在适当时机触发
TriggerDispatcher.dispatch(
    TriggerType.of("myplugin:on_combo"),
    entity = player
) {
    set("comboCount", 5)
    set("totalDamage", 150.0)
}
```

## 8. ON_TIMER 触发器实现

定时触发器通过 BukkitRunnable 实现，每 tick 检查：

```kotlin
class PeriodicTriggerTask : BukkitRunnable() {
    private var tickCount = 0L
    
    override fun run() {
        tickCount++
        
        for (player in Bukkit.getOnlinePlayers()) {
            val affixes = collectEntityAffixes(player)
            
            for (affix in affixes) {
                val definition = AffixManager.getDefinition(affix.affixId) ?: continue
                
                for (trigger in definition.triggers) {
                    if (trigger.type != TriggerType.ON_TIMER) continue
                    
                    val interval = trigger.getParam("interval", 20) // 默认 20 tick = 1 秒
                    if (tickCount % interval != 0L) continue
                    
                    TriggerDispatcher.dispatch(TriggerType.ON_TIMER, player) {
                        set("interval", interval)
                        set("tickCount", tickCount)
                    }
                }
            }
        }
    }
}
```
