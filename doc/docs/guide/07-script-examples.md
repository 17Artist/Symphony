# Aria 脚本示例

## 1. 属性定义脚本

> 属性脚本采用 **一属性一文件 + 注解** 的写法。详细注解参考见 [02-attribute-config.md](./02-attribute-config.md)。

### 从零开始定义一套属性

目录结构：
```
scripts/attributes/my_rpg/
├── str.aria
├── dex.aria
├── int.aria
├── vit.aria
├── base_atk.aria
├── base_hp.aria
├── atk.aria
└── max_hp.aria
```

`str.aria`：
```aria
@attribute('str')
@displayName('力量')
@description('影响物理攻击力和负重')
@category('base_stat')
@default(10.0)
@format('integer')
@priority(1)
class Str {}
```

`dex.aria` / `int.aria` / `vit.aria` 同构，仅 id / displayName / description / priority 变化。

中间属性 `base_atk.aria`：
```aria
@attribute('base_atk')
@displayName('基础攻击')
@category('hidden')
@default(5.0)
class BaseAtk {}
```

派生属性 `atk.aria`：
```aria
@attribute('atk')
@displayName('攻击力')
@category('derived')
@format('integer')
@readonly
@vanillaBinding('generic.attack_damage')
class Atk {
    @derive
    calc = -> {
        val.h = args[0]
        val.str = symphony.attribute.getRaw(h, 'str')
        val.baseAtk = symphony.attribute.getRaw(h, 'base_atk')
        return baseAtk + str * 2.5
    }
}
```

派生属性 `max_hp.aria`：
```aria
@attribute('max_hp')
@displayName('生命上限')
@category('derived')
@format('integer')
@readonly
@vanillaBinding('generic.max_health')
class MaxHp {
    @derive
    calc = -> {
        val.h = args[0]
        val.vit = symphony.attribute.getRaw(h, 'vit')
        val.baseHp = symphony.attribute.getRaw(h, 'base_hp')
        return baseHp + vit * 8
    }
}
```

### 添加自定义元素

直接复制 `elements/` 目录下任一文件，改 id/displayName 即可。例如新增风元素伤害 `scripts/attributes/elements/wind_damage.aria`：

```aria
@attribute('wind_damage')
@displayName('风伤害')
@description('附加风元素伤害')
@category('element')
@default(0.0) @min(0.0)
@format('number')
@priority(100)
class WindDamage {}
```

`wind_resistance.aria` 同理。要批量生成多个元素，可以写一个小脚本/模板生成器一次性铺好文件，比循环更直观。

### 带联动回调的属性

`@onChange` 标注值变更回调：

```aria
// scripts/attributes/combat/max_health.aria 的扩展示例
@attribute('max_health')
@displayName('最大生命值')
@default(20.0) @min(1.0)
@vanillaBinding('generic.max_health')
class MaxHealth {
    @onChange
    sync = -> {
        val.entity = args[0]
        val.oldMax = args[1]
        val.newMax = args[2]
        if (oldMax > 0) {
            val.ratio = symphony.entity.getHealth(entity) / oldMax
            symphony.entity.setHealth(entity, ratio * newMax)
        }
    }
}
```

## 2. 公式脚本

### 伤害计算

```aria
// formulas/damage.aria
// args[0] = 攻击力, args[1] = 防御力, args[2] = 穿透率

val.atk = args[0]
val.def = args[1]
val.pen = args[2]

val.effectiveDef = def * (1 - pen)
val.damage = math.max(1, atk - effectiveDef)

return damage
```

### 经验曲线

```aria
// formulas/exp.aria
// args[0] = 当前等级

val.level = args[0]

// 二次曲线 + 线性增长
val.base = 100 * math.pow(level, 1.5)
val.linear = level * 50

// 每 10 级一个台阶
val.milestone = math.floor(level / 10) * 200

return math.floor(base + linear + milestone)
```

### 暴击伤害

```aria
// formulas/critical.aria
// args[0] = 基础伤害, args[1] = 暴击倍率, args[2] = 幸运值

val.baseDmg = args[0]
val.critMult = args[1]
val.luck = args[2]

// 幸运值提供微量暴击伤害加成
val.luckBonus = luck * 0.001
val.finalMult = critMult + luckBonus

return baseDmg * finalMult
```

