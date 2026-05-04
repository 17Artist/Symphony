# 技能提供者设计

## 1. 核心概念

技能提供者（Skill Provider）是一个抽象层，允许不同的技能来源通过统一接口被词条系统调用。

调用链：`词条触发 → Action(SKILL) → SkillDispatcher → Provider(id).cast(skillId, level, context)`

这种设计的好处：
- 词条配置不需要关心技能的具体实现方式
- 可以无缝对接 MythicMobs、自定义技能插件等第三方系统
- Aria 脚本技能和 YAML 配置技能使用相同的调用方式

## 2. API 接口

```kotlin
interface ISkillProvider {
    val id: String
    val displayName: String
    
    fun cast(skillId: String, level: Int, context: SkillContext): Boolean
    fun hasSkill(skillId: String): Boolean
    fun getSkillInfo(skillId: String, level: Int): SkillInfo?
    fun getSkillIds(): List<String>
}

data class SkillContext(
    val caster: LivingEntity,
    val target: LivingEntity?,
    val targets: List<LivingEntity>,
    val origin: Location,
    val triggerContext: ITriggerContext?,
    val parameters: Map<String, Any>
)

data class SkillInfo(
    val id: String,
    val displayName: String,
    val description: List<String>,
    val maxLevel: Int,
    val cooldown: Long,
    val manaCost: Double
)
```

## 3. 技能调度器

```kotlin
object SkillDispatcher {
    
    fun dispatch(
        providerId: String,
        skillId: String,
        level: Int,
        context: SkillContext
    ): Boolean {
        val provider = SkillProviderManager.getProvider(providerId)
            ?: run {
                logger.warning("Unknown skill provider: $providerId")
                return false
            }
        
        if (!provider.hasSkill(skillId)) {
            logger.warning("Skill not found: $providerId:$skillId")
            return false
        }
        
        // 发布技能释放事件
        val event = SkillCastEvent(context.caster, providerId, skillId, level, context)
        Bukkit.getPluginManager().callEvent(event)
        if (event.isCancelled) return false
        
        return provider.cast(skillId, level, context)
    }
}
```

## 4. 内置提供者

### 4.1 Symphony 内置提供者 (`symphony`)

通过 YAML 配置定义技能，技能效果由 Action 序列组成（复用词条的 Action 系统）：

```yaml
# skills/fire_burst.yml
id: fire_burst
display_name: "火焰爆发"
description:
  - "&c对目标造成 {damage} 点火焰伤害"
  - "&c并点燃 {burn_duration} 秒"
max_level: 5
cooldown: 5000
mana_cost: 20

levels:
  1:
    damage: 30
    burn_duration: 2
    radius: 3
  2:
    damage: 50
    burn_duration: 3
    radius: 3.5
  3:
    damage: 75
    burn_duration: 4
    radius: 4
  4:
    damage: 100
    burn_duration: 5
    radius: 4.5
  5:
    damage: 150
    burn_duration: 6
    radius: 5
actions:
  - type: DAMAGE
    amount: "{damage}"
    damage_type: fire
    target: TRIGGER_TARGET
  - type: POTION
    effect: FIRE_RESISTANCE
    duration: "{burn_duration}"
    amplifier: 0
    target: TRIGGER_TARGET
    invert: true
  - type: PARTICLE
    particle: FLAME
    count: 30
    offset: [0.5, 0.5, 0.5]
    target: TRIGGER_TARGET
  - type: SOUND
    sound: "entity.blaze.shoot"
    volume: 1.0
    pitch: 1.2
```

### 4.2 Aria 脚本提供者 (`aria`)

通过 Aria 脚本定义技能逻辑，适合需要复杂逻辑的技能：

```yaml
# skills/script/chain_lightning.yml
id: chain_lightning
provider: aria
display_name: "连锁闪电"
max_level: 3
cooldown: 8000
mana_cost: 35

script: |
  val.caster = server.caster
  val.target = server.target
  val.level = server.skill_level
  val.damage = 20 + level * 15
  val.chainCount = 2 + level
  val.chainRange = 5.0
  
  symphony.entity.damage(target, damage, 'lightning')
  symphony.effect.particle(target, 'ELECTRIC_SPARK', 15)
  
  var.lastTarget = target
  var.hitTargets = [target]
  
  for (i in Range(0, chainCount)) {
      val.nearby = symphony.entity.getNearby(lastTarget, chainRange)
      val.nextTarget = nearby.find(-> {
          return !hitTargets.contains(args[0])
      })
      if (nextTarget == none) { break }
      
      val.chainDamage = damage * math.pow(0.7, i + 1)
      symphony.entity.damage(nextTarget, chainDamage, 'lightning')
      symphony.effect.line(lastTarget, nextTarget, 'ELECTRIC_SPARK', 10)
      
      hitTargets.add(nextTarget)
      lastTarget = nextTarget
  }
```

