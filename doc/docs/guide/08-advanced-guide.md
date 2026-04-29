# 高级系统使用指南

## 1. 属性交互网络

属性交互让属性之间产生化学反应，而不是孤立的数字堆叠。

### 快速上手

在 `scripts/interactions/` 目录下创建 `.aria` 文件：

```aria
// scripts/interactions/my_interactions.aria

// 每 10 点力量 → +5 最大生命值
symphony.interaction.register({
    'id': 'str_to_hp',
    'type': 'CONVERSION',
    'source': 'strength',
    'target': 'max_health',
    'ratio': 0.5
})

// 暴击率溢出转化为暴击伤害
symphony.interaction.register({
    'id': 'crit_overflow',
    'type': 'OVERFLOW',
    'source': 'critical_chance',
    'target': 'critical_damage',
    'threshold': 1.0,
    'ratio': 0.5
})
```

### 交互类型速查

| 类型           | 用途             | 关键参数                                             |
|--------------|----------------|--------------------------------------------------|
| `CONVERSION` | A 按比例转化为 B     | source, target, ratio                            |
| `OVERFLOW`   | A 超过阈值后溢出转化为 B | source, target, threshold, ratio                 |
| `THRESHOLD`  | A 达到阈值时激活效果    | source, threshold, effect                        |
| `SYNERGY`    | 多属性同时达标时互相增幅   | attributes, threshold, bonus                     |
| `CONFLICT`   | 两属性互斥衰减        | attribute_a, attribute_b, threshold_a, penalty_b |
| `AMPLIFY`    | 满足条件时属性获得倍率    | target, condition, multiplier                    |
| `DIMINISH`   | 递减收益曲线         | source, curve (Aria 函数)                          |

## 2. 元素反应系统

### 基本流程

1. 攻击附带元素 → 目标被附着元素 Aura
2. 第二种元素命中已有 Aura 的目标 → 触发反应
3. 反应产生倍率伤害 / 控制效果 / AOE 扩散

### 默认反应表

| 触发 | 底层 | 反应    | 类型    | 效果               |
|----|----|-------|-------|------------------|
| 火  | 水  | 蒸发    | 倍率    | 伤害 ×2.0          |
| 水  | 火  | 蒸发(逆) | 倍率    | 伤害 ×1.5          |
| 火  | 冰  | 融化    | 倍率    | 伤害 ×2.0          |
| 冰  | 火  | 融化(逆) | 倍率    | 伤害 ×1.5          |
| 雷  | 冰  | 超导    | 减防    | 目标物防 -40%，AOE 伤害 |
| 雷  | 水  | 感电    | 持续AOE | 每秒跳伤，传播水元素       |
| 冰  | 水  | 冻结    | 控制    | 冻结 3 秒           |
| 风  | 任意 | 扩散    | 传播    | 将元素传播给范围内敌人      |

### 自定义反应

```aria
// scripts/mechanics/reactions.aria 中追加

symphony.element.registerReaction({
    'id': 'crystallize',
    'display_name': '结晶',
    'trigger': 'earth',
    'aura': '*',
    'type': 'SHIELD',
    'effect': -> {
        val.caster = args[0]
        val.em = symphony.attribute.get(caster, 'elemental_mastery')
        val.shield = 50 + em * 2
        symphony.entity.addShield(caster, shield, 8000)
    }
})
```

## 3. 词条共鸣

### 配置共鸣

在 `resonances/` 目录下创建 YAML 文件：

```yaml
# resonances/ice_master.yml
id: ice_master
display_name: "&b&l冰霜大师"
description:
  - "&7集齐 3 个冰系词条"
  - "&b冰霜伤害 +25%，冻结持续时间 +50%"

condition:
  type: AFFIX_TAG_COUNT
  tag: "ice"
  count: 3

effects:
  attributes:
    ice_damage:
      operation: PERCENT
      value: 0.25
  special:
    freeze_duration_bonus: 0.5
```

### 给词条添加标签

在词条定义中添加 `tags` 字段：

```yaml
# affixes/frost_blade.yml
id: frost_blade
display_name: "霜刃"
tags: ["ice", "weapon"]              # ← 标签
# ...
```

## 4. 天赋门

天赋门让属性成为解锁机制的钥匙，而不仅仅是数字。

```aria
// scripts/talents/my_talents.aria

symphony.talent.register({
    'id': 'mana_shield',
    'display_name': '法力护盾',
    'description': '最大法力 ≥ 200 时：受到伤害时消耗法力抵消 30% 伤害',
    'gate': { 'attribute': 'max_mana', 'threshold': 200, 'operator': '>=' },
    'effect': -> {
        val.entity = args[0]
        symphony.trigger.registerDynamic(entity, {
            'id': 'mana_shield_trigger',
            'type': 'ON_DEFEND',
            'actions': [{
                'type': 'SCRIPT',
                'code': |
                    val.damage = server.trigger_damage
                    val.absorb = damage * 0.3
                    val.mana = symphony.entity.getMana(server.trigger_victim)
                    if (mana >= absorb) {
                        symphony.entity.costMana(server.trigger_victim, absorb)
                        server.trigger_damage = damage * 0.7
                    }
            }]
        })
    }
})
```

## 5. 状态层

### 定义状态层

```aria
// scripts/mechanics/status_layers.aria

symphony.status.register({
    'id': 'poison_stack',
    'display_name': '剧毒',
    'max_stacks': 5,
    'stack_duration': 10000,
    'per_stack': -> {
        return 3 + args[1] * 2        // 每层每秒 3 + 层数×2 伤害
    },
    'tick_interval': 1000,
    'damage_type': 'poison',
    'on_max_stacks': -> {
        val.target = args[0]
        symphony.entity.addPotion(target, 'WEAKNESS', 100, 1)
        symphony.entity.addPotion(target, 'SLOW', 100, 1)
        symphony.status.clearStacks(target, 'poison_stack')
    }
})
```

### 在词条中使用

```yaml
actions:
  - type: STATUS_STACK
    status: "poison_stack"
    stacks: 1
    target: TRIGGER_TARGET
```

## 6. 环境修正

环境修正让战斗场景变得有意义。

```aria
// scripts/mechanics/environment.aria

// 沙漠生物群系：火焰增强，水系减弱
symphony.environment.registerModifier({
    'id': 'desert_heat',
    'display_name': '沙漠酷热',
    'condition': -> {
        val.biome = symphony.world.getBiome(args[0])
        return biome == 'desert' || biome == 'badlands'
    },
    'attributes': {
        'fire_damage': { 'operation': 'PERCENT', 'value': 0.20 },
        'water_damage': { 'operation': 'PERCENT', 'value': -0.20 }
    }
})
```

## 7. 全部关闭

只想要传统属性插件的行为？在 `config.yml` 里一股脑关掉：

```yaml
advanced:
  interaction-enabled: false
  element-enabled: false
  resonance-enabled: false
  talent-enabled: false
  status-enabled: false
  environment-enabled: false
```

或者直接删掉 `scripts/mechanics/`、`scripts/interactions/`、`scripts/talents/`、`resonances/` 几个目录，插件找不到配置就不加载。