## 3. 技能脚本

> 技能脚本通过 `skills/*.yml` 的 `script: |` 字段注册，不是独立的 `.aria` 文件。以下示例展示 `script:` 字段中的内容。

### 连锁闪电

```aria
// skills/chain_lightning.yml → script: |
val.caster = server.caster
val.target = server.target
val.level = server.skill_level

val.damage = 20 + level * 15
val.chainCount = 2 + level
val.chainRange = 5.0

// 第一次伤害
symphony.entity.damage(target, damage, 'lightning')
symphony.effect.particle(target, 'ELECTRIC_SPARK', 15)
symphony.effect.sound(target, 'entity.lightning_bolt.impact', 0.5, 1.5)

// 连锁
var.lastTarget = target
var.hitTargets = [target]

for (i in Range(0, chainCount)) {
    val.nearby = symphony.entity.getNearbyHostile(lastTarget, chainRange)
    val.nextTarget = nearby.find(-> {
        return !hitTargets.contains(args[0])
    })
    
    if (nextTarget == none) { break }
    
    // 每次连锁伤害衰减 30%
    val.chainDamage = damage * math.pow(0.7, i + 1)
    symphony.entity.damage(nextTarget, chainDamage, 'lightning')
    
    // 画闪电线
    symphony.effect.line(lastTarget, nextTarget, 'ELECTRIC_SPARK', 10)
    symphony.effect.sound(nextTarget, 'entity.lightning_bolt.thunder', 0.3, 2.0)
    
    hitTargets.add(nextTarget)
    lastTarget = nextTarget
}
```

### 治疗波

```aria
// skills/healing_wave.aria
val.caster = server.caster
val.level = server.skill_level

val.healAmount = 15 + level * 10
val.radius = 5 + level
val.maxTargets = 3 + level

// 获取附近友方
val.allies = symphony.entity.getNearbyPlayers(caster, radius)

// 按生命值百分比排序（优先治疗血量低的）
var.healCount = 0

allies.forEach(-> {
    if (healCount >= maxTargets) { return }
    
    val.target = args[0]
    val.hp = symphony.entity.getHealth(target)
    val.maxHp = symphony.entity.getMaxHealth(target)
    
    // 跳过满血的
    if (hp >= maxHp) { return }
    
    // 治疗量随距离衰减（用 Bukkit Location.distance）
    val.targetLoc = symphony.entity.getLocation(target)
    val.casterLoc = symphony.entity.getLocation(caster)
    val.dist = targetLoc.distance(casterLoc)
    val.distMultiplier = 1 - (dist / radius) * 0.3
    val.finalHeal = healAmount * distMultiplier
    
    symphony.entity.heal(target, finalHeal)
    symphony.effect.particle(target, 'HEART', 5)
    symphony.effect.line(caster, target, 'HAPPY_VILLAGER', 8)
    
    healCount = healCount + 1
})

// 施法特效（注意：1.20.5+ 粒子枚举名 SPELL_WITCH → WITCH，VILLAGER_HAPPY → HAPPY_VILLAGER）
symphony.effect.circle(caster, radius, 'WITCH', 40)
symphony.effect.sound(caster, 'block.beacon.activate', 1.0, 1.5)
```

### 冰霜护盾

```aria
// skills/frost_shield.aria
val.caster = server.caster
val.level = server.skill_level

val.duration = 5000 + level * 2000
val.damageReduction = 0.1 + level * 0.05
val.thornsPercent = 0.05 + level * 0.03

// 添加防御 Buff
symphony.attribute.buff(caster, 'damage_reduction', 'FLAT', damageReduction, duration)
symphony.attribute.buff(caster, 'ice_resistance', 'FLAT', 0.3, duration)

// 特效
symphony.effect.sphere(caster, 2.0, 'SNOWFLAKE', 30)
symphony.effect.sound(caster, 'block.glass.place', 1.0, 0.5)
symphony.effect.actionbar(caster, "&b&l冰霜护盾已激活! ({duration / 1000}秒)")
```

## 4. 条件脚本

