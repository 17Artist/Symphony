# 划时代高级系统设计

## 设计背景

当前 MC 属性插件的共同瓶颈：

| 问题      | 现状                     | 现代 ARPG 的做法                  |
|---------|------------------------|------------------------------|
| 属性是孤立数字 | 攻击力 100 就是 100，属性之间无交互 | PoE：力量每 10 点 +5 生命；D4：智力提供全抗 |
| 元素是数值贴纸 | 火焰伤害 = 额外加一个数字         | 原神：两种元素叠加触发反应，产生倍率/控制        |
| 词条无协同   | 词条各自独立，堆得越多越好          | Lost Ark：铭刻达到阈值激活质变效果        |
| 成长是线性的  | 等级越高数字越大，没有质变          | PoE：天赋树 Keystone 改变玩法规则      |
| 没有状态深度  | 中毒 = 持续掉血，没有层数/叠加/爆发   | Lost Ark/PoE2：异常状态叠层到阈值触发爆发  |
| 环境无意义   | 在哪打都一样                 | 原神：草地上放火扩散，水边雷电感电            |

Symphony 的 6 个划时代系统逐一解决这些问题。

---

## 一、属性交互网络（Attribute Interaction Network）

### 1.1 核心思想

属性不再是孤立的数字，而是一张有向图。属性之间可以定义多种关系，使得 build 构建产生真正的深度。

### 1.2 交互类型

| 交互类型         | 说明                            | 示例                                |
|--------------|-------------------------------|-----------------------------------|
| `OVERFLOW`   | 溢出转化 — 属性超过阈值后，溢出部分按比例转化为另一属性 | 暴击率 > 100% 时，每 1% 溢出转化为 0.5% 暴击伤害 |
| `THRESHOLD`  | 阈值突变 — 属性达到阈值时触发质变效果          | 攻击速度 > 2.0 时解锁「疾风连斩」：每次攻击额外打一刀    |
| `SYNERGY`    | 协同增幅 — 两个属性同时高于某值时，互相增幅       | 力量 > 50 且 体力 > 50 时，两者各 +10%      |
| `CONVERSION` | 持续转化 — A 属性的一定比例持续转化为 B 属性    | 每 10 点智力 → +5 最大法力                |
| `DIMINISH`   | 递减收益 — 属性越高，每点收益越低            | 闪避率：前 30% 线性，30%~60% 半衰，60%+ 极度递减 |
| `CONFLICT`   | 互斥衰减 — 两个属性同时存在时互相削弱          | 重甲防御 和 闪避率 同时存在时，闪避率 -30%         |
| `AMPLIFY`    | 条件增幅 — 满足条件时属性获得额外倍率          | 生命值 < 30% 时，物理攻击力 ×1.5（狂战士）       |

### 1.3 脚本定义

```aria
// scripts/attributes/interactions.aria

// ── 溢出转化 ──
symphony.interaction.register({
    'id': 'crit_overflow',
    'type': 'OVERFLOW',
    'source': 'critical_chance',       // 源属性
    'target': 'critical_damage',       // 目标属性
    'threshold': 1.0,                  // 溢出阈值（100% 暴击率）
    'ratio': 0.5,                      // 转化比例（1% 暴击率溢出 → 0.5% 暴击伤害）
    'description': '暴击率超过 100% 时，溢出部分转化为暴击伤害'
})

// ── 持续转化 ──
symphony.interaction.register({
    'id': 'int_to_mana',
    'type': 'CONVERSION',
    'source': 'intelligence',
    'target': 'max_mana',
    'ratio': 5.0,                      // 每 1 点智力 → 5 点法力
    'description': '每点智力提供 5 点最大法力'
})

// ── 阈值突变 ──
symphony.interaction.register({
    'id': 'attack_speed_double_strike',
    'type': 'THRESHOLD',
    'source': 'attack_speed',
    'threshold': 2.0,
    'effect': -> {
        // 解锁效果：注册一个隐藏的触发器
        val.entity = args[0]
        symphony.trigger.registerDynamic(entity, {
            'type': 'ON_ATTACK',
            'conditions': [{ 'type': 'CHANCE', 'value': 30 }],
            'actions': [{
                'type': 'DAMAGE',
                'amount': 'trigger_damage * 0.5',
                'damage_type': 'physical',
                'target': 'TRIGGER_TARGET'
            }]
        })
    },
    'description': '攻击速度 ≥ 2.0 时：30% 概率触发额外一击（50% 伤害）'
})

// ── 协同增幅 ──
symphony.interaction.register({
    'id': 'str_vit_synergy',
    'type': 'SYNERGY',
    'attributes': ['strength', 'vitality'],
    'threshold': 50,                   // 两者都 > 50 时激活
    'bonus': 0.10,                     // 各 +10%
    'description': '力量和体力同时超过 50 时，两者各 +10%'
})

// ── 互斥衰减 ──
symphony.interaction.register({
    'id': 'heavy_armor_dodge_conflict',
    'type': 'CONFLICT',
    'attribute_a': 'physical_defense',
    'attribute_b': 'dodge',
    'threshold_a': 100,                // 防御 > 100 时触发
    'penalty_b': 0.30,                // 闪避率 -30%
    'description': '重甲防御超过 100 时，闪避率降低 30%'
})

// ── 条件增幅 ──
symphony.interaction.register({
    'id': 'berserker_rage',
    'type': 'AMPLIFY',
    'target': 'physical_damage',
    'condition': -> {
        val.entity = args[0]
        val.hp = symphony.entity.getHealth(entity)
        val.maxHp = symphony.attribute.get(entity, 'max_health')
        return hp / maxHp < 0.3
    },
    'multiplier': 1.5,
    'description': '生命值低于 30% 时，物理攻击力 ×1.5'
})
```

