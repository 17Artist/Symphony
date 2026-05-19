# 高级系统使用指南

Symphony 的高级系统（属性交互、元素反应、天赋、状态层、环境修正、词条共鸣）全部通过 YAML 配置加载，插件启动时扫描对应目录。下面每一节都给出"配置目录 + 字段清单 + 可直接使用的示例"。

## 1. 属性交互网络

属性交互让属性之间产生化学反应，而不是孤立的数字堆叠。

### 配置目录

`plugins/Symphony/interactions/` — `ConfigLoader.loadInteractions` 会扫描该目录下所有 `.yml`。

### 字段清单

| 字段            | 说明                            |
|---------------|-------------------------------|
| `id`          | 唯一标识                          |
| `type`        | 交互类型（见下表）                     |
| `description` | 提示文本，用于 GUI 展示                |
| `source`      | 单源属性（CONVERSION / OVERFLOW 等） |
| `target`      | 目标属性                          |
| `threshold`   | 阈值                            |
| `ratio`       | 转化比例                          |
| `bonus`       | 加成倍率（SYNERGY 用）               |
| `attributes`  | 属性列表（SYNERGY 用）               |
| `attribute_a` | 第一属性（CONFLICT 用）              |
| `attribute_b` | 第二属性（CONFLICT 用）              |
| `penalty_b`   | B 属性衰减量（CONFLICT 用）           |
| `multiplier`  | AMPLIFY 用倍率                   |

### 交互类型速查

| 类型           | 用途             | 关键参数                                    |
|--------------|----------------|-----------------------------------------|
| `CONVERSION` | A 按比例转化为 B     | source, target, ratio                   |
| `OVERFLOW`   | A 超过阈值后溢出转化为 B | source, target, threshold, ratio        |
| `THRESHOLD`  | A 达到阈值时激活效果    | source, threshold                       |
| `SYNERGY`    | 多属性同时达标时互相增幅   | attributes, threshold, bonus            |
| `CONFLICT`   | 两属性互斥衰减        | attribute_a, attribute_b, penalty_b     |
| `AMPLIFY`    | 满足条件时属性获得倍率    | target, multiplier                      |
| `DIMINISH`   | 递减收益曲线         | source, ratio                           |

### 示例

```yaml
# interactions/str_to_hp.yml
# 每 1 点力量 → +0.5 最大生命值
id: str_to_hp
type: CONVERSION
description: "每 1 点力量转化为 0.5 最大生命值"
source: strength
target: max_health
ratio: 0.5
```

```yaml
# interactions/crit_overflow.yml
# 暴击率溢出转化为暴击伤害
id: crit_overflow
type: OVERFLOW
description: "暴击率超过 100% 时，每 1% 溢出转化为 0.5% 暴击伤害"
source: critical_chance
target: critical_damage
threshold: 1.0
ratio: 0.5
```

```yaml
# interactions/str_vit_synergy.yml
# 力量与体力同时达标 → 两者各 +10%
id: str_vit_synergy
type: SYNERGY
description: "物理攻击力 > 50 且 最大生命值 > 50 时，两者各 +10%"
attributes:
  - physical_damage
  - max_health
threshold: 50
bonus: 0.1
```

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

在 `plugins/Symphony/reactions/` 目录下放置 `.yml` 文件，`ConfigLoader.loadReactions` 会自动加载。

可用的 `type` 取值：`AMPLIFY` / `DEBUFF` / `DOT_AOE` / `CONTROL` / `SPREAD`。

```yaml
# reactions/overload.yml
# 自定义一个"超载"反应：火 + 雷 → 爆炸伤害
id: overload
display_name: "超载"
trigger: fire          # 触发元素（后命中的那一种）
aura: lightning        # 底层 Aura（目标身上原先附着的元素）
type: AMPLIFY          # 倍率伤害
multiplier: 2.5
gauge_consume: 0.5     # 消耗多少底层 Aura（0~1）
particle: EXPLOSION_NORMAL
sound: entity.generic.explode
radius: 2.0            # 附带小范围 AOE
```

