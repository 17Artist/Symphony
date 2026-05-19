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

### 1.3 YAML 配置

交互定义统一走 `interactions/*.yml`，由 `ConfigLoader.loadInteractions` 装配到 `InteractionNetwork`。同一份文件可以声明多条关系，或者一个文件一条，按项目约定来就行。字段列表：

| 字段            | 适用类型                                    | 说明                                    |
|---------------|-----------------------------------------|---------------------------------------|
| `id`          | 全部                                      | 唯一标识                                  |
| `type`        | 全部                                      | 上表中的 7 种枚举之一                          |
| `source`      | OVERFLOW / CONVERSION / THRESHOLD       | 源属性                                   |
| `target`      | OVERFLOW / CONVERSION / AMPLIFY         | 目标属性                                  |
| `threshold`   | OVERFLOW / THRESHOLD / SYNERGY          | 触发阈值                                  |
| `ratio`       | OVERFLOW / CONVERSION                   | 转化比例                                  |
| `bonus`       | SYNERGY                                 | 协同加成（百分比，0.10 = +10%）                 |
| `attributes`  | SYNERGY                                 | 参与协同的属性列表                             |
| `attribute_a` | CONFLICT                                | 触发方属性                                 |
| `attribute_b` | CONFLICT                                | 被削弱属性                                 |
| `threshold_a` | CONFLICT                                | 触发方阈值                                 |
| `penalty_b`   | CONFLICT                                | 被削弱属性的削减比例                            |
| `multiplier`  | AMPLIFY / CONVERSION / DIMINISH 等       | 效果倍率（语义视 type 而定）                    |
| `description` | 全部                                      | 用于 UI 展示                              |

```yaml
# interactions/crit_overflow.yml — 溢出转化
id: crit_overflow
type: OVERFLOW
source: critical_chance
target: critical_damage
threshold: 1.0          # 100% 暴击率以上才溢出
ratio: 0.5              # 1% 暴击率溢出 → 0.5% 暴击伤害
description: "暴击率超过 100% 时，溢出部分转化为暴击伤害"
```

```yaml
# interactions/int_to_mana.yml — 持续转化
id: int_to_mana
type: CONVERSION
source: intelligence
target: max_mana
ratio: 5.0              # 每 1 点智力 → 5 点法力
description: "每点智力提供 5 点最大法力"
```

```yaml
# interactions/str_vit_synergy.yml — 协同增幅
id: str_vit_synergy
type: SYNERGY
attributes:
  - strength
  - vitality
threshold: 50           # 两者同时 > 50 才激活
bonus: 0.10             # 各 +10%
description: "力量和体力同时超过 50 时，两者各 +10%"
```

```yaml
# interactions/heavy_armor_dodge_conflict.yml — 互斥衰减
id: heavy_armor_dodge_conflict
type: CONFLICT
attribute_a: physical_defense
attribute_b: dodge
threshold_a: 100        # 防御 > 100 时触发
penalty_b: 0.30         # 闪避率 -30%
description: "重甲防御超过 100 时，闪避率降低 30%"
```

```yaml
# interactions/berserker_rage.yml — 条件增幅
id: berserker_rage
type: AMPLIFY
target: physical_damage
multiplier: 1.5
description: "生命值低于 30% 时，物理攻击力 ×1.5"
```

THRESHOLD 和 AMPLIFY 如果需要条件判断或副作用，可以在 YAML 里追加 `condition` 或 `effect` 字段，内容是 Aria 代码片段，`ConfigLoader` 会把它编译到 `AriaCallbackManager`（key：`interaction:<id>:condition` / `interaction:<id>:effect`）。脚本里读 `args[0]` 拿到实体，保持和其他系统的回调约定一致。

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

实体可以被「附着」元素状态，持续一定时间。附着有「量」的概念（类似原神的元素量）。元素池、默认附着时长、最大附着数、衰减速率等参数由 `ElementSystem` 在内部维护，脚本和配置只需要调用附着 API：