### 1.4 计算管线集成

```
普通属性叠加计算完毕
    ↓
执行交互网络：
    ├── CONVERSION：源属性值 × ratio → 加入目标属性的 FLAT 修改器
    ├── OVERFLOW：max(0, 源属性值 - threshold) × ratio → 加入目标属性
    ├── SYNERGY：检查多属性是否同时达标 → 各加 bonus%
    ├── CONFLICT：检查互斥条件 → 扣减 penalty
    ├── DIMINISH：应用递减曲线函数
    ├── AMPLIFY：检查条件 → 应用倍率
    └── THRESHOLD：检查阈值 → 激活/关闭效果
    ↓
派生属性计算
    ↓
clamp + 缓存
```

交互网络在每次属性重算时执行，可能需要多轮迭代（因为 CONVERSION 的结果可能触发另一个 THRESHOLD）。设置最大迭代次数（默认 3）防止无限循环。

## 二、元素反应系统（Elemental Reaction System）

### 2.1 核心思想

参考原神的元素反应机制。当两种不同元素在同一目标上叠加时，触发化学反应，产生远超简单加法的效果。这让元素属性从「额外数字」变成了「战术选择」。

### 2.2 元素附着（Elemental Aura）

实体可以被「附着」元素状态，持续一定时间。附着有「量」的概念（类似原神的元素量）：

```aria
// scripts/mechanics/element_aura.aria

// 注册元素附着系统
symphony.element.registerAura({
    'elements': ['fire', 'ice', 'lightning', 'poison', 'holy', 'dark', 'water', 'wind'],
    'default_duration': 8000,          // 默认附着持续 8 秒
    'default_gauge': 1.0,              // 默认元素量 1.0
    'max_auras': 2,                    // 同时最多 2 种元素附着
    'decay_rate': 0.125                // 每秒衰减 0.125 元素量
})
```

### 2.3 反应定义

