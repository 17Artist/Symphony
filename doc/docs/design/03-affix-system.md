# 词条系统设计

## 1. 核心概念

词条（Affix）是附着在物品上的特殊效果单元。每个词条由以下部分组成：

- 词条定义（Affix Definition）— 配置文件中的词条模板
- 词条实例（Affix Instance）— 附着在具体物品上的词条，包含等级和参数快照
- 触发器绑定（Trigger Binding）— 词条在何种条件下触发
- 动作序列（Action Sequence）— 触发后执行的效果列表

## 2. 词条定义配置

```yaml
# affixes/fire_strike.yml
id: fire_strike
display_name: "烈焰打击"
description:
  - "&c攻击时有 {chance}% 概率造成 {damage} 点火焰伤害"
max_level: 5
rarity: RARE                   # COMMON | UNCOMMON | RARE | EPIC | LEGENDARY | MYTHIC
category: weapon               # 可附着的物品类别：weapon | armor | accessory | any
exclusive_group: "fire"        # 互斥组（同组词条不能共存）

# 每级参数
levels:
  1:
    chance: 10
    damage: 20
  2:
    chance: 15
    damage: 35
  3:
    chance: 20
    damage: 50
  4:
    chance: 25
    damage: 70
  5:
    chance: 30
    damage: 100
# 触发器配置
triggers:
  - type: ON_ATTACK
    conditions:
      - type: CHANCE
        value: "{chance}"
      - type: COOLDOWN
        value: 3000
    actions:
      - type: SKILL
        provider: "symphony"
        skill: "fire_burst"
        level: "{level}"
      - type: ATTRIBUTE_BUFF
        attribute: "fire_damage"
        operation: FLAT
        value: "{damage}"
        duration: 5000

# 被动属性（始终生效，无需触发）
passive_attributes:
  physical_damage:
    operation: FLAT
    value: "{damage}"          # 引用等级参数
```

## 3. 词条效果类型（Actions）

| 类型 | 说明 | 关键参数 |
|------|------|----------|
| `SKILL` | 调用技能提供者 | provider, skill, level, params |
| `ATTRIBUTE_BUFF` | 临时属性修改 | attribute, operation, value, duration |
| `ATTRIBUTE_PERMANENT` | 永久属性修改（被动） | attribute, operation, value |
| `DAMAGE` | 造成额外伤害 | amount, type, target |
| `HEAL` | 恢复生命值 | amount, target |
| `MANA` | 恢复/消耗法力 | amount, target |
| `POTION` | 施加药水效果 | effect, duration, amplifier, target |
| `PARTICLE` | 播放粒子效果 | particle, count, offset |
| `SOUND` | 播放音效 | sound, volume, pitch |
| `MESSAGE` | 发送消息 | message, type(chat/actionbar/title) |
| `COMMAND` | 执行命令 | command, as(player/console) |
| `SCRIPT` | 执行 Aria 脚本 | code 或 file |

### target 参数

动作中的 `target` 参数指定效果目标：

| 值 | 说明 |
|----|------|
| `SELF` | 词条持有者自身 |
| `TRIGGER_TARGET` | 触发器上下文中的目标 |
| `TRIGGER_ATTACKER` | 触发器上下文中的攻击者 |
| `NEARBY_ENEMIES` | 附近敌对实体 |
| `NEARBY_ALLIES` | 附近友方实体 |

## 4. 词条实例数据结构

```kotlin
data class AffixInstance(
    val uuid: UUID,                        // 实例唯一 ID
    val affixId: String,                   // 词条定义 ID
    val level: Int,                        // 词条等级
    val parameters: Map<String, Any>,      // 当前等级的参数快照
    val timestamp: Long                    // 生成时间戳
)
```

物品上的词条数据存储在 PDC 中，JSON 序列化：

```json
[
  {
    "uuid": "550e8400-e29b-41d4-a716-446655440000",
    "affix_id": "fire_strike",
    "level": 3,
    "params": { "chance": 20, "damage": 50 }
  },
  {
    "uuid": "6ba7b810-9dad-11d1-80b4-00c04fd430c8",
    "affix_id": "lifesteal_hit",
    "level": 1,
    "params": { "percent": 5 }
  }
]
```

## 5. 词条池与随机生成

### 5.1 词条池配置

```yaml
# affix-pools/weapon_pool.yml
id: weapon_pool
display_name: "武器词条池"

entries:
  - affix: fire_strike
    weight: 100
    level_range: [1, 3]
  - affix: ice_slow
    weight: 80
    level_range: [1, 5]
  - affix: critical_boost
    weight: 120
    level_range: [1, 3]
  - affix: lifesteal_hit
    weight: 60
    level_range: [1, 2]

generation:
  min_affixes: 1
  max_affixes: 4
  rarity_weights:
    COMMON:
      min: 1
      max: 2
    UNCOMMON:
      min: 1
      max: 3
    RARE:
      min: 2
      max: 3
    EPIC:
      min: 2
      max: 4
    LEGENDARY:
      min: 3
      max: 4
    MYTHIC:
      min: 3
      max: 5
  allow_duplicates: false
  luck_influence: 0.1
```

### 5.2 生成算法

```
输入：词条池 pool, 物品稀有度 rarity, 玩家幸运值 luck
输出：List<AffixInstance>

1. 根据 rarity 确定词条数量范围 [min, max]
2. 在范围内随机确定词条数量 count
3. 复制 pool.entries 为候选列表
4. 重复 count 次：
   a. 对每个候选词条计算有效权重：weight * (1 + luck * luck_influence)
   b. 加权随机选择一个词条
   c. 在 level_range 内随机确定等级
   d. 检查互斥组（exclusive_group），移除同组候选
   e. 如果 !allow_duplicates，移除已选词条
   f. 创建 AffixInstance
5. 返回结果列表
```

## 6. 词条 Lore 渲染

词条信息会渲染到物品 Lore 中：

```yaml
# config/lore-format.yml
affix_lore:
  header: "&8&m─────────&r &6词条 &8&m─────────"
  format: "&7[{rarity_color}{rarity_name}&7] {display_name} &8Lv.{level}"
  description_prefix: "  &7"
  footer: "&8&m──────────────────────"
  
  rarity_colors:
    COMMON: "&f"
    UNCOMMON: "&a"
    RARE: "&9"
    EPIC: "&5"
    LEGENDARY: "&6"
    MYTHIC: "&c"
```

渲染示例：
```
─────────── 词条 ───────────
[&9稀有] 烈焰打击 Lv.3
  攻击时有 20% 概率造成 50 点火焰伤害
[&a普通] 生命偷取 Lv.1
  攻击时恢复造成伤害的 5%
────────────────────────────
```
