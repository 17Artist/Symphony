# SymphonyRunes

`SymphonyRunes` 是一个完整可运行的外置符文系统。它不属于 Symphony 本体，目的是展示特殊玩法插件如何使用公共属性定义、稳定来源、等级快照和等级事件接入 Symphony。

默认玩法闭环是“管理员授予符文 → 玩家装备到槽位 → 属性来源生效 → 等级变化时自动暂停或恢复 → 重启恢复”。本模块没有物理符文物品和 GUI；正式服可把授予与装备入口替换成 Overture 物品、任务、掉落或自有界面。

## 构建与安装

在 Symphony 仓库根目录执行：

```powershell
./gradlew.bat :example:runes:build
```

成品位于 `example/runes/build/libs/`。插件名为 `SymphonyRunes`，硬依赖 `Symphony`，对 `SymphonyLevel` 声明软依赖。任何正确注册的 `LevelProvider` 都能替代 `SymphonyLevel`。

首次启动生成：

```text
plugins/SymphonyRunes/
├─ config.yml
├─ attributes.yml
├─ language.yml
├─ runes/
│  ├─ ember_sigil.yml
│  ├─ glacial_ward.yml
│  └─ prismatic_convergence.yml
└─ players/
```

## 槽位配置

`config.yml` 定义稳定槽位及其可接受类别：

```yaml
definition-priority: 100

slots:
  offense:
    display-name: '进攻符文槽'
    accepts:
      - offense
      - elemental

  defense:
    display-name: '守护符文槽'
    accepts:
      - defense

  wildcard:
    display-name: '通用符文槽'
    accepts:
      - '*'
```

一个槽位可接受多个类别；`*` 表示任意类别。相同符文不能同时占用两个槽位。每个槽位对应固定来源 `AttributeSourceKey("symphonyrunes", "slot/<槽位ID>")`，换符文时执行整来源替换，不会重复叠加旧 modifier。

## 符文定义

每个 `runes/*.yml` 定义一枚符文：

```yaml
id: ember_sigil
display:
  name: '余烬刻印'
  description:
    - '提高火焰伤害与火焰增幅。'
    - '等级越高，增幅成长越明显。'
category: elemental
maximum-rank: 5
minimum-level:
  base: 1
  per-rank: 4
modifiers:
  - id: fire-damage
    attribute: symphony:fire_damage
    operation: add
    value:
      base: 5.0
      per-rank: 2.0

  - id: resonance
    attribute: symphonyrunes:rune_resonance
    operation: add
    value:
      base: 3.0
      per-rank: 1.0
```

第 N 阶的数值为 `base + per-rank × (N - 1)`，等级要求采用同一阶级增量。默认的 4 阶余烬刻印要求 Lv.13，提供 11 点火焰伤害、5% 火焰增幅和 6 点符文共鸣。

`attributes.yml` 展示外部系统注册自有属性定义。未写 namespace 的 key 归属于插件，即 `symphonyrunes:rune_resonance`。插件拒绝替其它 namespace 注册定义，也拒绝未知属性、重复符文 ID、非法阶级和无法被任何槽位接纳的类别。

## 等级联动

当符文存在等级要求时：

- 没有任何等级提供者返回快照：拒绝新装备，已记录槽位暂停来源；
- 当前等级不足：保留装备记录，但移除该槽位来源；
- 收到 `LevelChangeEvent` 且等级重新满足：自动恢复来源，不需要再次装备；
- 没有等级要求的符文不依赖等级提供者。

## 命令

| 命令                                   | 权限                     | 用途                |
|--------------------------------------|------------------------|-------------------|
| `/symrune show [玩家]`                 | 无                      | 显示每个槽位的生效、暂停或错误状态 |
| `/symrune inspect <符文ID>`            | 无                      | 显示定义、阶级成长和属性显示名   |
| `/symrune equip <槽位> <符文ID>`         | 玩家执行                   | 装备已解锁且满足条件的符文     |
| `/symrune unequip <槽位>`              | 玩家执行                   | 卸下槽位符文            |
| `/symrune grant <玩家> <符文ID> [阶级]`    | `symrune.admin.grant`  | 授予符文或提高拥有阶级       |
| `/symrune revoke <玩家> <符文ID>`        | `symrune.admin.revoke` | 收回符文并清理占用槽位       |
| `/symrune equipfor <玩家> <槽位> <符文ID>` | `symrune.admin.equip`  | 管理员代为装备，仍执行全部校验   |
| `/symrune reload`                    | `symrune.admin.reload` | 校验候选定义并刷新在线玩家     |

`/srune` 是根命令别名。命令提示、类别名、运算名和状态名都来自 `language.yml`。

## 数据与重载

玩家文件只保存拥有阶级与槽位分配：

```yaml
schema-version: 1
unlocked:
  ember_sigil: 4
equipped:
  offense: ember_sigil
```

写入使用同目录临时文件原子替换。重载会先解析完整候选目录、注册候选属性并验证全部引用；失败时恢复旧定义与旧来源。删除槽位后，旧槽位对应的稳定来源会被清理。