```aria
// scripts/mechanics/reactions.aria

// ── 蒸发（火 + 水）— 倍率反应 ──
symphony.element.registerReaction({
    'id': 'vaporize',
    'display_name': '蒸发',
    'trigger': 'fire',                 // 触发元素
    'aura': 'water',                   // 底层元素
    'type': 'AMPLIFY',                 // 倍率型反应
    'multiplier': 2.0,                 // 触发攻击伤害 ×2.0
    'gauge_consume': 0.5,              // 消耗 0.5 元素量
    'reverse': {                       // 反向反应（水触发火底）
        'id': 'vaporize_reverse',
        'multiplier': 1.5              // 反向只有 1.5 倍
    },
    'particle': 'CLOUD',
    'sound': 'block.fire.extinguish'
})

// ── 融化（火 + 冰）— 倍率反应 ──
symphony.element.registerReaction({
    'id': 'melt',
    'display_name': '融化',
    'trigger': 'fire',
    'aura': 'ice',
    'type': 'AMPLIFY',
    'multiplier': 2.0,
    'reverse': { 'id': 'melt_reverse', 'multiplier': 1.5 }
})

// ── 超导（冰 + 雷）— 减防反应 ──
symphony.element.registerReaction({
    'id': 'superconduct',
    'display_name': '超导',
    'trigger': 'lightning',
    'aura': 'ice',
    'type': 'DEBUFF',
    'effect': -> {
        val.target = args[0]
        val.triggerLevel = args[1]     // 触发者的元素精通
        // 降低目标物理防御 40%，持续 12 秒
        symphony.attribute.buff(target, 'physical_defense', 'PERCENT', -0.4, 12000)
        // 范围 AOE 伤害
        val.nearby = symphony.entity.getNearbyHostile(target, 3.0)
        val.damage = 50 + triggerLevel * 2
        nearby.forEach(-> {
            symphony.entity.damage(args[0], damage, 'lightning')
        })
    },
    'particle': 'ELECTRIC_SPARK',
    'sound': 'entity.lightning_bolt.impact'
})

// ── 感电（水 + 雷）— 持续 AOE ──
symphony.element.registerReaction({
    'id': 'electro_charged',
    'display_name': '感电',
    'trigger': 'lightning',
    'aura': 'water',
    'type': 'DOT_AOE',                 // 持续范围伤害
    'ticks': 6,                        // 跳 6 次
    'interval': 1000,                  // 每秒一跳
    'damage_per_tick': -> {
        val.triggerLevel = args[0]
        return 20 + triggerLevel * 1.5
    },
    'radius': 3.0,
    'transfers_aura': true             // 感电会把水元素传播给范围内敌人
})

// ── 冻结（水 + 冰）— 控制反应 ──
symphony.element.registerReaction({
    'id': 'frozen',
    'display_name': '冻结',
    'trigger': 'ice',
    'aura': 'water',
    'type': 'CONTROL',
    'effect': -> {
        val.target = args[0]
        val.duration = 3000 + args[1] * 100  // 基础 3 秒 + 元素精通加成
        symphony.entity.freeze(target, duration)
        symphony.effect.particle(target, 'SNOWFLAKE', 30)
    }
})

// ── 扩散（风 + 任意元素）— 传播反应 ──
symphony.element.registerReaction({
    'id': 'swirl',
    'display_name': '扩散',
    'trigger': 'wind',
    'aura': '*',                       // 匹配任意元素
    'type': 'SPREAD',
    'radius': 5.0,
    'effect': -> {
        val.target = args[0]
        val.auraElement = args[1]      // 被扩散的元素类型
        val.triggerLevel = args[2]
        // 将元素附着传播给范围内所有敌人
        val.nearby = symphony.entity.getNearbyHostile(target, 5.0)
        nearby.forEach(-> {
            symphony.element.applyAura(args[0], auraElement, 0.5, 6000)
            symphony.entity.damage(args[0], 30 + triggerLevel, auraElement)
        })
    }
})
```

### 2.4 元素精通属性

新增一个核心属性「元素精通」，影响所有元素反应的效果：

```aria
// scripts/attributes/combat.aria 中追加
symphony.attribute.register({
    'id': 'elemental_mastery',
    'display_name': '元素精通',
    'description': '提升元素反应的伤害和效果',
    'category': 'combat',
    'default_value': 0.0,
    'format': 'integer',
    'priority': 25
})
```

元素精通对反应的加成公式（脚本可配）：

```aria
// 倍率反应加成：元素精通 100 → +15% 反应倍率
val.amplifyBonus = -> {
    val.em = args[0]
    return 2.78 * em / (em + 1400)
}

// 转化反应加成：元素精通 100 → +40% 反应伤害
val.transformBonus = -> {
    val.em = args[0]
    return 16.0 * em / (em + 2000)
}
```

### 2.5 与 Minecraft 环境联动

```aria
// scripts/mechanics/environment.aria

// 雨天：所有户外实体自动附着水元素
symphony.environment.register({
    'id': 'rain_water_aura',
    'condition': -> {
        val.entity = args[0]
        val.world = symphony.entity.getWorld(entity)
        return symphony.world.isRaining(world) && symphony.entity.isOutdoor(entity)
    },
    'effect': -> {
        val.entity = args[0]
        symphony.element.applyAura(entity, 'water', 0.3, 2000)
    },
    'interval': 40                     // 每 2 秒检查一次
})

// 站在岩浆旁：附着火元素
// 站在水中：附着水元素
// 雪地生物群系：附着冰元素
```

## 三、词条共鸣系统（Affix Resonance System）

### 3.1 核心思想

