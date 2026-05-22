# 触发器参考手册

## 1. 触发器类型完整列表

> **关于"上下文变量"**：表格中列的字段描述了该触发器**意图传递**的语义信息。其中 `attacker` 通常对应 `context.entity`（=自己）、`victim` 对应 `context.target`，这两个角色由触发器类型隐式决定（参见 03 文档的 `TRIGGER_ATTACKER` / `TRIGGER_TARGET` 章节）。其余字段是显式 `set(...)` 注入到 `globalStorage` 的命名 key，可在 SCRIPT action 里通过 `server.trigger_<name>` 读取。
> 真实注入的 key 列在本文末尾"上下文变量在脚本中的访问"章节，**与下表不完全一致**（部分字段是语义指引，未真正写入 storage）。

### 攻击/伤害类

| 触发器                  | 说明      | 上下文变量                                                           |
|----------------------|---------|-----------------------------------------------------------------|
| `ON_ATTACK`          | 攻击实体时   | `attacker` `victim` `damage` `damageType` `isCritical` `weapon` |
| `ON_ATTACK_CRITICAL` | 暴击时     | `attacker` `victim` `damage` `critMultiplier`                   |
| `ON_KILL`            | 击杀实体时   | `killer` `victim` `damage`                                      |
| `ON_DAMAGE`          | 造成最终伤害时 | `attacker` `victim` `finalDamage` `rawDamage`                   |
| `ON_MELEE_ATTACK`    | 近战攻击时   | `attacker` `victim` `damage` `weapon`                           |
| `ON_RANGED_ATTACK`   | 远程攻击时   | `attacker` `victim` `damage` `projectile`                       |

### 防御/受伤类

| 触发器             | 说明      | 上下文变量                                             |
|-----------------|---------|---------------------------------------------------|
| `ON_DEFEND`     | 被攻击时    | `attacker` `victim` `damage` `damageType`         |
| `ON_DAMAGED`    | 受到伤害后   | `attacker` `victim` `finalDamage` `currentHealth` |
| `ON_BLOCK`      | 格挡成功时   | `attacker` `victim` `blockedDamage`               |
| `ON_DODGE`      | 闪避成功时   | `attacker` `victim` `dodgedDamage`                |
| `ON_DEATH`      | 死亡时     | `victim` `killer` `damage`                        |
| `ON_LOW_HEALTH` | 生命值低于阈值 | `entity` `currentHealth` `maxHealth` `threshold`  |

### 移动/交互类

| 触发器              | 说明   | 上下文变量                            |
|------------------|------|----------------------------------|
| `ON_MOVE`        | 移动时  | `entity` `from` `to` `speed`     |
| `ON_JUMP`        | 跳跃时  | `entity` `location`              |
| `ON_SNEAK`       | 潜行切换 | `entity` `isSneaking`            |
| `ON_SPRINT`      | 疾跑切换 | `entity` `isSprinting`           |
| `ON_INTERACT`    | 交互时  | `player` `action` `block` `item` |
| `ON_RIGHT_CLICK` | 右键点击 | `player` `item` `block`          |
| `ON_LEFT_CLICK`  | 左键点击 | `player` `item` `block`          |

### 装备/物品类

| 触发器             | 说明   | 上下文变量                          |
|-----------------|------|--------------------------------|
| `ON_EQUIP`      | 穿戴装备 | `player` `item` `slot`         |
| `ON_UNEQUIP`    | 卸下装备 | `player` `item` `slot`         |
| `ON_HOLD`       | 切换手持 | `player` `item` `previousItem` |
| `ON_CONSUME`    | 消耗物品 | `player` `item`                |
| `ON_BREAK_ITEM` | 物品损坏 | `player` `item`                |

### 周期/状态类

| 触发器               | 说明    | 上下文变量                           |
|-------------------|-------|---------------------------------|
| `ON_TIMER`        | 定时触发  | `entity` `interval` `tickCount` |
| `ON_ENTER_COMBAT` | 进入战斗  | `entity`                        |
| `ON_LEAVE_COMBAT` | 脱离战斗  | `entity` `combatDuration`       |
| `ON_LEVEL_UP`     | 升级    | `player` `oldLevel` `newLevel`  |
| `ON_RESPAWN`      | 重生    | `player`                        |
| `ON_JOIN`         | 加入服务器 | `player`                        |
| `ON_QUIT`         | 退出服务器 | `player`                        |

