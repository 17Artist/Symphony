# 技能提供者使用指南

## 1. 概述

技能提供者是 Symphony 的技能调用抽象层。词条触发时可以通过 `provider_id + skill_id + level` 调用任意来源的技能。

内置提供者：

| 提供者 ID       | 说明                              |
|--------------|---------------------------------|
| `symphony`   | Symphony 内置技能（YAML Action 序列）   |
| `aria`       | Aria 脚本技能                       |
| `mythicmobs` | MythicMobs 技能桥接（需安装 MythicMobs） |

## 2. Symphony 内置技能

### 2.1 创建技能

在 `plugins/Symphony/skills/` 目录下创建 YAML 文件：

```yaml
# skills/ice_nova.yml
id: ice_nova
display_name: "冰霜新星"
description:
  - "&b对周围 {radius} 格内的敌人"
  - "&b造成 {damage} 点冰霜伤害并减速"
max_level: 3
cooldown: 8000
mana_cost: 30

levels:
  1:
    damage: 40
    radius: 4
    slow_duration: 60
  2:
    damage: 65
    radius: 5
    slow_duration: 80
  3:
    damage: 100
    radius: 6
    slow_duration: 100

# 目标选择
target_selector:
  type: NEARBY_ENEMIES
  radius: "{radius}"

actions:
  - type: DAMAGE
    amount: "{damage}"
    damage_type: ice
    target: ALL_TARGETS
  - type: POTION
    effect: SLOW
    duration: "{slow_duration}"
    amplifier: 1
    target: ALL_TARGETS
  - type: PARTICLE
    particle: SNOWFLAKE
    count: 50
    offset: [3, 1, 3]
    target: SELF
  - type: SOUND
    sound: "block.glass.break"
    volume: 1.0
    pitch: 0.5
```

### 2.2 在词条中调用

```yaml
triggers:
  - type: ON_ATTACK
    conditions:
      - type: CHANCE
        value: 20
    actions:
      - type: SKILL
        provider: "symphony"
        skill: "ice_nova"
        level: 2
```

### 2.3 目标选择器

| 类型               | 说明     | 参数               |
|------------------|--------|------------------|
| `TRIGGER_TARGET` | 触发器目标  | —                |
| `SELF`           | 施法者自身  | —                |
| `NEARBY_ENEMIES` | 附近敌对实体 | `radius`         |
| `NEARBY_ALLIES`  | 附近友方实体 | `radius`         |
| `NEARBY_ALL`     | 附近所有实体 | `radius`         |
| `LINE_OF_SIGHT`  | 视线方向实体 | `range`, `width` |
| `CONE`           | 锥形范围   | `range`, `angle` |

## 3. Aria 脚本技能

### 3.1 创建脚本技能

在 `plugins/Symphony/skills/script/` 目录下创建：

```yaml
# skills/script/meteor_strike.yml
id: meteor_strike
provider: aria
display_name: "流星打击"
max_level: 3
cooldown: 15000
mana_cost: 50

script: |
  val.caster = server.caster
  val.target = server.target
  val.level = server.skill_level
  val.damage = 50 + level * 30
  val.radius = 3 + level * 0.5
  
  // 目标位置
  val.loc = symphony.entity.getLocation(target)
  
  // 延迟效果：先显示警告圈
  symphony.effect.circle(loc, radius, 'FLAME', 30)
  symphony.effect.sound(loc, 'entity.ender_dragon.growl', 0.5, 2.0)
  
  // 对范围内敌人造成伤害
  val.enemies = symphony.entity.getNearbyHostile(caster, radius)
  enemies.forEach(-> {
      val.dist = symphony.entity.distance(args[0], loc)
      if (dist <= radius) {
          val.dmgMultiplier = 1 - (dist / radius) * 0.5
          symphony.entity.damage(args[0], damage * dmgMultiplier, 'fire')
      }
  })
  
  // 爆炸特效
  symphony.effect.particle(loc, 'EXPLOSION_LARGE', 5)
  symphony.effect.particle(loc, 'FLAME', 50, radius, 1, radius)
  symphony.effect.sound(loc, 'entity.generic.explode', 1.0, 0.8)
```