> 条件脚本通过词条 YAML 中 `type: SCRIPT` + `code: |` 内联使用，不存在独立的 conditions 加载目录。

### Boss 战判定

```aria
// 词条条件中 type: SCRIPT, code: | 的内容
// ⚠️ 词条触发器 SCRIPT 条件求值时，server.trigger_* 上下文未注入，
// 这个示例只适合在技能脚本（AriaSkillProvider）里用 server.target。
// 词条触发器内做 boss 判定建议改用内置 TARGET_TYPE: BOSS 条件。

val.target = server.target           // 技能脚本里可用；词条条件里用此 key 拿不到
if (target == none) { return false }

// Symphony 没有 entity.getName 等便捷方法，但 getMaxHealth 是存在的。
// 用最大生命值作为简易 boss 判定阈值。
val.maxHp = symphony.entity.getMaxHealth(target)

// Boss 判定：生命值超过 500
return maxHp > 500
```

### 连击判定

```aria
// 词条条件中 type: SCRIPT, code: |
// ⚠️ 词条 SCRIPT 条件不能读 server.trigger_*；连击逻辑请改用
// 自定义 ConditionType（在插件里读 PlayerData.runtime.comboCount），
// 这里只展示伪代码思路。

// var.combo ~= 0
// combo = combo + 1
// 实际工程做法：
//   1. 第三方插件用 IEntityMetadata 存 combo_count
//   2. 注册 customCondition 'COMBO_AT_LEAST'，从 metadata 读取
return false
```

## 5. 公共模块

### 工具函数

```aria
// modules/utils.aria
export var.clamp = -> {
    val.value = args[0]
    val.min = args[1]
    val.max = args[2]
    return math.max(min, math.min(max, value))
}

export var.lerp = -> {
    val.a = args[0]
    val.b = args[1]
    val.t = args[2]
    return a + (b - a) * t
}

export var.randomRange = -> {
    val.min = args[0]
    val.max = args[1]
    return min + math.random() * (max - min)
}

export var.chance = -> {
    val.percent = args[0]
    return math.random() * 100 < percent
}

export var.formatNumber = -> {
    val.num = args[0]
    if (num >= 1000000) {
        return (num / 1000000).toFixed(1) + 'M'
    } elif (num >= 1000) {
        return (num / 1000).toFixed(1) + 'K'
    }
    return num.toFixed(0)
}
```

### 常量定义

```aria
// modules/constants.aria
export val.DAMAGE_TYPES = ['physical', 'magic', 'fire', 'ice', 'lightning', 'poison', 'holy', 'dark']
export val.MAX_LEVEL = 100
export val.TICK_RATE = 20
export val.COMBAT_TIMEOUT = 10000
```

### 在其他脚本中使用

```aria
import modules.utils as u
import { DAMAGE_TYPES, MAX_LEVEL } from 'modules.constants'

val.clamped = u.clamp(damage, 0, 9999)
val.formatted = u.formatNumber(totalDamage)

if (u.chance(30)) {
    // 30% 概率执行
}
```

## 6. 最佳实践

1. 属性脚本保持独立 — `scripts/attributes/` 下的脚本不应 `import` 其他脚本，它们是最底层的定义
2. 公式脚本保持简短 — 公式（包括属性的 `@formula`/`@derive`）会被高频调用，避免复杂逻辑。所有公式和技能脚本会被 Aria JIT 编译缓存（`Aria.compile()`），相同代码只编译一次
3. 一属性一文件 — 有规律的属性（如元素系列）用模板批量生成文件，不要运行期循环 register
4. 派生属性用 `@readonly` — 防止外部修改器干扰计算结果
5. 使用 `val` 声明不变的值 — 语义更清晰
6. 善用模块系统 — 公共函数放在 `modules/` 目录，通过 `import` 复用
7. 注意沙箱限制 — 脚本在 Aria 引擎自带沙箱中执行，有执行时间限制
8. 避免无限循环 — 超时会被终止
9. 使用 `server.*` 访问上下文 — 触发器和技能的上下文变量通过 `server.*` 前缀访问
10. `@onChange` 回调中不要触发属性重算 — 避免循环依赖