### 技能/法力类

| 触发器             | 说明   | 上下文变量                              |
|-----------------|------|------------------------------------|
| `ON_SKILL_CAST` | 释放技能 | `caster` `skill` `level` `targets` |
| `ON_SKILL_HIT`  | 技能命中 | `caster` `victim` `skill` `damage` |
| `ON_MANA_USE`   | 消耗法力 | `entity` `amount` `remaining`      |
| `ON_MANA_FULL`  | 法力充满 | `entity`                           |

### ON_TIMER 特殊参数

```yaml
triggers:
  - type: ON_TIMER
    interval: 40              # 每 40 tick（2 秒）触发一次
    conditions: []
    actions:
      - type: HEAL
        amount: 5
        target: SELF
```

> `ON_TIMER` 由调度任务每 20 tick（1 秒）派发一次，词条层级再按 `interval` 字段做模运算节流。
> - `interval` 单位 tick，**实际精度为秒**（dispatcher 每秒派发一次）。例如 `interval: 40` 实际每 2 秒触发一次。
> - 缺省 / 0 / 负值时回退到默认每秒一次。
> - 上下文变量：`server.tickCount` = 当前服务器 tick 数（Long）。

## 2. 条件完整列表

### 概率/冷却

| 条件         | 参数             | 说明        |
|------------|----------------|-----------|
| `CHANCE`   | `value`: 0~100 | 概率判定（百分比） |
| `COOLDOWN` | `value`: 毫秒    | 冷却时间      |

### 生命/法力

| 条件             | 参数             | 说明      |
|----------------|----------------|---------|
| `HEALTH_ABOVE` | `value`: 0~100 | 生命百分比高于 |
| `HEALTH_BELOW` | `value`: 0~100 | 生命百分比低于 |
| `MANA_ABOVE`   | `value`: 0~100 | 法力百分比高于 |
| `MANA_BELOW`   | `value`: 0~100 | 法力百分比低于 |

### 状态

| 条件               | 参数            | 说明      |
|------------------|---------------|---------|
| `IS_SNEAKING`    | —             | 正在潜行    |
| `IS_SPRINTING`   | —             | 正在疾跑    |
| `IS_FLYING`      | —             | 正在飞行    |
| `IN_WORLD`       | `value`: 世界名  | 在指定世界   |
| `IN_BIOME`       | `value`: 生物群系 | 在指定生物群系 |
| `HAS_PERMISSION` | `value`: 权限节点 | 拥有权限    |

### 装备/属性

| 条件                | 参数                   | 说明     |
|-------------------|----------------------|--------|
| `HOLDING_TYPE`    | `value`: Material    | 手持物品类型 |
| `WEARING_SET`     | `value`: 套装 ID       | 穿戴指定套装 |
| `HAS_AFFIX`       | `value`: 词条 ID       | 拥有指定词条 |
| `ATTRIBUTE_ABOVE` | `attribute`, `value` | 属性值高于  |
| `ATTRIBUTE_BELOW` | `attribute`, `value` | 属性值低于  |
| `LEVEL_RANGE`     | `min`, `max`         | 等级范围   |

### 伤害/目标

| 条件            | 参数            | 说明     |
|---------------|---------------|--------|
| `DAMAGE_TYPE` | `value`: 伤害类型 | 伤害类型匹配 |
| `TARGET_TYPE` | `value`: 目标类型 | 目标类型匹配 |

伤害类型：`physical` `vanilla`

> `DAMAGE_TYPE` 当前只在 `ON_DAMAGED` 上下文中携带，且取值仅 `physical`（Symphony 直伤入口）/ `vanilla`（其他原版伤害源）。元素伤害走 `SymphonyDamageEvent.elementDamages`，**不会**让 `DAMAGE_TYPE: fire/ice/lightning/...` 匹配命中。如需按元素分流请在 SCRIPT action 里读 `server.trigger_damage` 或 listen 事件。