### 3.2 脚本可用 API

脚本中可使用以下命名空间：

- `symphony.attribute.*` — 属性操作
- `symphony.entity.*` — 实体操作
- `symphony.item.*` — 物品操作
- `symphony.effect.*` — 特效
- `symphony.trigger.*` — 触发器操作
- `symphony.growth.*` — 成长系统
- `math.*` — 数学函数
- `type.*` — 类型检查

### 3.3 脚本上下文变量

| 变量                   | 类型           | 说明    |
|----------------------|--------------|-------|
| `server.caster`      | LivingEntity | 施法者   |
| `server.target`      | LivingEntity | 主目标   |
| `server.targets`     | List         | 多目标列表 |
| `server.skill_level` | Number       | 技能等级  |
| `server.skill_id`    | String       | 技能 ID |
| `server.origin`      | Location     | 施法位置  |

## 4. MythicMobs 桥接

### 4.1 前提条件

服务器需安装 MythicMobs 插件。Symphony 会自动检测并注册 `mythicmobs` 提供者。MythicMobs 作为 `compileOnly` 依赖直接调用 API，无需反射。

### 4.2 调用 MythicMobs 技能

```yaml
triggers:
  - type: ON_ATTACK
    conditions:
      - type: CHANCE
        value: 15
    actions:
      - type: SKILL
        provider: "mythicmobs"
        skill: "FireballBarrage"      # MythicMobs 中定义的技能名
        level: 1
```

MythicMobs 技能的 caster 和 target 会自动从触发器上下文中获取。`level` 会映射为 MM 的 `power` 参数，支持技能等级缩放。多目标场景下，`context.targets` 列表会全部传递给 MM。

### 4.3 在 MythicMobs 中使用 Symphony Mechanic

Symphony 向 MythicMobs 注册了自定义 Mechanic，可以在 MM 技能配置中直接调用 Symphony 的能力：

| Mechanic | 参数 | 说明 |
|----------|------|------|
| `symphony_damage{amount=10}` | `amount` | 对目标造成指定数值的伤害 |
| `symphony_heal{amount=5}` | `amount` | 治疗目标指定数值 |
| `symphony_buff{id=..;op=flat;value=10;duration=100}` | `id`, `op`, `value`, `duration` | 为目标添加临时属性 Buff |

示例 MythicMobs 技能配置：

```yaml
# plugins/MythicMobs/Skills/my_skills.yml
SymphonyBurst:
  Skills:
    - symphony_damage{amount=50} @target
    - symphony_heal{amount=20} @self
    - symphony_buff{id=physical_damage;op=flat;value=10;duration=200} @self
```

## 5. 注册自定义提供者

其他插件可以注册自己的技能提供者：

```kotlin
class MySkillProvider : ISkillProvider {
    override val id = "myplugin"
    override val displayName = "My Plugin Skills"
    
    override fun cast(skillId: String, level: Int, context: SkillContext): Boolean {
        when (skillId) {
            "my_skill" -> {
                // 执行技能逻辑
                return true
            }
            else -> return false
        }
    }
    
    override fun hasSkill(skillId: String): Boolean = skillId == "my_skill"
    
    override fun getSkillInfo(skillId: String, level: Int): SkillInfo? {
        if (skillId == "my_skill") {
            return SkillInfo("my_skill", "我的技能", listOf("描述"), 5, 5000, 20.0)
        }
        return null
    }
    
    override fun getSkillIds(): List<String> = listOf("my_skill")
}

// 注册
SymphonyAPI.getInstance().getSkillProviderManager().register(MySkillProvider())
```

注册后即可在词条中使用：

```yaml
actions:
  - type: SKILL
    provider: "myplugin"
    skill: "my_skill"
    level: 3
```
