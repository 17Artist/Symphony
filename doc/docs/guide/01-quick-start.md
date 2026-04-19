# 快速上手

## 1. 安装

1. 将 `Symphony.jar` 放入服务器 `plugins/` 目录
2. 启动服务器，Symphony 会自动生成默认配置
3. 根据需要修改配置后执行 `/sym reload`

### 依赖

- 必须：Blink 运行时（首次启动自动下载）
- 可选：PlaceholderAPI（变量占位符）、MythicMobs（技能桥接）

### 目录结构

```
plugins/Symphony/
├── config.yml                # 全局配置
├── config/
│   ├── level.yml             # 等级与经验公式
│   ├── enhancement.yml       # 强化倍率表
│   └── lore-format.yml       # 物品 Lore 模板
├── affixes/                  # 词条定义
│   ├── fire_strike.yml
│   └── ...
├── affix-pools/              # 词条池（随机生成表）
│   ├── weapon_pool.yml
│   └── ...
├── skills/                   # 技能定义
│   ├── fire_burst.yml
│   └── script/               # Aria 脚本技能
│       └── chain_lightning.yml
├── runes/                    # 符文定义
│   ├── berserker.yml
│   └── ...
├── sets/                     # 套装定义
│   ├── dragon_slayer.yml
│   └── ...
├── scripts/                  # Aria 脚本
│   ├── formulas/
│   ├── skills/
│   └── modules/
└── data/                     # 玩家数据（自动生成）
    └── players/
```

## 2. 全局配置

```yaml
# config.yml
debug: false

# 自动保存间隔（秒）
auto-save-interval: 300

# 存储类型：yaml / sqlite / mysql
storage-type: yaml

# 等级上限
max-level: 100

# 脱战时间（毫秒）
combat-timeout: 10000

# 连击超时（毫秒）
combo-timeout: 3000

advanced:
  interaction-enabled: true
  element-enabled: true
  resonance-enabled: true
  talent-enabled: true
  status-enabled: true
  environment-enabled: true

performance:
  # 只对 isAsync=true 的 Provider 采集走异步线程池
  async-recalc: false
```

## 3. 第一个词条

创建 `plugins/Symphony/affixes/sharp_blade.yml`：

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

# 被动属性（始终生效）
passive_attributes:
  physical_damage:
    operation: FLAT
    value: "{damage}"
```

这是一个最简单的被动词条，只提供固定的攻击力加成。

## 4. 第一个触发词条

创建 `plugins/Symphony/affixes/thunder_strike.yml`：

```yaml
id: thunder_strike
display_name: "雷霆一击"
description:
  - "&b攻击时有 {chance}% 概率造成 {damage} 点雷电伤害"
max_level: 3
rarity: UNCOMMON
category: weapon

levels:
  1:
    chance: 10
    damage: 15
  2:
    chance: 15
    damage: 25
  3:
    chance: 20
    damage: 40

triggers:
  - type: ON_ATTACK
    conditions:
      - type: CHANCE
        value: "{chance}"
      - type: COOLDOWN
        value: 2000
    actions:
      - type: DAMAGE
        amount: "{damage}"
        damage_type: lightning
        target: TRIGGER_TARGET
      - type: PARTICLE
        particle: ELECTRIC_SPARK
        count: 15
        target: TRIGGER_TARGET
      - type: SOUND
        sound: "entity.lightning_bolt.thunder"
        volume: 0.5
        pitch: 1.5
```

## 5. 给物品添加词条

```
/sym affix add <玩家> thunder_strike 2
```

会把玩家主手物品上挂一条 2 级「雷霆一击」。操作对象是主手物品的 NBT，不是背包某格。

## 6. 查看属性

```
/sym attribute list <玩家>
/sym attribute get <玩家> physical_damage
/sym debug <玩家>
```

## 7. 下一步

- [属性配置](02-attribute-config.md) — 了解所有属性和自定义属性
- [词条配置](03-affix-config.md) — 编写更复杂的词条
- [触发器参考](04-trigger-reference.md) — 所有触发器和条件的完整列表
- [技能提供者指南](05-skill-provider-guide.md) — 配置技能和脚本技能
- [成长系统配置](06-growth-config.md) — 等级/宝石/符文/强化/套装
- [Aria 脚本示例](07-script-examples.md) — 脚本编写示例
