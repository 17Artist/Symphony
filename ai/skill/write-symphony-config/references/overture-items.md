# Overture 物品与 Symphony 组件

## 目录

- [文件布局](#文件布局)
- [展示方案](#展示方案)
- [组件](#组件)
- [分组](#分组)
- [引用检查](#引用检查)

## 文件布局

把物品放在 `Overture/items/<pack>/`，把展示方案放在 `Overture/displays/<pack>.yml`。每个物品 ID 在本包与内置物品中必须唯一。

```yaml
arcane_blade:
  display: my_pack
  icon: DIAMOND_SWORD
  name:
    item_name: '&d奥术之刃'
  lore:
    item_type: '&8[武器]'
    item_desc:
      - '&7凝聚奥术能量。'
  meta:
    unique: true
  components:
    symphony:attributes:
      physical_damage:
        operation: add
        value: 20
```

完整 Overture 字段以当前 Overture 源码和 `symphony-bukkit/src/main/resources/showcase/prismatic-arsenal/overture/` 为准。

## 展示方案

```yaml
my_pack:
  name: '<item_name> <symphony:enhancement>'
  lore:
    - '<item_type>'
    - '<item_desc...>'
    - ''
    - '<symphony:attributes...>'
    - '<symphony:affixes...>'
    - '<symphony:skills...>'
    - '<symphony:sockets...>'
    - '<symphony:offhand...>'
    - '<symphony:enhancement...>'
    - '<symphony:set...>'
```

支持的 Symphony 标签是 `attributes`、`affixes`、`skills`、`sockets`、`offhand`、`enhancement` 和 `set`。标签布局来自 Overture，具体行文来自 Symphony `display.yml`。

## 组件

### 属性

```yaml
symphony:attributes:
  fire_damage:
    operation: add
    value: 12
    priority: 100
    description: '武器提供的火焰伤害'
```

### 套装

```yaml
symphony:set:
  id: elemental_vanguard
  piece: main_hand
  amount: 1
```

`id` 和 `piece` 必须存在于套装定义。外部物品来源也参与套装计数。

### 技能

```yaml
symphony:skills:
  prismatic_burst:
    level: 2
```

技能必须存在，等级必须在定义的 `max-level` 内。

### 孔位

```yaml
symphony:sockets:
  max-extra-slots: 2
  slots:
    - accepts:
        - offense
        - fire
      unlock-at-enhancement: 0
    - accepts:
        - '*'
      unlock-at-enhancement: 5
```

`accepts` 至少一项。`*` 表示通用；具体类别必须与宝石和打孔器设计一致。

### 副手

```yaml
symphony:offhand:
  enabled: true
  attribute-scale: 50%
```

物品级开关只在主配置的 `item-controlled` 模式中决定是否允许副手；比例只缩放数值属性，不会产生半个套装或半个技能。

## 分组

每个 `items/<pack>/` 及其子目录都写 `__group__.yml`：

```yaml
priority: 20
icon: CHEST
name: '&d奥术装备'
lore:
  - '&7武器、宝石与工坊材料'
```

只使用 `priority`、`icon`、`name`、`lore` 四个键。

## 引用检查

- 宝石定义的 `overture-item` 必须是实际物品 ID。
- 打孔器、拆卸道具、词条成本和强化保护道具同样必须存在。
- 每件物品的 `display` 必须在 `Overture/displays/` 中声明。
- 每个 `symphony:*` 组件只能出现一次；禁止把同一组件拆成两个重复键。
- Overture 物品不使用命名空间冒号；Symphony 定义可以使用命名空间。