参考 Lost Ark 铭刻系统 + PoE 词缀协同。词条不再是独立的效果贴纸，当玩家身上的词条满足特定组合条件时，激活「共鸣」— 一种远超单个词条之和的质变效果。

这创造了 build 构建的深度：玩家需要思考词条之间的搭配，而不是无脑堆最高数值。

### 3.2 共鸣定义

```yaml
# resonances/fire_mastery.yml
id: fire_mastery
display_name: "&c&l火焰精通"
description:
  - "&7集齐 3 个火系词条时激活"
  - "&c所有火焰伤害 +30%"
  - "&c火焰反应倍率 +0.5"

# 激活条件
condition:
  type: AFFIX_TAG_COUNT              # 按词条标签计数
  tag: "fire"                        # 标签名
  count: 3                           # 需要 3 个

# 共鸣效果
effects:
  attributes:
    fire_damage:
      operation: PERCENT
      value: 0.30
  reactions:
    fire_amplify_bonus: 0.5          # 火焰倍率反应额外 +0.5
  triggers:
    - type: ON_ATTACK
      conditions:
        - type: CHANCE
          value: 10
      actions:
        - type: SCRIPT
          code: |
            // 火焰精通特效：攻击时 10% 概率引爆目标身上的火元素
            val.target = server.trigger_victim
            val.aura = symphony.element.getAura(target, 'fire')
            if (aura != none) {
                val.damage = aura.gauge * 100
                symphony.entity.damage(target, damage, 'fire')
                symphony.element.removeAura(target, 'fire')
                symphony.effect.particle(target, 'EXPLOSION_LARGE', 3)
            }
```

### 3.3 共鸣条件类型

| 条件类型                   | 说明           | 参数                      |
|------------------------|--------------|-------------------------|
| `AFFIX_TAG_COUNT`      | 指定标签的词条数量    | tag, count              |
| `AFFIX_ID_SET`         | 指定词条 ID 组合   | affix_ids (列表)          |
| `AFFIX_RARITY_COUNT`   | 指定稀有度的词条数量   | rarity, count           |
| `AFFIX_CATEGORY_COUNT` | 指定类别的词条数量    | category, count         |
| `AFFIX_LEVEL_SUM`      | 词条等级之和       | min_sum                 |
| `MULTI_TAG`            | 多标签组合        | tags (map: tag → count) |
| `SCRIPT`               | Aria 脚本自定义条件 | code                    |

### 3.4 高级共鸣示例

```yaml
# resonances/elemental_harmony.yml
id: elemental_harmony
display_name: "&6&l元素和谐"
description:
  - "&7同时拥有 3 种不同元素的词条时激活"
  - "&6元素精通 +100"
  - "&6所有元素反应冷却 -50%"

condition:
  type: MULTI_TAG
  tags:
    "fire": 1
    "ice": 1
    "lightning": 1
  mode: DISTINCT                     # 需要 3 种不同元素，不是 3 个同元素

effects:
  attributes:
    elemental_mastery:
      operation: FLAT
      value: 100
  special:
    reaction_cooldown_reduction: 0.5
```

```yaml
# resonances/berserker_set.yml
id: berserker_set
display_name: "&4&l狂战士之魂"
description:
  - "&7同时拥有「嗜血」「狂怒」「不屈」三个词条"
  - "&4生命值低于 50% 时攻击力翻倍"
  - "&4击杀回复 20% 最大生命值"

condition:
  type: AFFIX_ID_SET
  affix_ids: ["bloodthirst", "fury", "unyielding"]

effects:
  interactions:
    - type: AMPLIFY
      target: physical_damage
      condition: "health_below_50"
      multiplier: 2.0
  triggers:
    - type: ON_KILL
      actions:
        - type: HEAL
          amount: "max_health * 0.2"
          target: SELF
```

### 3.5 共鸣 UI 提示

当玩家接近激活共鸣时（比如已有 2/3 个火系词条），物品 Lore 和 ActionBar 会提示：

```
&8&m──────── &6共鸣 &8&m────────
&c火焰精通 &7[2/3] &8未激活
  &7再获得 1 个火系词条即可激活
&6元素和谐 &7[火✔ 冰✔ 雷✘] &8未激活
&8&m────────────────────────
```

## 四、属性阈值与天赋门（Threshold & Talent Gate）

### 4.1 核心思想

参考 PoE 天赋树的 Keystone 节点 + D4 巅峰系统。属性不再是「数字越大越好」的线性增长，而是在特定阈值处产生质变 — 解锁全新的机制或改变玩法规则。