```aria
// 给目标附着 1.0 量的火元素
symphony.element.applyAura(target, 'fire', 1.0)

// 查询已有的元素附着
val.aura = symphony.element.getAura(target, 'fire')

// 主动消除某个元素附着
symphony.element.removeAura(target, 'fire')

// 列出目标身上的所有元素附着
val.all = symphony.element.getAllAuras(target)
```

### 2.3 反应定义

元素反应通过 `reactions/*.yml` 配置，由 `ConfigLoader.loadReactions` 装配到 `ReactionSystem`。`ReactionType` 枚举包含：`AMPLIFY`（倍率反应）、`DEBUFF`（减益反应）、`DOT_AOE`（持续范围伤害）、`CONTROL`（控制反应）、`SPREAD`（扩散反应）。字段说明：

| 字段              | 说明                                       |
|-----------------|------------------------------------------|
| `id`            | 反应唯一标识                                   |
| `display_name`  | 显示名                                      |
| `trigger`       | 触发元素（主动命中的元素）                            |
| `aura`          | 底层元素（目标身上已附着的元素），`*` 表示匹配任意              |
| `type`          | 上述反应类型枚举                                 |
| `multiplier`    | 效果倍率（视 type 语义）                          |
| `gauge_consume` | 消耗的元素量（默认 0.5）                           |
| `particle`      | 反应触发时的粒子效果                               |
| `sound`         | 反应触发时的音效                                 |
| `radius`        | 作用半径（DOT_AOE / SPREAD）                   |
| `ticks`         | 持续跳数（DOT_AOE）                            |
| `interval`      | 每跳间隔毫秒（DOT_AOE）                          |
| `reverse`       | 反向反应配置块（`id` + `multiplier`），用于水→火底等对称写法 |
| `on_tick`       | Aria 脚本，DOT_AOE 每跳执行                     |

```yaml
# reactions/vaporize.yml — 蒸发（火 + 水）倍率反应
id: vaporize
display_name: "蒸发"
trigger: fire
aura: water
type: AMPLIFY
multiplier: 2.0
gauge_consume: 0.5
particle: CLOUD
sound: block.fire.extinguish
reverse:
  id: vaporize_reverse
  multiplier: 1.5          # 反向（水触发火底）只有 1.5 倍
```

```yaml
# reactions/melt.yml — 融化（火 + 冰）
id: melt
display_name: "融化"
trigger: fire
aura: ice
type: AMPLIFY
multiplier: 2.0
reverse:
  id: melt_reverse
  multiplier: 1.5
```

```yaml
# reactions/superconduct.yml — 超导（冰 + 雷）减防反应
id: superconduct
display_name: "超导"
trigger: lightning
aura: ice
type: DEBUFF
particle: ELECTRIC_SPARK
sound: entity.lightning_bolt.impact
```

```yaml
# reactions/electro_charged.yml — 感电（水 + 雷）持续 AOE
id: electro_charged
display_name: "感电"
trigger: lightning
aura: water
type: DOT_AOE
ticks: 6
interval: 1000
radius: 3.0
on_tick: |
  val.target = args[0]
  val.damage = 20 + symphony.attribute.get(args[1], 'elemental_mastery') * 1.5
  symphony.entity.damage(target, damage, 'lightning')
```

```yaml
# reactions/frozen.yml — 冻结（水 + 冰）控制反应
id: frozen
display_name: "冻结"
trigger: ice
aura: water
type: CONTROL
particle: SNOWFLAKE
```

```yaml
# reactions/swirl.yml — 扩散（风 + 任意）
id: swirl
display_name: "扩散"
trigger: wind
aura: "*"              # 匹配任意底层元素
type: SPREAD
radius: 5.0
```

在 Aria 脚本中想手动触发一次反应（比如技能或词条 Action 里），可以调用：

```aria
// 主动尝试触发反应：返回反应 ID 或 none
val.reactionId = symphony.element.tryReaction(target, 'fire', 1.0)
```

