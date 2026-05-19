---
layout: home

hero:
  name: Symphony
  text: 把属性玩出花
  tagline: 给 RPG 服务端的可编程属性引擎 — 配置即玩法
  image:
    src: /logo.svg
    alt: Symphony
  actions:
    - theme: brand
      text: 五分钟上手
      link: /guide/01-quick-start
    - theme: alt
      text: 看示例
      link: /guide/03-affix-config

features:
  - icon:
      light: /icons/swords.svg
      dark: /icons/swords-dark.svg
    title: 战斗管道开箱即用
    details: 物理 / 暴击 / 闪避 / 格挡 / 元素 / 吸血 / 反伤 全套已就绪。怪物用 MythicMobs 配，玩家用装备 NBT，自动接通。

  - icon:
      light: /icons/dna.svg
      dark: /icons/dna-dark.svg
    title: 复杂衍生属性
    details: 战斗力 = f(攻击, 防御, 暴击, 元素…) 之类的派生写一个 @derive 函数即可，多层依赖自动按拓扑顺序计算。

  - icon:
      light: /icons/sliders.svg
      dark: /icons/sliders-dark.svg
    title: 状态条件激活
    details: 战斗中才暴击 +30%、潜水时移动加速、血量低于 30% 触发护盾 — 一行 @when 注解搞定。

  - icon:
      light: /icons/sparkles.svg
      dark: /icons/sparkles-dark.svg
    title: 词条 / 触发器 / 技能
    details: 13 种内置 action、20+ 触发条件、MythicMobs 技能双向桥。配出「流血叠 5 层 → 50% 几率冰冻」只要 yml。

  - icon:
      light: /icons/plug.svg
      dark: /icons/plug-dark.svg
    title: 即装即通
    details: PlaceholderAPI 占位符自动注册，MythicMobs mob 配置一段就上属性，ItemsAdder / MMOItems 物品 NBT 直接读取。

  - icon:
      light: /icons/blocks.svg
      dark: /icons/blocks-dark.svg
    title: 18 事件 / 6 类扩展点
    details: 第三方插件可挂三段式伤害管道、注册自定义条件 / 动作 / 反应、订阅属性变更。生态友好。
---

<div class="home-section">

## 一份配置，一个流血词条

```yaml
# plugins/Symphony/affixes/bleed.yml
id: bleed
display_name: "&c流血"
description:
  - "&7攻击时有 {chance}% 概率使目标流血"
max_level: 3
rarity: RARE

levels:
  1:
    chance: 10
    damage: 5
  2:
    chance: 20
    damage: 12
  3:
    chance: 30
    damage: 25

triggers:
  - type: ON_ATTACK
    conditions:
      - type: CHANCE
        value: "{chance}"
      - type: COOLDOWN
        value: 2000
    actions:
      - type: STATUS_STACK
        status: bleeding
        stacks: 1
      - type: DAMAGE
        amount: "{damage}"
```

</div>

<div class="home-section home-section-muted">

## MythicMobs 怪物属性，写在 mob yml 里

```yaml
BanditBoss:
  Type: ZOMBIE
  Display: '&c山贼头目'
  Health: 200
  Symphony:
    attributes:
      physical_damage: 40
      physical_defense: 20
      critical_chance: 15%
      fire_resistance: 30%
    affixes:
      - bleed
      - id: fire_aura
        level: 2
```

无需 Symphony 命令，无需重载。怪物一生成自动套属性、自动挂词条。

</div>

<div class="home-section">

## 装上之后的 5 分钟

<div class="home-steps">

1. 拖 `Symphony.jar` 到 `plugins/`，启动服务器
2. `/sym menu` — 玩家看到自己所有属性
3. 改 `scripts/attributes/combat/physical_damage.aria` 数值，`/sym reload` 立即生效
4. 给一把武器加 NBT 属性，玩家拿起来 `/sym explain physical_damage` 看完整流水线
5. 写一个 mob 配置加 `Symphony:` 段，召唤一只测试

</div>
</div>

<div class="home-section home-section-muted">

## 跟谁配合

| 你已有的插件 | Symphony 怎么接 |
|------------|----------------|
| **MythicMobs** | mob yml 写 `Symphony:` 段；同时注册了 `symphony_damage / heal / buff` 三个 mechanic 给 MM skill 用 |
| **PlaceholderAPI** | `%symphony_attribute_xxx%` `%symphony_when_in_combat%` 等占位符自动可用 |
| **MMOItems / ItemsAdder** | 自定义 `IAttributeProvider` 读对应物品的 NBT，几十行代码即可接通 |
| **Vault / 经济插件** | 用属性当货币系数，监听 `AttributeUpdateEvent` 即可 |

</div>
