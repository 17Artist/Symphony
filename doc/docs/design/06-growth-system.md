# 成长系统设计

## 1. 概述

成长系统包含 5 个子系统，它们共同构成角色的养成体系：

| 子系统  | 说明                 | 属性来源优先级 |
|------|--------------------|---------|
| 等级系统 | 经验升级，每级提供基础属性成长    | 100     |
| 宝石系统 | 镶嵌到装备槽位，提供固定属性加成   | 300     |
| 符文系统 | 收集碎片激活，提供被动效果和触发效果 | 400     |
| 强化系统 | 强化装备提升基础属性倍率       | 500     |
| 套装系统 | 穿戴同套装多件装备激活套装效果    | 550     |

各子系统通过独立的 AttributeProvider 汇入属性计算管线（LevelProvider/GemProvider/RuneProvider/EnhanceProvider/SetProvider）。

## 2. 等级系统

### 2.1 配置

```yaml
# config/level.yml
level:
  max_level: 100
  
  # 升级经验公式（Aria 脚本）
  exp_formula: |
    val.level = args[0]
    return math.floor(100 * math.pow(level, 1.5) + level * 50)
  
  # 每级属性成长
  attribute_growth:
    max_health:
      base: 20
      per_level: 2.0
      # 可选：使用 Aria 公式替代线性成长
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
  
  # 经验来源配置
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
    quest_reward:
      enabled: true
    command:
      enabled: true
  
  # 升级特效
  effects:
    sound: "entity.player.levelup"
    particle: "VILLAGER_HAPPY"
    title:
      main: "&6&l升级!"
      sub: "&e等级 {old_level} → {new_level}"
      fade_in: 10
      stay: 40
      fade_out: 10
```

### 2.2 核心逻辑

```kotlin
class LevelManager {
    fun addExp(player: Player, amount: Long, source: String) {
        val data = PlayerDataManager.getData(player.uniqueId)
        val bonusMultiplier = 1.0 + SymphonyAPI.getAttribute(player, "exp_bonus")
        val finalAmount = (amount * bonusMultiplier).toLong()
        
        data.exp += finalAmount
        
        // 检查升级
        while (data.exp >= getRequiredExp(data.level) && data.level < config.maxLevel) {
            data.exp -= getRequiredExp(data.level)
            data.level++
            onLevelUp(player, data.level - 1, data.level)
        }
    }
    
    private fun onLevelUp(player: Player, oldLevel: Int, newLevel: Int) {
        // 触发 ON_LEVEL_UP 触发器
        TriggerDispatcher.dispatch(TriggerType.ON_LEVEL_UP, player) {
            set("oldLevel", oldLevel)
            set("newLevel", newLevel)
        }
        // 重算属性
        AttributeCalculator.markDirty(player)
        // 播放特效
        playLevelUpEffects(player, oldLevel, newLevel)
    }
}
```

## 3. 宝石系统

### 3.1 宝石定义

```yaml
# gems/ruby.yml
id: ruby
display_name: "&c红宝石"
description:
  - "&7镶嵌后增加物理攻击力"
max_level: 5
material: REDSTONE
custom_model_data: 2001

levels:
  1:
    attributes:
      physical_damage:
        operation: FLAT
        value: 5
    lore: "&c+5 物理攻击力"
  2:
    attributes:
      physical_damage:
        operation: FLAT
        value: 10
      critical_chance:
        operation: FLAT
        value: 0.02
    lore: "&c+10 物理攻击力 &e+2% 暴击率"
  3:
    attributes:
      physical_damage:
        operation: FLAT
        value: 18
      critical_chance:
        operation: FLAT
        value: 0.05
    lore: "&c+18 物理攻击力 &e+5% 暴击率"
  4:
    attributes:
      physical_damage:
        operation: FLAT
        value: 28
      critical_chance:
        operation: FLAT
        value: 0.08
      critical_damage:
        operation: FLAT
        value: 0.1
    lore: "&c+28 物理攻击力 &e+8% 暴击率 +10% 暴击伤害"
  5:
    attributes:
      physical_damage:
        operation: FLAT
        value: 40
      critical_chance:
        operation: FLAT
        value: 0.12
      critical_damage:
        operation: FLAT
        value: 0.2
    lore: "&c+40 物理攻击力 &e+12% 暴击率 +20% 暴击伤害"

# 宝石合成
synthesis:
  enabled: true
  count: 3                    # 3 个同级合成下一级
  success_rate: 1.0
  failure_action: "keep"      # keep（保留原石）| destroy（销毁）| downgrade（降级）
```

### 3.2 宝石槽

装备上的宝石槽数据存储在 PDC 中：