目标类型：`PLAYER` `MOB` `BOSS` `ANIMAL` `UNDEAD` `ARTHROPOD`

> `BOSS` 当前仅匹配原版 4 种：`ENDER_DRAGON / WITHER / ELDER_GUARDIAN / WARDEN`。MythicMobs 自定义 boss 不会被识别为 `BOSS`，请改用 SCRIPT 条件读 `entity.maxHealth` 或自定义 PDC 标记。

### 逻辑组合

| 条件    | 参数               | 说明      |
|-------|------------------|---------|
| `AND` | `children`: 条件列表 | 所有子条件为真 |
| `OR`  | `children`: 条件列表 | 任一子条件为真 |
| `NOT` | `child`: 单个条件    | 子条件取反   |

### 脚本条件

| 条件       | 参数              | 说明           |
|----------|-----------------|--------------|
| `SCRIPT` | `code`: Aria 代码 | 脚本返回 boolean |

```yaml
- type: SCRIPT
  code: |
    // SCRIPT 条件求值时会注入与 action 一致的 trigger_* 上下文，
    // 可以读 server.trigger_entity / trigger_victim / trigger_damage 等。
    val.hp = symphony.entity.getHealth(server.trigger_entity)
    val.maxHp = symphony.entity.getMaxHealth(server.trigger_entity)
    return hp / maxHp < 0.3
```

## 3. 上下文变量在脚本中的访问

触发器 **action 阶段** 与 **SCRIPT 条件求值** 都注入相同的 `server.trigger_*` 命名变量。**当前实际注入的 key**（其它文档可能列了更多，但代码里只有以下这些会写入）：

### 词条触发器（`ScriptActionHandler` / `ConditionEvaluator.SCRIPT`）注入

| 变量                      | 类型           | 说明                   |
|-------------------------|--------------|----------------------|
| `server.trigger_entity` | LivingEntity | `context.entity`，攻击侧=自己、防御侧=受害者本人 |
| `server.trigger_type`   | String       | 触发器类型 ID             |
| `server.trigger_location` | Location   | 触发位置                 |
| `server.trigger_victim` | LivingEntity | `context.target`，攻击侧=受害者、防御侧=攻击者（命名沿用历史） |
| `server.trigger_damage` | Number       | 伤害值（仅伤害类触发器有）        |
| 词条 params               | Number       | 当前等级的所有 `levels.<n>.*` 参数 |

> ⚠️ `trigger_victim` 命名容易误导——它实际是 `context.target`。在 ON_DEFEND 下指向攻击者。需要"始终指向受害者"或"始终指向攻击者"的语义时，**优先使用 action 的 `target: TRIGGER_VICTIM` / `TRIGGER_ATTACKER`**（自动按触发侧反转），而不是脚本里手动判断。

### 技能 / Aria 脚本上下文（`AriaSkillProvider`）注入

| 变量                    | 类型                     | 说明     |
|-----------------------|------------------------|--------|
| `server.caster`       | LivingEntity           | 施法者    |
| `server.skill_id`     | String                 | 技能 ID  |
| `server.skill_level`  | Number                 | 技能等级   |
| `server.origin`       | Location               | 施法位置   |
| `server.target`       | LivingEntity           | 主目标    |
| `server.targets`      | List<LivingEntity>     | 多目标列表  |
| `server.trigger_type` | String                 | 调用上下文  |
| `server.trigger_target` | LivingEntity         | 触发的目标  |

> **未注入但常见误用**：`trigger_attacker` / `trigger_isCritical` / `trigger_weapon` / `trigger_comboCount` / `trigger_currentTick` —— 这些 key 没有被任何 listener 写入 globalStorage，脚本里读到的会是 `none`。需要这些信息时请改用 Bukkit 事件或在 listener 里自己注入。

## 4. 自定义触发器

其他插件注册的自定义触发器也可以在词条配置中使用：

```yaml
triggers:
  - type: "myplugin:on_combo"
    conditions:
      - type: SCRIPT
        code: |
          return server.trigger_comboCount >= 5
    actions:
      - type: DAMAGE
        amount: 100
        damage_type: physical
        target: TRIGGER_TARGET
```