```yaml
# reactions/overload_reverse.yml
# 也可以单文件配反转（反过来触发时的独立倍率）
id: overload
display_name: "超载"
trigger: fire
aura: lightning
type: AMPLIFY
multiplier: 2.5
reverse:
  id: overload_reverse
  multiplier: 1.5      # 雷后命中火的反向反应
```

`on_tick` 字段可选，接收一段 Aria 脚本作为 DOT_AOE 类反应的每跳回调。

## 3. 词条共鸣

### 配置共鸣

在 `resonances/` 目录下创建 YAML 文件（`ConfigLoader.loadResonances` 加载）：

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

天赋门让属性成为解锁机制的钥匙，而不仅仅是数字。配置目录：`plugins/Symphony/talents/`（`ConfigLoader.loadTalents`）。

### 字段清单

| 字段                   | 说明                                    |
|----------------------|---------------------------------------|
| `id`                 | 唯一标识                                  |
| `display_name`       | 显示名                                   |
| `description`        | 描述                                    |
| `icon`               | 图标物品（Material 枚举名）                    |
| `gate`               | 解锁门 — `type: SINGLE/AND/OR` + 条件      |
| `passive_attributes` | 解锁后获得的被动属性                            |
| `effect`             | 可选，解锁瞬间的 Aria 脚本（`args[0] = 实体`）      |
| `on_deactivate`      | 可选，失活瞬间的 Aria 脚本                      |

### 单条件示例

```yaml
# talents/immovable.yml
id: immovable
display_name: "不动如山"
description: "最大生命值 ≥ 1000 时解锁：击退抗性 +80%"
icon: SHIELD

gate:
  type: SINGLE
  attribute: max_health
  threshold: 1000
  operator: ">="

passive_attributes:
  knockback_resistance:
    operation: FLAT
    value: 0.8
```

### 带激活特效的示例

```yaml
# talents/precise_strike.yml
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
    value: 0.2

# 解锁/失活时的提示（Aria 脚本，args[0] = 玩家）
effect: |
  val.entity = args[0]
  symphony.effect.actionbar(entity, '&e&l精准打击 &7已激活')
on_deactivate: |
  val.entity = args[0]
  symphony.effect.actionbar(entity, '&7精准打击 已失效')
```

### 多条件门（AND / OR）

```yaml
gate:
  type: AND
  conditions:
    - attribute: max_health
      threshold: 800
      operator: ">="
    - attribute: defense
      threshold: 100
      operator: ">="
```

## 5. 状态层

状态层定义在 `plugins/Symphony/statuses/` 目录（`ConfigLoader.loadStatuses`）。

### YAML 方式（推荐）

```yaml
# statuses/poison_stack.yml
id: poison_stack
display_name: "剧毒"
icon: SPIDER_EYE
max_stacks: 5
stack_duration: 10000         # 单层持续时间（毫秒）
decay_mode: INDIVIDUAL         # INDIVIDUAL 独立衰减 / REFRESH 整体刷新
tick_interval: 1000
damage_type: poison
per_stack_damage_ratio: 0.05   # 每层造成最大生命值 5% 的伤害

# 每层叠加的属性（可选）
per_stack_attributes:
  move_speed:
    operation: PERCENT
    value: -0.05               # 每层 -5% 移速

# 达到最大层数时触发的 Aria 脚本（args[0] = 目标）
on_max_stacks: |
  val.target = args[0]
  symphony.effect.sound(target, 'entity.witch.drink', 1.0, 1.0)
  symphony.status.clearStacks(target, 'poison_stack')
```

### 脚本方式（运行时动态注册）

Bridge 也支持在 Aria 脚本中用字典形式注册，适合动态流程：

```aria
// scripts/mechanics/status_layers.aria

symphony.status.register({
    'id': 'poison_stack',
    'display_name': '剧毒',
    'max_stacks': 5,
    'stack_duration': 10000,
    'tick_interval': 1000,
    'damage_type': 'poison',
    'per_stack_damage_ratio': 0.05,
    'on_max_stacks': |
        val.target = args[0]
        symphony.status.clearStacks(target, 'poison_stack')
})
```