### 2.4 元素精通属性

新增一个核心属性「元素精通」，影响所有元素反应的效果。属性定义使用 `@attribute` 注解式脚本：

```aria
// scripts/attributes/combat.aria 中追加
@attribute('elemental_mastery')
@displayName('元素精通')
@description('提升元素反应的伤害和效果')
@category('combat')
@default(0.0)
@format('integer')
@priority(25)
class ElementalMastery {}
```

元素精通对反应的加成公式（脚本内常量，反应计算阶段由 `ReactionSystem` 使用）：

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

元素附着可以和「动态环境属性」系统（见 §六）配合：在 `environments/*.yml` 里声明一条 `WEATHER` 或 `IN_WATER` 修正器时，再用一段 Aria 脚本（例如技能的 `on_tick` 或词条的 `ON_TICK` 触发器）周期性地给实体附着对应元素即可。示例思路：

- 雨天 + 户外 → 每 2 秒给实体附 0.3 水元素
- 在水中 → 持续低量水元素附着
- 雪地生物群系 → 低量冰元素附着
- 靠近岩浆 → 低量火元素附着

附着逻辑由脚本调用 `symphony.element.applyAura(entity, element, gauge)` 完成，环境修正器负责在属性面上做加减法（火伤加成、冰抗降低等）。

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

# 激活条件
condition:
  type: AFFIX_TAG_COUNT              # 按词条标签计数
  tag: "fire"                        # 标签名
  count: 3                           # 需要 3 个

# 共鸣效果（当前仅 attributes 会被 ConfigLoader 装配）
effects:
  attributes:
    fire_damage:
      operation: PERCENT
      value: 0.30
```

> 目前 `ConfigLoader.loadResonances` 只读取 `effects.attributes` 一项。若需要「共鸣激活时额外挂触发器/反应加成/质变机制」这类进阶联动，走词条 triggers 或 Aria 脚本回调去实现，避免在共鸣 YAML 里堆叠未被装配的字段。

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
```