这是 MC 属性插件从未触及的领域：**属性的意义不仅是数值，更是解锁机制的钥匙。**

### 4.2 天赋门定义

```aria
// scripts/attributes/talent_gates.aria

// ── 暴击率 50% 阈值：解锁「精准打击」 ──
symphony.talent.register({
    'id': 'precise_strike',
    'display_name': '精准打击',
    'description': '暴击率 ≥ 50% 时解锁：暴击必定无视 20% 防御',
    'icon': 'DIAMOND_SWORD',
    'gate': {
        'attribute': 'critical_chance',
        'threshold': 0.5,
        'operator': '>='
    },
    'effect': -> {
        // 暴击时自动附加穿透
        val.entity = args[0]
        symphony.trigger.registerDynamic(entity, {
            'id': 'precise_strike_trigger',
            'type': 'ON_ATTACK_CRITICAL',
            'actions': [{
                'type': 'ATTRIBUTE_BUFF',
                'attribute': 'penetration',
                'operation': 'FLAT',
                'value': 0.2,
                'duration': 100       // 极短 buff，只影响本次攻击
            }]
        })
    },
    'on_deactivate': -> {
        val.entity = args[0]
        symphony.trigger.removeDynamic(entity, 'precise_strike_trigger')
    }
})

// ── 生命值 1000 阈值：解锁「不动如山」 ──
symphony.talent.register({
    'id': 'immovable',
    'display_name': '不动如山',
    'description': '最大生命值 ≥ 1000 时解锁：受到的击退效果减少 80%，受到致命伤害时有 10% 概率保留 1 点生命',
    'gate': {
        'attribute': 'max_health',
        'threshold': 1000,
        'operator': '>='
    },
    'passive_attributes': {
        'knockback_resistance': { 'operation': 'FLAT', 'value': 0.8 }
    },
    'effect': -> {
        val.entity = args[0]
        symphony.trigger.registerDynamic(entity, {
            'id': 'immovable_cheat_death',
            'type': 'ON_DEATH',
            'conditions': [{ 'type': 'CHANCE', 'value': 10 }, { 'type': 'COOLDOWN', 'value': 60000 }],
            'actions': [{
                'type': 'SCRIPT',
                'code': "symphony.entity.setHealth(server.trigger_victim, 1)\nsymphony.effect.title(server.trigger_victim, '&c&l不动如山', '&7死亡被抵消', 5, 20, 5)"
            }]
        })
    }
})

// ── 多属性组合门：力量 + 敏捷 双修 ──
symphony.talent.register({
    'id': 'battle_dancer',
    'display_name': '战舞者',
    'description': '力量 ≥ 40 且 敏捷 ≥ 40 时解锁：攻击速度加成同时提升物理伤害',
    'gate': {
        'type': 'AND',
        'conditions': [
            { 'attribute': 'strength', 'threshold': 40, 'operator': '>=' },
            { 'attribute': 'dexterity', 'threshold': 40, 'operator': '>=' }
        ]
    },
    'effect': -> {
        // 攻击速度每超过 1.0 的部分，额外提供等量的物理伤害百分比加成
        val.entity = args[0]
        val.atkSpd = symphony.attribute.get(entity, 'attack_speed')
        val.bonus = math.max(0, atkSpd - 1.0)
        symphony.attribute.modify(entity, 'physical_damage', 'PERCENT', bonus, 'talent:battle_dancer')
    }
})
```

### 4.3 天赋门 UI

玩家可以通过命令或 GUI 查看所有天赋门的解锁状态：

```
&8&m──────── &6天赋门 &8&m────────
&a✔ &f精准打击 &7(暴击率 62% / 50%)
&a✔ &f不动如山 &7(生命值 1200 / 1000)
&c✘ &8战舞者 &7(力量 35/40, 敏捷 42/40)
  &7力量还差 5 点
&c✘ &8元素大师 &7(元素精通 80/200)
&8&m────────────────────────
```

### 4.4 与等级系统联动

天赋门可以作为等级系统的「里程碑」，让升级不再只是数字增长：

```aria
// 每 10 级解锁一个天赋槽位
symphony.talent.register({
    'id': 'level_10_slot',
    'display_name': '初级天赋槽',
    'gate': { 'attribute': 'level', 'threshold': 10, 'operator': '>=' },
    'effect': -> {
        // 解锁一个可选天赋槽位，玩家可以从候选列表中选择一个天赋
        symphony.talent.unlockSlot(args[0], 'tier_1')
    }
})
```