```json
{
  "slots": [
    { "index": 0, "gem_id": "ruby", "gem_level": 2 },
    { "index": 1, "gem_id": null },
    { "index": 2, "locked": true }
  ]
}
```

宝石槽操作：
- 镶嵌：将宝石物品放入空槽
- 拆卸：取出已镶嵌的宝石（可配置是否消耗道具）
- 解锁：使用解锁道具开启锁定槽位

### 3.3 宝石合成（规划中）

> ⚠️ 宝石合成功能当前处于规划阶段，尚未实现。以下为设计规范。

```yaml
# config/gem-synthesis.yml
synthesis:
  default_count: 3
  default_success_rate: 1.0
  
  # 合成 UI
  gui:
    title: "&8宝石合成"
    size: 27
    input_slots: [10, 11, 12]
    output_slot: 15
    confirm_slot: 22
```

## 4. 符文系统

### 4.1 符文定义

```yaml
# runes/berserker.yml
id: berserker
display_name: "&4狂战士符文"
description:
  - "&7生命值越低，攻击力越高"
max_level: 3
category: combat
icon:
  material: BLAZE_POWDER
  custom_model_data: 3001

# 激活条件
activation:
  type: FRAGMENT
  fragments_required:
    1: 10
    2: 25
    3: 50

# 被动属性（始终生效）
passive_attributes:
  1:
    physical_damage:
      operation: PERCENT
      value: 0.05
  2:
    physical_damage:
      operation: PERCENT
      value: 0.10
    attack_speed:
      operation: PERCENT
      value: 0.05
  3:
    physical_damage:
      operation: PERCENT
      value: 0.15
    attack_speed:
      operation: PERCENT
      value: 0.10
    critical_chance:
      operation: FLAT
      value: 0.05
# 触发效果
triggers:
  - type: ON_LOW_HEALTH
    conditions:
      - type: HEALTH_BELOW
        value: 30
      - type: COOLDOWN
        value: 30000
    actions:
      - type: ATTRIBUTE_BUFF
        attribute: physical_damage
        operation: PERCENT
        value: "0.1 * {level}"
        duration: 10000
      - type: PARTICLE
        particle: VILLAGER_ANGRY
        count: 10
      - type: MESSAGE
        message: "&4&l狂战士之怒已激活!"
        type: actionbar
```

### 4.2 符文碎片

符文碎片是激活符文的材料，可通过以下方式获取：
- 击杀怪物掉落
- 副本奖励
- 任务奖励
- 命令给予

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
```

## 5. 强化系统

### 5.1 配置

```yaml
# config/enhancement.yml
enhancement:
  max_level: 15
  
  levels:
    1:
      multiplier: 1.05
      success_rate: 0.95
      destroy_rate: 0.00
    2:
      multiplier: 1.10
      success_rate: 0.90
      destroy_rate: 0.00
    3:
      multiplier: 1.15
      success_rate: 0.85
      destroy_rate: 0.00
    4:
      multiplier: 1.20
      success_rate: 0.80
      destroy_rate: 0.00
    5:
      multiplier: 1.30
      success_rate: 0.70
      destroy_rate: 0.00
    6:
      multiplier: 1.40
      success_rate: 0.60
      destroy_rate: 0.02
    7:
      multiplier: 1.50
      success_rate: 0.50
      destroy_rate: 0.05
    8:
      multiplier: 1.65
      success_rate: 0.40
      destroy_rate: 0.08
    9:
      multiplier: 1.80
      success_rate: 0.30
      destroy_rate: 0.10
    10:
      multiplier: 2.00
      success_rate: 0.20
      destroy_rate: 0.15
    11:
      multiplier: 2.20
      success_rate: 0.15
      destroy_rate: 0.20
    12:
      multiplier: 2.50
      success_rate: 0.10
      destroy_rate: 0.25
    13:
      multiplier: 2.80
      success_rate: 0.08
      destroy_rate: 0.30
    14:
      multiplier: 3.20
      success_rate: 0.05
      destroy_rate: 0.35
    15:
      multiplier: 3.50
      success_rate: 0.03
      destroy_rate: 0.40
  on_failure:
    downgrade: true
    downgrade_levels: 1
  
  # 强化材料
  materials:
    basic_stone:
      display_name: "&f强化石"
      material: FLINT
      custom_model_data: 4001
      applicable_levels: [1, 10]
    advanced_stone:
      display_name: "&a高级强化石"
      material: FLINT
      custom_model_data: 4002
      applicable_levels: [6, 15]
  
  # 保护道具
  protection_items:
    anti_destroy:
      display_name: "&c防爆符"
      material: PAPER
      custom_model_data: 4010
      effect: "prevent_destroy"
    anti_downgrade:
      display_name: "&e防降符"
      material: PAPER
      custom_model_data: 4011
      effect: "prevent_downgrade"
    luck_stone:
      display_name: "&a幸运石"
      material: EMERALD
      custom_model_data: 4012
      effect: "success_rate_bonus"
      value: 0.10