```yaml
# resonances/berserker_set.yml
id: berserker_set
display_name: "&4&l狂战士之魂"
description:
  - "&7同时拥有「嗜血」「狂怒」「不屈」三个词条"
  - "&4物理伤害 +40%，生命值上限 +20%"

condition:
  type: AFFIX_ID_SET
  affix_ids: ["bloodthirst", "fury", "unyielding"]

effects:
  attributes:
    physical_damage:
      operation: PERCENT
      value: 0.40
    max_health:
      operation: PERCENT
      value: 0.20
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

天赋门走 `talents/*.yml`，由 `ConfigLoader.loadTalents` 装配到 `TalentManager`。字段：

| 字段                   | 说明                                                                 |
|----------------------|--------------------------------------------------------------------|
| `id`                 | 唯一标识                                                               |
| `display_name`       | 显示名                                                                |
| `description`        | 描述                                                                 |
| `icon`               | 图标 Material 名（默认 `NETHER_STAR`）                                    |
| `gate`               | 解锁条件块：`type` = `SINGLE` 或 `AND`；SINGLE 用 `attribute`/`threshold`/`operator`；AND 用 `conditions: [...]` |
| `passive_attributes` | 激活后挂的被动属性（和词条的 `passive_attributes` 结构一致）                          |
| `effect`             | Aria 脚本，激活时执行一次（key：`talent:<id>:effect`）                          |
| `on_deactivate`      | Aria 脚本，从激活转未激活时执行（key：`talent:<id>:on_deactivate`）                |

```yaml
# talents/precise_strike.yml — 暴击率 50% 阈值
id: precise_strike
display_name: "精准打击"
description: "暴击率 ≥ 50% 时解锁：暴击无视 20% 防御"
icon: DIAMOND_SWORD
gate:
  type: SINGLE
  attribute: critical_chance
  threshold: 0.5
  operator: ">="
passive_attributes:
  penetration:
    operation: FLAT
    value: 0.20       # 解锁后常驻 20% 穿透
```

```yaml
# talents/immovable.yml — 最大生命 ≥ 1000
id: immovable
display_name: "不动如山"
description: "最大生命值 ≥ 1000 时解锁：击退抗性 +80%"
gate:
  type: SINGLE
  attribute: max_health
  threshold: 1000
  operator: ">="
passive_attributes:
  knockback_resistance:
    operation: FLAT
    value: 0.8
effect: |
  # 激活时的额外副作用（可选），比如播放一次音效或广播
  val.entity = args[0]
  symphony.entity.sendMessage(entity, '&6不动如山 已激活')
```

```yaml
# talents/battle_dancer.yml — 多属性 AND 门
id: battle_dancer
display_name: "战舞者"
description: "力量 ≥ 40 且 敏捷 ≥ 40 时解锁：物理伤害 +25%"
gate:
  type: AND
  conditions:
    - attribute: strength
      threshold: 40
      operator: ">="
    - attribute: dexterity
      threshold: 40
      operator: ">="
passive_attributes:
  physical_damage:
    operation: PERCENT
    value: 0.25
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

天赋门的解锁条件可以直接写 `level` 属性，让升级不再只是数字增长，而是解锁新机制的里程碑：

```yaml
# talents/tier1_unlocked.yml
id: tier1_unlocked
display_name: "初级天赋槽解锁"
description: "达到 10 级时，获得一个初级天赋槽位的访问权限"
gate:
  type: SINGLE
  attribute: level
  threshold: 10
  operator: ">="
# 具体槽位的管理由上层 GUI/插件读取该天赋的激活状态后决定
```

天赋的"激活/未激活"状态可以通过 `symphony.talent.isUnlocked(entity, id)` 在脚本里查询，然后驱动自定义的天赋树 GUI 或命令菜单。

## 五、状态层系统（Status Layer System）

### 5.1 核心思想

参考 Lost Ark 异常状态叠层 + PoE2 的 Ailment 系统。引入「层数」概念 — 持续攻击叠加层数，达到阈值触发爆发效果。这让战斗从「一刀一个数字」变成了「持续施压 → 爆发收割」的节奏。

> **递归伤害防护**：状态层 DOT（持续伤害）造成的伤害不会再次触发 Symphony 自定义伤害管线，避免无限递归。由 `StatusDamageGuard` 在 `DamageListener` 中实现。

### 5.2 状态层定义

状态层支持两种定义入口：Aria 脚本调用 `symphony.status.register({...})`，或者放在 `statuses/*.yml` 由 `ConfigLoader.loadStatuses` 装配。下面示范脚本形式，字段名和 YAML 保持一致。

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
    'tick_interval': 1000,             // 每秒结算一次
    'damage_type': 'physical',
    'per_stack_damage_ratio': 0.05,    // 每层每 tick 对攻击者攻击力的 5% 作为伤害

    // 满层爆发（Aria 片段，执行时 args[0] = 目标，args[1] = 施加者）
    'on_max_stacks': -> {
        val.target = args[0]
        val.attacker = args[1]
        val.atk = symphony.attribute.get(attacker, 'physical_damage')

        // 爆发：造成大量伤害 + 清空层数
        symphony.entity.damage(target, atk * 2.0, 'physical')
        symphony.status.clearStacks(target, 'bleed')

        // 特效
        symphony.effect.particle(target, 'DAMAGE_INDICATOR', 30)
        symphony.effect.sound(target, 'entity.player.attack.crit', 1.0, 0.5)
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

    // 每层减速（注意是 per_stack_attributes，复数）
    'per_stack_attributes': {
        'movement_speed': { 'operation': 'PERCENT', 'value': -0.08 }  // 每层 -8% 移速
    },

    // 满层：大额范围冰伤
    'on_max_stacks': -> {
        val.target = args[0]
        val.attacker = args[1]
        val.atk = symphony.attribute.get(attacker, 'ice_damage')
        symphony.entity.damage(target, 80 + atk * 1.5, 'ice')
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

    // 满层：电磁脉冲（AOE）
    'on_max_stacks': -> {
        val.target = args[0]
        val.attacker = args[1]
        val.em = symphony.attribute.get(attacker, 'elemental_mastery')
        val.damage = 80 + em * 1.5

        // AOE 爆发
        val.nearby = symphony.entity.getNearby(target, 4.0)
        nearby.forEach(-> {
            symphony.entity.damage(args[0], damage, 'lightning')
        })
        symphony.status.clearStacks(target, 'electro_charge')
    }
})
```

> 「冻结 / 眩晕」这类硬控效果目前不在 Symphony 的 entity API 里，推荐通过 Bukkit 原生 `PotionEffect`（由外部脚本或插件实现）或后续 API 扩展来实现，`on_max_stacks` 里暂以伤害 + 特效作为占位方案。

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

环境修正器通过 `environments/*.yml` 配置，由 `ConfigLoader.loadEnvironments` 装配到 `EnvironmentSystem`。每条修正器声明一个 `type` 和一组 `attributes` 作为属性修改器。`EnvironmentType` 枚举：

| 类型           | 判定语义                              |
|--------------|-----------------------------------|
| `DIMENSION`  | 所在维度（主世界 / 下界 / 末地等）              |
| `BIOME`      | 所在生物群系                            |
| `TIME`       | 游戏时间段（昼夜区间）                       |
| `WEATHER`    | 天气（雨 / 雷暴）                        |
| `ALTITUDE`   | 高度区间                              |
| `IN_WATER`   | 是否在水中                             |

每种类型有对应的声明式匹配字段（无需写脚本即可使用）。如果声明了可选的 `condition` Aria 表达式，将优先于声明式字段使用，由 `ConfigLoader` 编译为 `environment:<id>:condition`，签名为 `(entity) -> boolean`。

通用字段：

| 字段             | 说明                                          |
|----------------|---------------------------------------------|
| `id`           | 唯一标识                                        |
| `display_name` | 显示名                                         |
| `type`         | 上述枚举之一                                      |
| `attributes`   | 属性修改表，每个条目含 `operation`（FLAT/PERCENT）和 `value` |
| `description`  | 描述文本                                        |
| `condition`    | Aria 表达式（可选），优先级最高                          |

类型相关字段：

| 字段                 | 适用类型           | 说明                                              |
|--------------------|----------------|-------------------------------------------------|
| `dimension`        | DIMENSION      | 维度名（NORMAL / NETHER / THE_END），不区分大小写            |
| `biomes`           | BIOME          | 生物群系名列表（如 `[DESERT, BADLANDS]`），不区分大小写           |
| `time_start`       | TIME           | 起始 tick（含），范围 0-23999                           |
| `time_end`         | TIME           | 结束 tick（含），支持跨越 24000 边界（如 23000 → 1000）        |
| `weather`          | WEATHER        | `CLEAR` / `RAIN` / `THUNDER` / `STORM`          |
| `min_y` / `max_y`  | ALTITUDE       | Y 坐标区间（含端点），未设端点时不限制                            |
| `require_outdoor`  | TIME / WEATHER | 是否要求实体在户外（默认 false）                             |

```yaml
# environments/nether_fire_boost.yml — 下界维度加成
id: nether_fire_boost
display_name: "地狱灼热"
type: DIMENSION
dimension: NETHER
attributes:
  fire_damage:
    operation: PERCENT
    value: 0.25
  fire_resistance:
    operation: FLAT
    value: -0.15
  ice_damage:
    operation: PERCENT
    value: -0.30
description: "地狱维度：火焰伤害 +25%，火焰抗性 -15%，冰霜伤害 -30%"
```

```yaml
# environments/ocean_water_boost.yml — 水中增幅
id: ocean_water_boost
display_name: "深海之力"
type: IN_WATER
attributes:
  lightning_damage:
    operation: PERCENT
    value: 0.20
  fire_damage:
    operation: PERCENT
    value: -0.50
  movement_speed:
    operation: PERCENT
    value: -0.20
```

```yaml
# environments/night_shadow_boost.yml — 夜间加成
id: night_shadow_boost
display_name: "暗夜之力"
type: TIME
time_start: 13000
time_end: 23000
attributes:
  dark_damage:
    operation: PERCENT
    value: 0.20
  dodge:
    operation: FLAT
    value: 0.05
  holy_damage:
    operation: PERCENT
    value: -0.15
```

```yaml
# environments/thunderstorm_lightning.yml — 雷暴加成
id: thunderstorm_lightning
display_name: "雷暴增幅"
type: WEATHER
weather: THUNDER
require_outdoor: true
attributes:
  lightning_damage:
    operation: PERCENT
    value: 0.50
  lightning_resistance:
    operation: FLAT
    value: -0.10
```

```yaml
# environments/high_altitude.yml — 高空加成
id: high_altitude
display_name: "高空稀薄"
type: ALTITUDE
min_y: 200.0
attributes:
  movement_speed:
    operation: PERCENT
    value: 0.10
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

### 6.4 环境指示器

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
| `symphony.resonance.*`   | 词条共鸣（register/getActive/check/list）                                       |
| `symphony.talent.*`      | 天赋门（register/isUnlocked/check/getStatus/list）                             |
| `symphony.status.*`      | 状态层（register/addStacks/getStacks/clearStacks/setImmune/list）              |
| `symphony.environment.*` | 环境系统（register/getActive/list）                                        |
| `symphony.trigger.*`     | 触发器分发（dispatch/isOnCooldown/getCooldown/setCooldown）                      |

### 7.3 配置与脚本目录结构

所有 YAML 配置都是 `plugins/Symphony/` 下的顶级目录，和 `affixes/`、`skills/` 并列；Aria 脚本放在 `scripts/` 下按职责分组。

```
plugins/Symphony/
├── affixes/                  # 词条定义
├── affix-pools/              # 词条池
├── skills/                   # 技能定义
├── sets/                     # 套装
├── runes/                    # 符文
├── gems/                     # 宝石
├── interactions/             # 属性交互网络 [新增]
│   ├── crit_overflow.yml
│   ├── int_to_mana.yml
│   └── berserker_rage.yml
├── resonances/               # 词条共鸣 [新增]
│   ├── fire_mastery.yml
│   ├── elemental_harmony.yml
│   └── berserker_set.yml
├── talents/                  # 天赋门 [新增]
│   ├── precise_strike.yml
│   ├── immovable.yml
│   └── battle_dancer.yml
├── statuses/                 # 状态层 [新增]
│   ├── bleed.yml
│   ├── frostbite.yml
│   └── electro_charge.yml
├── reactions/                # 元素反应 [新增]
│   ├── vaporize.yml
│   ├── melt.yml
│   ├── superconduct.yml
│   ├── electro_charged.yml
│   ├── frozen.yml
│   └── swirl.yml
├── environments/             # 环境修正器 [新增]
│   ├── nether_fire_boost.yml
│   ├── ocean_water_boost.yml
│   ├── night_shadow_boost.yml
│   └── thunderstorm_lightning.yml
├── config/                   # 全局配置（level.yml / enhancement.yml 等）
└── scripts/                  # Aria 脚本
    ├── attributes/
    ├── mechanics/
    │   ├── damage.aria
    │   └── status_layers.aria     # 也可以改写成 YAML 放到 statuses/
    ├── formulas/
    └── modules/                    # 按需 import，不自动加载
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

换一种关法：直接把对应的顶级目录（`interactions/`、`resonances/`、`talents/`、`statuses/`、`reactions/`、`environments/`）清空，注册列表为空，等同于关闭该系统。