## 五、状态层系统（Status Layer System）

### 5.1 核心思想

参考 Lost Ark 异常状态叠层 + PoE2 的 Ailment 系统。引入「层数」概念 — 持续攻击叠加层数，达到阈值触发爆发效果。这让战斗从「一刀一个数字」变成了「持续施压 → 爆发收割」的节奏。

### 5.2 状态层定义

```aria
// scripts/mechanics/status_layers.aria

// ── 流血（物理叠层）──
symphony.status.register({
    'id': 'bleed',
    'display_name': '流血',
    'icon': '&c🩸',
    'max_stacks': 10,
    'stack_duration': 8000,            // 每层持续 8 秒
    'decay_mode': 'INDIVIDUAL',        // 每层独立计时（vs REFRESH = 叠加时刷新所有层）
    
    // 每层效果
    'per_stack': -> {
        val.target = args[0]
        val.stacks = args[1]
        // 每层每秒造成攻击力 5% 的伤害
        val.atk = symphony.attribute.get(server.trigger_attacker, 'physical_damage')
        return atk * 0.05
    },
    'tick_interval': 1000,             // 每秒结算一次
    'damage_type': 'physical',
    
    // 满层爆发
    'on_max_stacks': -> {
        val.target = args[0]
        val.attacker = args[1]
        val.atk = symphony.attribute.get(attacker, 'physical_damage')
        
        // 爆发：消耗所有层数，造成大量伤害
        symphony.entity.damage(target, atk * 2.0, 'physical')
        symphony.status.clearStacks(target, 'bleed')
        
        // 特效
        symphony.effect.particle(target, 'DAMAGE_INDICATOR', 30)
        symphony.effect.sound(target, 'entity.player.attack.crit', 1.0, 0.5)
        
        // 爆发后 3 秒内无法再叠加流血
        symphony.status.setImmune(target, 'bleed', 3000)
    },
    
    // ActionBar 显示
    'display': -> {
        val.stacks = args[0]
        val.max = args[1]
        return '&c🩸 流血 ' + stacks + '/' + max
    }
})

// ── 冰冻叠层 ──
symphony.status.register({
    'id': 'frostbite',
    'display_name': '冻伤',
    'icon': '&b❄',
    'max_stacks': 5,
    'stack_duration': 6000,
    'decay_mode': 'REFRESH',
    
    // 每层减速
    'per_stack_attribute': {
        'movement_speed': { 'operation': 'PERCENT', 'value': -0.08 }  // 每层 -8% 移速
    },
    
    // 满层：冻结
    'on_max_stacks': -> {
        val.target = args[0]
        symphony.entity.freeze(target, 3000)
        symphony.status.clearStacks(target, 'frostbite')
        symphony.effect.particle(target, 'SNOWFLAKE', 50)
        symphony.effect.sound(target, 'block.glass.break', 1.0, 0.3)
    }
})

// ── 电荷叠层 ──
symphony.status.register({
    'id': 'electro_charge',
    'display_name': '电荷',
    'icon': '&e⚡',
    'max_stacks': 8,
    'stack_duration': 5000,
    'decay_mode': 'INDIVIDUAL',
    
    // 满层：电磁脉冲
    'on_max_stacks': -> {
        val.target = args[0]
        val.attacker = args[1]
        val.em = symphony.attribute.get(attacker, 'elemental_mastery')
        val.damage = 80 + em * 1.5
        
        // AOE 爆发
        val.nearby = symphony.entity.getNearbyHostile(target, 4.0)
        nearby.forEach(-> {
            symphony.entity.damage(args[0], damage, 'lightning')
            symphony.entity.stun(args[0], 1000)  // 眩晕 1 秒
        })
        symphony.status.clearStacks(target, 'electro_charge')
    }
})
```

### 5.3 叠层来源

词条和技能可以通过 Action 叠加状态层：

```yaml
# 词条中叠加流血
actions:
  - type: STATUS_STACK
    status: "bleed"
    stacks: 1                          # 叠加 1 层
    target: TRIGGER_TARGET

# 技能中叠加多层
actions:
  - type: STATUS_STACK
    status: "frostbite"
    stacks: 2                          # 一次叠 2 层
    target: ALL_TARGETS
```

### 5.4 状态层 ActionBar 显示

目标身上的状态层会在攻击者的 ActionBar 上显示：

```
&c🩸×7 &b❄×3 &e⚡×5    &f目标: Zombie &c❤ 45/100
```