脚本技能的上下文变量通过 `server.*` 注入：

| 变量                   | 类型           | 说明          |
|----------------------|--------------|-------------|
| `server.caster`      | LivingEntity | 施法者         |
| `server.target`      | LivingEntity | 主目标         |
| `server.targets`     | List         | 多目标列表       |
| `server.skill_level` | Number       | 技能等级        |
| `server.skill_id`    | String       | 技能 ID       |
| `server.origin`      | Location     | 施法位置        |
| `server.trigger_*`   | Any          | 关联触发器的上下文变量 |

### 4.3 MythicMobs 桥接提供者 (`mythicmobs`)

自动检测 MythicMobs 插件，注册桥接提供者。MythicMobs 作为 `compileOnly` 依赖直接调用 API，不再使用反射：

```kotlin
class MythicMobsBridge : ISkillProvider {
    override val id = "mythicmobs"
    override val displayName = "MythicMobs"
    
    override fun cast(skillId: String, level: Int, context: SkillContext): Boolean {
        val mm = MythicBukkit.inst()
        val casterEntity = BukkitAdapter.adapt(context.caster)
        
        // 收集目标：优先多目标列表，其次单目标
        val targets = context.targets.ifEmpty {
            listOfNotNull(context.target)
        }.map { BukkitAdapter.adapt(it) }
        
        // power 映射为 level，支持 MM 技能等级缩放
        return mm.apiHelper.castSkill(
            casterEntity, skillId, 
            level.toFloat(),  // power
            targets
        )
    }
    
    override fun hasSkill(skillId: String): Boolean {
        return MythicBukkit.inst().skillManager.getSkill(skillId).isPresent
    }
}
```

与旧版的区别：
- 类名从 `MythicMobsSkillProvider` 改为 `MythicMobsBridge`
- 使用 `mm.apiHelper.castSkill()` 替代手动构造 `SkillMetadataImpl`
- 支持多目标（`context.targets` 列表）
- `level` 映射为 MM 的 `power` 参数，支持技能等级缩放

### 4.4 MythicMobs Mechanic 注册 (`MythicMobsMechanicRegistrar`)

Symphony 向 MythicMobs 注册自定义 Mechanic，允许 MM 技能配置中直接调用 Symphony 的属性/伤害/治疗能力：

```kotlin
object MythicMobsMechanicRegistrar : Listener {
    // 监听 MythicMechanicLoadEvent，按 mechanicName 注册 ITargetedEntitySkill 实现
}
```

注册的 Mechanic：

| Mechanic | 参数 | 说明 |
|----------|------|------|
| `symphony_damage{amount=10}` | `amount` | 对目标造成指定数值的伤害 |
| `symphony_heal{amount=5}` | `amount` | 治疗目标指定数值 |
| `symphony_buff{id=..;op=flat;value=10;duration=100}` | `id`, `op`, `value`, `duration` | 为目标添加临时属性 Buff |

在 MythicMobs 技能配置中使用：

```yaml
# MythicMobs 技能配置
MySkill:
  Skills:
    - symphony_damage{amount=50} @target
    - symphony_heal{amount=20} @self
    - symphony_buff{id=physical_damage;op=flat;value=10;duration=200} @self
```

## 5. 词条中的技能调用配置

```yaml
# 词条配置中调用不同提供者的技能
triggers:
  - type: ON_ATTACK
    conditions:
      - type: CHANCE
        value: 25
    actions:
      # 调用 Symphony 内置技能
      - type: SKILL
        provider: "symphony"
        skill: "fire_burst"
        level: "{level}"
        
      # 调用 MythicMobs 技能
      - type: SKILL
        provider: "mythicmobs"
        skill: "FireballBarrage"
        level: 1
        
      # 调用 Aria 脚本技能
      - type: SKILL
        provider: "aria"
        skill: "chain_lightning"
        level: "{level}"
```

## 6. 自定义技能提供者注册

其他插件可通过 API 注册自己的技能提供者：

```kotlin
// 在你的插件中
class MySkillProvider : ISkillProvider {
    override val id = "myplugin"
    override val displayName = "My Plugin Skills"
    
    override fun cast(skillId: String, level: Int, context: SkillContext): Boolean {
        // 你的技能执行逻辑
        return true
    }
    
    // ... 其他方法实现
}

// 注册
SymphonyAPI.getSkillProviderManager().register(MySkillProvider())
```
