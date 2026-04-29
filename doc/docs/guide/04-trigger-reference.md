# 触发器参考手册

## 1. 触发器类型完整列表

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

伤害类型：`physical` `magic` `fire` `ice` `lightning` `poison` `holy` `dark` `true`

目标类型：`PLAYER` `MOB` `BOSS` `ANIMAL` `UNDEAD` `ARTHROPOD`

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
    val.hp = symphony.entity.getHealth(server.trigger_entity)
    val.maxHp = symphony.entity.getMaxHealth(server.trigger_entity)
    val.ratio = hp / maxHp
    return ratio < 0.3 && ratio > 0.1
```

## 3. 上下文变量在脚本中的访问

触发器上下文变量通过 `server.trigger_*` 前缀在 Aria 脚本中访问：

```aria
val.attacker = server.trigger_attacker
val.victim = server.trigger_victim
val.damage = server.trigger_damage
val.isCritical = server.trigger_isCritical
val.weapon = server.trigger_weapon
```

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