## 六、动态环境属性（Adaptive Attribute Scaling）

### 6.1 核心思想

属性不是固定的，会根据战斗环境、时间、天气、生物群系、对手类型等因素动态调整。这让「在哪打」和「打什么」变得有意义，而不是所有战斗都是同一套数值。

### 6.2 环境修正器

```aria
// scripts/mechanics/environment_scaling.aria

// ── 生物群系修正 ──
symphony.environment.registerModifier({
    'id': 'nether_fire_boost',
    'display_name': '地狱灼热',
    'condition': -> {
        val.entity = args[0]
        return symphony.world.getDimension(entity) == 'the_nether'
    },
    'attributes': {
        'fire_damage': { 'operation': 'PERCENT', 'value': 0.25 },
        'fire_resistance': { 'operation': 'FLAT', 'value': -0.15 },
        'ice_damage': { 'operation': 'PERCENT', 'value': -0.30 }
    },
    'description': '地狱维度：火焰伤害 +25%，火焰抗性 -15%，冰霜伤害 -30%'
})

symphony.environment.registerModifier({
    'id': 'ocean_water_boost',
    'display_name': '深海之力',
    'condition': -> {
        val.entity = args[0]
        return symphony.entity.isInWater(entity)
    },
    'attributes': {
        'water_damage': { 'operation': 'PERCENT', 'value': 0.40 },
        'lightning_damage': { 'operation': 'PERCENT', 'value': 0.20 },
        'fire_damage': { 'operation': 'PERCENT', 'value': -0.50 },
        'movement_speed': { 'operation': 'PERCENT', 'value': -0.20 }
    }
})

// ── 时间修正 ──
symphony.environment.registerModifier({
    'id': 'night_shadow_boost',
    'display_name': '暗夜之力',
    'condition': -> {
        val.entity = args[0]
        val.world = symphony.entity.getWorld(entity)
        val.time = symphony.world.getTime(world)
        return time >= 13000 && time <= 23000  // 夜晚
    },
    'attributes': {
        'dark_damage': { 'operation': 'PERCENT', 'value': 0.20 },
        'dodge': { 'operation': 'FLAT', 'value': 0.05 },
        'holy_damage': { 'operation': 'PERCENT', 'value': -0.15 }
    }
})

// ── 天气修正 ──
symphony.environment.registerModifier({
    'id': 'thunderstorm_lightning',
    'display_name': '雷暴增幅',
    'condition': -> {
        val.entity = args[0]
        val.world = symphony.entity.getWorld(entity)
        return symphony.world.isThundering(world) && symphony.entity.isOutdoor(entity)
    },
    'attributes': {
        'lightning_damage': { 'operation': 'PERCENT', 'value': 0.50 },
        'lightning_resistance': { 'operation': 'FLAT', 'value': -0.10 }
    }
})

// ── 对手类型修正 ──
symphony.environment.registerCombatModifier({
    'id': 'undead_slayer',
    'display_name': '亡灵克星',
    'condition': -> {
        val.target = args[0]
        return symphony.entity.isUndead(target)
    },
    'attacker_attributes': {
        'holy_damage': { 'operation': 'PERCENT', 'value': 0.30 }
    },
    'description': '对亡灵目标：神圣伤害 +30%'
})

// ── 高度修正 ──
symphony.environment.registerModifier({
    'id': 'high_altitude',
    'display_name': '高空稀薄',
    'condition': -> {
        val.entity = args[0]
        return symphony.entity.getY(entity) > 200
    },
    'attributes': {
        'wind_damage': { 'operation': 'PERCENT', 'value': 0.20 },
        'movement_speed': { 'operation': 'PERCENT', 'value': 0.10 }
    }
})
```

### 6.3 环境修正器的计算时机

环境修正器作为一个特殊的 `AttributeProvider`（优先级 750，在 Buff 之后、脚本之前），每次属性重算时评估条件：

```
属性重算 → 收集所有 Provider
    ↓
EnvironmentProvider（优先级 750）：
    ├── 遍历所有已注册的环境修正器
    ├── 对每个修正器评估 condition(entity)
    ├── 条件为 true → 将 attributes 作为修改器加入
    └── 条件为 false → 跳过
```

### 6.4 战斗修正器

战斗修正器（`registerCombatModifier`）与环境修正器不同，它在伤害计算时评估，而不是属性重算时：

```
伤害计算流程中：
    ├── 获取攻击者属性
    ├── 评估所有 CombatModifier 的 condition(target)
    ├── 满足条件 → 临时应用 attacker_attributes 到本次伤害计算
    └── 不修改缓存中的属性值（只影响本次攻击）
```

