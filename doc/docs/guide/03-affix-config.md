# 词条配置说明

## 1. 词条文件结构

每个词条一个 YAML 文件，放在 `plugins/Symphony/affixes/` 目录下。

### 最小配置

```yaml
id: sharp_blade
display_name: "锋利之刃"
description:
  - "&7攻击力 +{damage}"
max_level: 3
rarity: COMMON
category: weapon

levels:
  1:
    damage: 5
  2:
    damage: 10
  3:
    damage: 15

passive_attributes:
  physical_damage:
    operation: FLAT
    value: "{damage}"
```

### 完整配置

```yaml
id: fire_strike
display_name: "烈焰打击"
description:
  - "&c攻击时有 {chance}% 概率"
  - "&c造成 {damage} 点火焰伤害"
max_level: 5
rarity: RARE
category: weapon
exclusive_group: "fire"        # 互斥组

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

# 被动属性
passive_attributes:
  fire_damage:
    operation: FLAT
    value: "{damage}"

# 触发效果
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
      - type: PARTICLE
        particle: FLAME
        count: 20
        target: TRIGGER_TARGET
      - type: SOUND
        sound: "entity.blaze.shoot"
        volume: 0.8
        pitch: 1.2
```

## 2. 参数说明

### 基础参数

| 参数 | 类型 | 必填 | 说明 |
|------|------|------|------|
| `id` | String | 是 | 唯一标识符 |
| `display_name` | String | 是 | 显示名称（支持颜色代码） |
| `description` | List | 是 | 描述文本，`{param}` 引用等级参数 |
| `max_level` | Integer | 是 | 最大等级 |
| `rarity` | String | 是 | 稀有度 |
| `category` | String | 否 | 可附着类别：`weapon`/`armor`/`accessory`/`any` |
| `exclusive_group` | String | 否 | 互斥组，同组词条不能共存 |

### 稀有度

| 稀有度 | 颜色 | 说明 |
|--------|------|------|
| `COMMON` | &f 白色 | 普通 |
| `UNCOMMON` | &a 绿色 | 非凡 |
| `RARE` | &9 蓝色 | 稀有 |
| `EPIC` | &5 紫色 | 史诗 |
| `LEGENDARY` | &6 金色 | 传说 |
| `MYTHIC` | &c 红色 | 神话 |

## 3. 触发器配置

每个触发器条目包含 `type`、`conditions`、`actions` 三部分。

### 触发器类型

参见 [触发器参考](04-trigger-reference.md) 获取完整列表。常用类型：

- `ON_ATTACK` — 攻击时
- `ON_DEFEND` — 被攻击时
- `ON_KILL` — 击杀时
- `ON_DAMAGED` — 受伤后
- `ON_TIMER` — 定时触发

### 条件配置

```yaml
conditions:
  # 简单条件
  - type: CHANCE
    value: 30                  # 30% 概率
  
  # 冷却
  - type: COOLDOWN
    value: 5000                # 5 秒冷却
  
  # 生命值条件
  - type: HEALTH_BELOW
    value: 50                  # 生命值低于 50%
  
  # 组合条件
  - type: AND
    children:
      - type: CHANCE
        value: 20
      - type: OR
        children:
          - type: IS_SNEAKING
          - type: HEALTH_BELOW
            value: 30
  
  # Aria 脚本条件
  - type: SCRIPT
    code: |
      val.hp = symphony.entity.getHealth(server.trigger_entity)
      val.maxHp = symphony.entity.getMaxHealth(server.trigger_entity)
      return hp / maxHp < 0.3
```

### 动作配置

```yaml
actions:
  # 调用技能
  - type: SKILL
    provider: "symphony"
    skill: "fire_burst"
    level: "{level}"
  
  # 临时属性 Buff
  - type: ATTRIBUTE_BUFF
    attribute: "physical_damage"
    operation: PERCENT
    value: 0.2
    duration: 10000
  
  # 造成伤害
  - type: DAMAGE
    amount: "{damage}"
    damage_type: fire
    target: TRIGGER_TARGET
  
  # 治疗
  - type: HEAL
    amount: 20
    target: SELF
  
  # 药水效果
  - type: POTION
    effect: SPEED
    duration: 100
    amplifier: 1
    target: SELF
  
  # 粒子
  - type: PARTICLE
    particle: FLAME
    count: 20
    offset: [0.5, 0.5, 0.5]
    target: TRIGGER_TARGET
  
  # 音效
  - type: SOUND
    sound: "entity.blaze.shoot"
    volume: 1.0
    pitch: 1.2
  
  # 消息
  - type: MESSAGE
    message: "&c烈焰打击!"
    type: actionbar
  
  # 执行命令
  - type: COMMAND
    command: "effect give {player} minecraft:glowing 5 0"
    as: console
  
  # Aria 脚本
  - type: SCRIPT
    code: |
      val.target = server.trigger_target
      val.damage = server.trigger_damage
      symphony.entity.heal(server.trigger_entity, damage * 0.1)
```

## 4. 词条池配置

词条池定义了随机生成词条时的候选列表和规则。

```yaml
# affix-pools/weapon_pool.yml
id: weapon_pool
display_name: "武器词条池"

entries:
  - affix: sharp_blade
    weight: 150
    level_range: [1, 3]
  - affix: fire_strike
    weight: 80
    level_range: [1, 5]
  - affix: thunder_strike
    weight: 80
    level_range: [1, 3]
  - affix: lifesteal_hit
    weight: 50
    level_range: [1, 2]
  - affix: critical_boost
    weight: 100
    level_range: [1, 3]

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

### 生成命令

```
/sym affix generate <玩家> weapon_pool
```

## 5. 词条示例集

### 被动词条（无触发器）

```yaml
# 生命加成
id: vitality
display_name: "活力"
description: ["&a最大生命值 +{hp}"]
max_level: 5
rarity: COMMON
category: armor
levels:
  1:
    hp: 10
  2:
    hp: 20
  3:
    hp: 35
  4:
    hp: 50
  5:
    hp: 75
passive_attributes:
  max_health:
    operation: FLAT
    value: "{hp}"
```

### 防御触发词条

```yaml
# 受击反弹
id: thorns_aura
display_name: "荆棘光环"
description: ["&e被攻击时反弹 {percent}% 伤害"]
max_level: 3
rarity: UNCOMMON
category: armor
levels:
  1:
    percent: 10
  2:
    percent: 20
  3:
    percent: 30
triggers:
  - type: ON_DEFEND
    actions:
      - type: DAMAGE
        amount: "{percent}% * trigger_damage"
        damage_type: physical
        target: TRIGGER_ATTACKER
      - type: PARTICLE
        particle: CRIT
        count: 10
        target: TRIGGER_ATTACKER
```

### 击杀触发词条

```yaml
# 灵魂收割
id: soul_harvest
display_name: "灵魂收割"
description: ["&5击杀时恢复 {heal} 点生命值"]
max_level: 3
rarity: RARE
category: weapon
levels:
  1:
    heal: 10
  2:
    heal: 20
  3:
    heal: 35
triggers:
  - type: ON_KILL
    actions:
      - type: HEAL
        amount: "{heal}"
        target: SELF
      - type: PARTICLE
        particle: SOUL
        count: 15
        target: SELF
      - type: SOUND
        sound: "entity.wither.ambient"
        volume: 0.3
        pitch: 2.0
```
