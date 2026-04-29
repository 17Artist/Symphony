# 成长系统配置说明

## 1. 等级系统

### 配置文件

```yaml
# config/level.yml
level:
  max_level: 100
  
  # 升级经验公式
  exp_formula: |
    val.level = args[0]
    return math.floor(100 * math.pow(level, 1.5) + level * 50)
  
  # 每级属性成长
  attribute_growth:
    max_health:
      base: 20
      per_level: 2.0
      # 可选：Aria 公式（优先于 per_level）
      formula: |
        val.level = args[0]
        return 20 + level * 2 + math.floor(level / 10) * 5
    physical_damage:
      base: 1
      per_level: 0.5
    physical_defense:
      base: 0
      per_level: 0.3
    max_mana:
      base: 100
      per_level: 5
    mana_regen:
      base: 1
      per_level: 0.1
  
  # 经验来源
  exp_sources:
    mob_kill:
      enabled: true
      formula: |
        val.mobLevel = args[0]
        val.playerLevel = args[1]
        val.baseExp = args[2]
        val.diff = playerLevel - mobLevel
        if (diff > 10) { return 0 }
        return baseExp * math.max(0.1, 1 - diff * 0.05)
  
  # 升级特效
  effects:
    sound: "entity.player.levelup"
    particle: "VILLAGER_HAPPY"
    title:
      main: "&6&l升级!"
      sub: "&e等级 {old_level} → {new_level}"
```

### 命令

```
/sym player level get <玩家>
/sym player level set <玩家> <等级>
```

> 目前只有 get/set，没有 addexp 子命令——直接改 `data.persistent.exp` 的话用 API 或 `/sym player attr set` 绕过。

## 2. 宝石系统

### 2.1 宝石定义

在 `plugins/Symphony/gems/` 目录下创建：

```yaml
# gems/sapphire.yml
id: sapphire
display_name: "&9蓝宝石"
description:
  - "&7镶嵌后增加魔法攻击力"
max_level: 5
material: LAPIS_LAZULI
custom_model_data: 2002

levels:
  1:
    attributes:
      magic_damage:
        operation: FLAT
        value: 5
    lore: "&9+5 魔法攻击力"
  2:
    attributes:
      magic_damage:
        operation: FLAT
        value: 12
      max_mana:
        operation: FLAT
        value: 10
    lore: "&9+12 魔法攻击力 &b+10 法力上限"
  3:
    attributes:
      magic_damage:
        operation: FLAT
        value: 20
      max_mana:
        operation: FLAT
        value: 25
      mana_regen:
        operation: FLAT
        value: 0.5
    lore: "&9+20 魔法攻击力 &b+25 法力 +0.5 法力恢复"

synthesis:
  enabled: true
  count: 3
  success_rate: 1.0
  failure_action: "keep"
```

### 2.2 宝石槽配置

装备的宝石槽数量由物品稀有度决定：

```yaml
# config/gem-slots.yml
gem_slots:
  default_slots:
    COMMON: 0
    UNCOMMON: 1
    RARE: 2
    EPIC: 3
    LEGENDARY: 4
    MYTHIC: 5
  
  # 解锁道具
  unlock_item:
    material: NETHER_STAR
    custom_model_data: 2100
    display_name: "&e宝石槽钥匙"
```

### 2.3 操作命令

```
/sym item gem list <装备槽>
/sym item gem insert <装备槽> <宝石槽索引> <宝石ID> [等级]
/sym item gem remove <装备槽> <宝石槽索引>
/sym item gem unlock <装备槽> <宝石槽索引>
```

装备槽大小写不敏感，写 `MAIN / OFF / HELMET / CHEST / LEGS / BOOTS` 或对应的 `HAND / OFF_HAND / HEAD / CHESTPLATE / LEGGINGS / FEET` 都行。

## 3. 符文系统

### 3.1 符文定义

在 `plugins/Symphony/runes/` 目录下创建：