### 6.5 环境指示器

玩家进入特殊环境时，ActionBar 显示当前生效的环境修正：

```
&c🔥 地狱灼热 &7| &e⚡ 雷暴增幅 &7| &8🌙 暗夜之力
```

## 七、系统协同总览

### 7.1 六大系统如何协同工作

```
玩家装备一把带有「烈焰打击 Lv.3」「灼烧 Lv.2」「火焰精通 Lv.1」词条的武器

    ↓ 词条共鸣系统检测
    3 个火系词条 → 激活「火焰精通」共鸣 → 火焰伤害 +30%

    ↓ 属性交互网络
    火焰伤害超过 50 → 触发 THRESHOLD「火焰亲和」→ 火焰攻击自动附着火元素

    ↓ 玩家攻击一个站在水中的怪物
    
    ↓ 环境系统
    目标在水中 → 自动附着水元素 Aura
    
    ↓ 触发器系统
    ON_ATTACK → 「烈焰打击」触发 → 造成火焰伤害
    
    ↓ 元素反应系统
    火元素 hit 水元素 Aura → 触发「蒸发」反应 → 伤害 ×2.0
    元素精通 150 → 反应加成 +23% → 实际倍率 2.46
    
    ↓ 状态层系统
    「灼烧」词条叠加 1 层 burn → 目标身上 burn ×3
    
    ↓ 属性阈值系统
    玩家暴击率 62% → 「精准打击」天赋已激活 → 暴击无视 20% 防御
    
    ↓ 最终伤害输出
    基础伤害 × 蒸发倍率 × 暴击倍率 × 环境加成 × 共鸣加成
```

### 7.2 新增 Aria 命名空间汇总

| 命名空间                     | 说明                                                                   |
|--------------------------|----------------------------------------------------------------------|
| `symphony.interaction.*` | 属性交互网络（register/remove/list）                                         |
| `symphony.element.*`     | 元素系统（applyAura/getAura/removeAura/getAllAuras/tryReaction）           |
| `symphony.resonance.*`   | 词条共鸣（register/getActive/check/list）                                  |
| `symphony.talent.*`      | 天赋门（register/isUnlocked/check/getStatus/list）                        |
| `symphony.status.*`      | 状态层（register/addStacks/getStacks/clearStacks/setImmune/list）         |
| `symphony.environment.*` | 环境系统（register/getActive/list）                                        |
| `symphony.world.*`       | 世界信息（getTime/isRaining/isThundering/getDimension/getBiome/isOutdoor） |

### 7.3 新增脚本目录

```
plugins/Symphony/scripts/
├── attributes/               # 属性定义（已有）
├── mechanics/                # 战斗机制（已有，扩展）
│   ├── damage.aria
│   ├── element_aura.aria     # 元素附着系统 [新增]
│   ├── reactions.aria        # 元素反应定义 [新增]
│   ├── status_layers.aria    # 状态层定义 [新增]
│   └── environment.aria      # 环境修正器 [新增]
├── interactions/             # 属性交互网络 [新增]
│   └── default.aria
├── resonances/               # 词条共鸣（YAML 配置）[新增]
│   ├── fire_mastery.yml
│   ├── elemental_harmony.yml
│   └── berserker_set.yml
├── talents/                  # 天赋门 [新增]
│   └── default.aria
├── formulas/
├── skills/
├── conditions/
└── modules/
```

### 7.4 主配置里的开关

所有高级系统的开关都在 `config.yml` 的 `advanced.*` 下。代码里能读到的字段只有 enable 这一批布尔位，其余调优参数走各自系统的脚本内部常量——不希望再搞成嵌套一堆小字段的全局配置文件。

```yaml
# config.yml
advanced:
  interaction-enabled: true
  element-enabled: true
  resonance-enabled: true
  talent-enabled: true
  status-enabled: true
  environment-enabled: true
```

### 7.5 关掉之后会怎样

任一开关关掉后，该系统在启动时就不做 `registerDefaults()`，`tick()` 和相关 Provider 也就没有东西可以发。Symphony 会安静地退化成一个「只有属性引擎 + 词条 + 触发器 + 成长」的传统属性插件，不会报错，也不影响其他开着的系统。

换一种关法：直接把 `scripts/mechanics/` 里对应脚本、或者 `resonances/` 目录清空，注册列表为空，等同于关闭。