### 在词条中叠加状态

```yaml
actions:
  - type: STATUS_STACK
    status: "poison_stack"
    stacks: 1
    target: TRIGGER_TARGET
```

## 6. 环境修正

环境修正让战斗场景变得有意义。配置目录：`plugins/Symphony/environments/`（`ConfigLoader.loadEnvironments`）。

### 字段清单

通用字段：

| 字段            | 说明                                             |
|---------------|------------------------------------------------|
| `id`          | 唯一标识                                           |
| `display_name`| 显示名                                            |
| `type`        | 环境类型（见下表）                                      |
| `attributes`  | 触发时生效的属性修正                                     |
| `description` | 描述                                             |
| `condition`   | 可选，Aria 表达式（变量名 `entity`），写了就优先于声明式字段使用        |

类型相关字段（按类型选填，不写 `condition` 时必须用对应字段才会生效）：

| 字段                 | 适用类型           | 说明                                              |
|--------------------|----------------|-------------------------------------------------|
| `dimension`        | DIMENSION      | 维度名（NORMAL / NETHER / THE_END）                  |
| `biomes`           | BIOME          | 生物群系名列表（如 `[DESERT, BADLANDS]`）                  |
| `time_start`       | TIME           | 起始 tick（0-23999）                                |
| `time_end`         | TIME           | 结束 tick（支持跨 24000 边界，如 23000 → 1000）            |
| `weather`          | WEATHER        | `CLEAR` / `RAIN` / `THUNDER` / `STORM`          |
| `min_y` / `max_y`  | ALTITUDE       | Y 坐标区间（端点都含），未设端点时不限制                          |
| `require_outdoor`  | TIME / WEATHER | 是否要求实体在户外（默认 false）                             |

`type` 可选值：

| 类型            | 触发时机                                  |
|---------------|---------------------------------------|
| `DIMENSION`   | 在特定维度（主世界 / 下界 / 末地）                  |
| `BIOME`       | 在特定生物群系                               |
| `WEATHER`     | 天气事件（晴天 / 雨 / 雷暴）                     |
| `TIME`        | 时间段（白天 / 夜晚）                          |
| `ALTITUDE`    | 海拔区间                                  |
| `IN_WATER`    | 角色在水中                                 |

### 示例：沙漠酷热（BIOME 声明式）

```yaml
# environments/desert_heat.yml
id: desert_heat
display_name: "沙漠酷热"
type: BIOME
biomes:
  - DESERT
  - BADLANDS
description: "在沙漠或恶地：火焰伤害 +20%，水系伤害 -20%"
attributes:
  fire_damage:
    operation: PERCENT
    value: 0.20
  water_damage:
    operation: PERCENT
    value: -0.20
```

### 示例：夜晚阴影（TIME 区间）

```yaml
# environments/night_shadow.yml
id: night_shadow
display_name: "暗夜之力"
type: TIME
time_start: 13000
time_end: 23000
description: "夜晚（13000-23000 tick）：暗影伤害 +15%"
attributes:
  shadow_damage:
    operation: PERCENT
    value: 0.15
```

### 示例：用 condition 写复杂条件

如果声明式字段不够用，可以写 Aria 表达式：

```yaml
# 仅在沙漠且在户外白天才生效
id: desert_noon
display_name: "正午烈日"
type: BIOME
condition: |
  symphony.world.getBiome(entity) == 'DESERT'
    && symphony.world.getTime(entity) > 6000
    && symphony.world.getTime(entity) < 12000
attributes:
  fire_damage:
    operation: PERCENT
    value: 0.30
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

或者直接删掉 `plugins/Symphony/` 下的 `interactions/`、`talents/`、`resonances/`、`reactions/`、`environments/`、`statuses/` 这几个顶级目录，插件扫不到配置就不会加载对应系统。