```yaml
# runes/guardian.yml
id: guardian
display_name: "&b守护者符文"
description:
  - "&7提升防御力和生命值"
max_level: 3
category: defense
icon:
  material: DIAMOND
  custom_model_data: 3002

activation:
  type: FRAGMENT
  fragments_required:
    1: 10
    2: 30
    3: 60

passive_attributes:
  1:
    physical_defense:
      operation: FLAT
      value: 10
    max_health:
      operation: FLAT
      value: 20
  2:
    physical_defense:
      operation: FLAT
      value: 25
    max_health:
      operation: FLAT
      value: 50
    damage_reduction:
      operation: FLAT
      value: 0.03
  3:
    physical_defense:
      operation: FLAT
      value: 50
    max_health:
      operation: FLAT
      value: 100
    damage_reduction:
      operation: FLAT
      value: 0.08
    block_chance:
      operation: FLAT
      value: 0.05
triggers:
  - type: ON_LOW_HEALTH
    conditions:
      - type: HEALTH_BELOW
        value: 20
      - type: COOLDOWN
        value: 60000
    actions:
      - type: ATTRIBUTE_BUFF
        attribute: damage_reduction
        operation: FLAT
        value: "0.15 * {level}"
        duration: 8000
      - type: HEAL
        amount: "20 * {level}"
        target: SELF
      - type: PARTICLE
        particle: TOTEM
        count: 30
        target: SELF
      - type: MESSAGE
        message: "&b&l守护之盾已激活!"
        type: actionbar
```

### 3.2 碎片获取配置

```yaml
# config/rune-fragments.yml
fragment_sources:
  mob_kill:
    enabled: true
    base_chance: 0.05
    luck_influence: 0.01
    fragment_pool:
      - rune: berserker
        weight: 100
      - rune: guardian
        weight: 80
      - rune: arcane
        weight: 60
    amount_range: [1, 3]
```

### 3.3 命令

```
/sym player rune activate <玩家> <符文ID> [等级]
/sym player rune fragment <玩家> <符文ID> <数量>
```

> 停用符文不通过命令——目前需要通过 API `GrowthManager.deactivateRune(player, runeId)`，命令没对外开这个子项。

## 4. 强化系统

### 4.1 配置

参见 [成长系统设计](../design/06-growth-system.md) 中的强化配置。

### 4.2 强化流程

1. 玩家打开强化界面（GUI 或命令）
2. 放入装备和强化石
3. 可选放入保护道具（防爆符/防降符/幸运石）
4. 确认强化
5. 系统计算成功率并判定结果

### 4.3 强化结果

| 结果 | 说明               |
|----|------------------|
| 成功 | 强化等级 +1，属性倍率提升   |
| 失败 | 强化等级 -1（可被防降符阻止） |
| 破碎 | 装备销毁（可被防爆符阻止）    |

### 4.4 命令

```
/sym item enhance get
/sym item enhance set <等级>
```

作用对象是执行者主手物品。

## 5. 套装系统

### 5.1 套装定义

在 `plugins/Symphony/sets/` 目录下创建：

```yaml
# sets/frost_guardian.yml
id: frost_guardian
display_name: "&b霜卫套装"

bonuses:
  2:
    display: "&7(2) &f+15% 冰霜抗性"
    attributes:
      ice_resistance:
        operation: FLAT
        value: 0.15
  3:
    display: "&7(3) &f+20 物理防御 +50 生命值"
    attributes:
      physical_defense:
        operation: FLAT
        value: 20
      max_health:
        operation: FLAT
        value: 50
  4:
    display: "&7(4) &b霜卫之盾"
    attributes:
      ice_resistance:
        operation: FLAT
        value: 0.30
      damage_reduction:
        operation: FLAT
        value: 0.05
    triggers:
      - type: ON_DEFEND
        conditions:
          - type: CHANCE
            value: 20
        actions:
          - type: POTION
            effect: SLOW
            duration: 60
            amplifier: 1
            target: TRIGGER_ATTACKER
          - type: DAMAGE
            amount: 15
            damage_type: ice
            target: TRIGGER_ATTACKER
```

### 5.2 标记物品为套装件

通过命令或 API 在物品 PDC 上写 `set_id`：

```
/sym item set list
/sym item set mark <装备槽> <套装ID>
/sym item set unmark <装备槽>
```

物品 Lore 会自动显示套装信息：

```
&b霜卫套装 (2/4)
&a✔ &7(2) +15% 冰霜抗性
&7✘ &8(3) +20 物理防御 +50 生命值
&7✘ &8(4) 霜卫之盾
```