> 保护道具匹配支持 `"MATERIAL"` 或 `"MATERIAL:CMD"` 格式。例如 `"PAPER:4010"` 匹配 Material=PAPER 且 CustomModelData=4010。
> 强化成功率计算公式：`effectiveRate = baseRate + successBonus + luck * 0.01`，其中 luck 为玩家 `luck` 属性值。
  
  # 成功率公式（可自定义）
  success_rate_formula: |
    val.baseRate = args[0]
    val.luck = args[1]
    val.bonusRate = args[2]
    return math.min(1.0, baseRate + luck * 0.01 + bonusRate)
  
  # 特效
  effects:
    success_sound: "entity.player.levelup"
    success_particle: "VILLAGER_HAPPY"
    failure_sound: "entity.villager.no"
    failure_particle: "SMOKE_NORMAL"
    destroy_sound: "entity.item.break"
    destroy_particle: "EXPLOSION_NORMAL"
```

### 5.2 强化逻辑

```kotlin
class EnhanceManager {
    fun enhance(player: Player, item: ItemStack, protections: List<ItemStack>): EnhanceResult {
        val currentLevel = getEnhanceLevel(item)
        if (currentLevel >= config.maxLevel) return EnhanceResult.MAX_LEVEL
        
        val levelConfig = config.levels[currentLevel + 1] ?: return EnhanceResult.MAX_LEVEL
        
        // 计算成功率
        val luck = SymphonyAPI.getAttribute(player, "luck")
        val bonusRate = calculateProtectionBonus(protections)
        val successRate = formulaEngine.evaluate("enhance_success", 
            levelConfig.successRate, luck, bonusRate)
        
        val random = Random.nextDouble()
        
        return when {
            random < successRate -> {
                // 成功
                setEnhanceLevel(item, currentLevel + 1)
                AttributeCalculator.markDirty(player)
                EnhanceResult.SUCCESS
            }
            random < successRate + levelConfig.destroyRate && !hasProtection(protections, "prevent_destroy") -> {
                // 破碎
                EnhanceResult.DESTROYED
            }
            else -> {
                // 失败
                if (config.onFailure.downgrade && !hasProtection(protections, "prevent_downgrade")) {
                    val newLevel = maxOf(0, currentLevel - config.onFailure.downgradeLevels)
                    setEnhanceLevel(item, newLevel)
                    AttributeCalculator.markDirty(player)
                }
                EnhanceResult.FAILURE
            }
        }
    }
}
```

## 6. 套装系统

### 6.1 套装定义

```yaml
# sets/dragon_slayer.yml
id: dragon_slayer
display_name: "&6屠龙者套装"

# 套装件数 → 效果
bonuses:
  2:
    display: "&7(2) &f+10% 物理攻击力"
    attributes:
      physical_damage:
        operation: PERCENT
        value: 0.10
  3:
    display: "&7(3) &f+15% 暴击伤害"
    attributes:
      critical_damage:
        operation: FLAT
        value: 0.15
  4:
    display: "&7(4) &6屠龙之怒"
    attributes:
      physical_damage:
        operation: PERCENT
        value: 0.25
      fire_damage:
        operation: FLAT
        value: 30
    triggers:
      - type: ON_ATTACK
        conditions:
          - type: CHANCE
            value: 15
        actions:
          - type: SKILL
            provider: symphony
            skill: dragon_flame
            level: 1
```

### 6.2 套装检测

套装检测在装备变更时触发：

```kotlin
class SetDetector {
    fun detectSets(player: Player): Map<String, Int> {
        val setPieces = mutableMapOf<String, Int>()
        
        // 遍历装备栏
        for (slot in EquipmentSlot.values()) {
            val item = player.inventory.getItem(slot) ?: continue
            val setId = getSetId(item) ?: continue
            setPieces[setId] = (setPieces[setId] ?: 0) + 1
        }
        
        // 检查主手
        val mainHand = player.inventory.itemInMainHand
        val mainSetId = getSetId(mainHand)
        if (mainSetId != null) {
            setPieces[mainSetId] = (setPieces[mainSetId] ?: 0) + 1
        }
        
        return setPieces
    }
}
```

### 6.3 套装 Lore 渲染

```
&6屠龙者套装 (2/4)
&a✔ &7(2) +10% 物理攻击力
&a✔ &7(3) +15% 暴击伤害        ← 未激活时显示灰色
&7✘ &8(4) 屠龙之怒
```
