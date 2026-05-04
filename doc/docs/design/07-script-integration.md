# Aria 脚本集成设计

## 1. 集成架构

Symphony 的一切可配置逻辑均由 Aria 脚本驱动，包括属性定义本身。集成方式：

```
SymphonyScriptEngine（初始化）
    ├── 复用 Aria 默认引擎（共享 GlobalStorage + AnnotationRegistry）
    ├── 注册 symphony.* 命名空间（bootstrap script 写入 global.symphony）
    ├── 递归扫描 scripts/attributes/**/*.aria → 执行脚本收集 @attribute 注解
    ├── AttributeAnnotationProcessor.process() → 聚合注解 → AttributeRegistry.register
    ├── 执行 scripts/formulas/*.aria → 预编译公式
    └── 执行 scripts/mechanics/*.aria → 注册战斗机制脚本
```

### 1.1 属性脚本加载顺序

属性脚本是整个系统的基石，必须最先加载：

```
1. scripts/attributes/**/*.aria    ← 属性定义（递归，最先）
   └── AttributeAnnotationProcessor.process()
2. scripts/formulas/*.aria         ← 公式（依赖属性 ID）
3. scripts/mechanics/*.aria        ← 战斗机制（依赖属性 + 公式）
4. scripts/skills/*.aria           ← 技能脚本（依赖以上所有）
5. scripts/modules/*.aria          ← 公共模块（按需 import）
```

属性脚本单个文件只声明一个属性，互相独立，顺序无关。加载顺序仅在跨目录类型（attributes/formulas/…）之间有意义。

## 2. Symphony Aria 命名空间

通过 `SymphonyBridge` + `NamespaceRegistrar` 注册到 `global.symphony`，在所有脚本中可见：

### 2.1 symphony.attribute — 属性查询与运行时操作

> 属性 **定义** 不再通过命名空间注册，改用 [Aria 注解系统](#3-属性注解系统)。此处保留查询、运行时读写与修改方法。

```aria
// 查询定义
val.all = symphony.attribute.list()
val.info = symphony.attribute.getInfo('physical_damage')
val.exists = symphony.attribute.exists('my_attr')
val.byCategory = symphony.attribute.listByCategory('combat')
val.byTag = symphony.attribute.listByTag('offensive')

// 运行时读取（以实体为上下文）
val.value = symphony.attribute.get(entity, 'physical_damage')
val.raw = symphony.attribute.getRaw(entity, 'physical_damage')

// 获取所有属性快照
val.snapshot = symphony.attribute.snapshot(entity)

// 临时属性修改（Buff）
symphony.attribute.buff(entity, 'physical_damage', 'FLAT', 50, 10000)

// 永久属性修改（带来源标识）
symphony.attribute.modify(entity, 'physical_damage', 'FLAT', 10, 'my_source')

// 移除指定来源的修改
symphony.attribute.remove(entity, 'my_source')

// 强制重算
symphony.attribute.recalculate(entity)

// 注销（极少使用）
symphony.attribute.unregister('my_attr')
```

### 2.2 symphony.entity — 实体操作

```aria
// 伤害
symphony.entity.damage(target, 50, 'physical')
symphony.entity.damage(target, 30, 'fire')

// 治疗
symphony.entity.heal(target, 20)

// 生命值
val.hp = symphony.entity.getHealth(target)
val.maxHp = symphony.entity.getMaxHealth(target)
symphony.entity.setHealth(target, 100)

// 法力值
val.mana = symphony.entity.getMana(player)
symphony.entity.setMana(player, 50)
symphony.entity.costMana(player, 20)

// 附近实体
val.nearby = symphony.entity.getNearby(location, 5.0)
val.nearbyPlayers = symphony.entity.getNearbyPlayers(location, 10.0)
val.nearbyHostile = symphony.entity.getNearbyHostile(entity, 8.0)

// 药水效果
symphony.entity.addPotion(target, 'SPEED', 200, 1)
symphony.entity.removePotion(target, 'SPEED')
symphony.entity.hasPotion(target, 'SPEED')

// 位置与方向
val.loc = symphony.entity.getLocation(entity)
val.dir = symphony.entity.getDirection(entity)
symphony.entity.teleport(entity, location)
symphony.entity.setVelocity(entity, 0, 1, 0)
```

### 2.3 symphony.item — 物品操作

```aria
// 获取手持物品
val.item = symphony.item.getMainHand(player)
val.offhand = symphony.item.getOffHand(player)

// 获取装备
val.helmet = symphony.item.getEquipment(player, 'HEAD')
val.chest = symphony.item.getEquipment(player, 'CHEST')

// 物品属性
val.attrs = symphony.item.getAttributes(item)
val.affixes = symphony.item.getAffixes(item)
val.gems = symphony.item.getGems(item)
val.enhanceLevel = symphony.item.getEnhanceLevel(item)
val.setId = symphony.item.getSetId(item)

// 物品检查
val.hasAffix = symphony.item.hasAffix(item, 'fire_strike')
val.rarity = symphony.item.getRarity(item)
```

### 2.4 symphony.effect — 特效

```aria
// 粒子效果
symphony.effect.particle(location, 'FLAME', 20, 0.5, 0.5, 0.5)
symphony.effect.particleAt(entity, 'HEART', 5)

// 音效
symphony.effect.sound(location, 'entity.blaze.shoot', 1.0, 1.2)
symphony.effect.soundAt(entity, 'entity.player.levelup', 1.0, 1.0)

// 几何粒子
symphony.effect.line(from, to, 'REDSTONE', 15)
symphony.effect.circle(center, 3.0, 'ENCHANTMENT_TABLE', 30)
symphony.effect.sphere(center, 2.0, 'FLAME', 50)
symphony.effect.helix(center, 2.0, 3.0, 'SPELL_WITCH', 60)

// 消息
symphony.effect.actionbar(player, '&c你受到了 {damage} 点伤害!')
symphony.effect.title(player, '&6&l升级!', '&e等级 10', 10, 40, 10)
symphony.effect.message(player, '&a你获得了一个新词条!')
```

### 2.5 symphony.trigger — 触发器操作

```aria
// 手动触发自定义触发器
symphony.trigger.dispatch('ON_CUSTOM', player, {
    'customData': 'value',
    'damage': 100
})

// 检查冷却
val.onCooldown = symphony.trigger.isOnCooldown(player, 'fire_strike')
val.remaining = symphony.trigger.getCooldown(player, 'fire_strike')

// 设置冷却
symphony.trigger.setCooldown(player, 'fire_strike', 5000)
```

### 2.6 symphony.growth — 成长系统

```aria
// 等级
val.level = symphony.growth.getLevel(player)
symphony.growth.addExp(player, 1000, 'script')
symphony.growth.setLevel(player, 50)

// 宝石（addGem 和 insertGem 等价）
symphony.growth.addGem(item, 0, 'ruby', 3)
symphony.growth.insertGem(item, 0, 'ruby', 3)
symphony.growth.removeGem(item, 0)

// 符文
symphony.growth.activateRune(player, 'berserker', 2)
symphony.growth.addFragments(player, 'berserker', 10)
val.fragments = symphony.growth.getFragments(player, 'berserker')

// 强化
val.enhLevel = symphony.growth.getEnhanceLevel(item)
val.result = symphony.growth.enhance(player, item)
symphony.growth.setEnhanceLevel(item, 10)
```

### 2.7 symphony.element — 元素系统

```aria
// 元素光环
symphony.element.applyAura(entity, 'fire', 1.0)
val.aura = symphony.element.getAura(entity, 'fire')
symphony.element.removeAura(entity, 'fire')
val.allAuras = symphony.element.getAllAuras(entity)

// 元素反应
val.reacted = symphony.element.tryReaction(attacker, target, 'fire')
```

### 2.8 symphony.status — 状态层系统

```aria
// 注册状态层
symphony.status.register('bleed', '流血', 5, 8000, 'INDIVIDUAL')

// 操作状态层
symphony.status.addStacks(entity, 'bleed', 2, attacker)
val.stacks = symphony.status.getStacks(entity, 'bleed')
symphony.status.clearStacks(entity, 'bleed')
symphony.status.setImmune(entity, 'bleed', 3000)

// 查询
val.all = symphony.status.list()
```

### 2.9 symphony.resonance — 词条共鸣

```aria
// 注册共鸣
symphony.resonance.register('fire_mastery', '火焰精通', 'AFFIX_TAG_COUNT', 'fire', 3)

// 查询
val.active = symphony.resonance.getActive(player)
symphony.resonance.check(player)
val.all = symphony.resonance.list()
```

### 2.10 symphony.talent — 天赋门

```aria
// 注册天赋
symphony.talent.register('berserker', '狂战本能', 'physical_damage', 50, '>=')

// 查询
val.unlocked = symphony.talent.isUnlocked(player, 'berserker')
symphony.talent.check(player)
val.status = symphony.talent.getStatus(player)
val.all = symphony.talent.list()
```

### 2.11 symphony.interaction — 属性交互网络

```aria
// 注册交互
symphony.interaction.register('crit_overflow', 'OVERFLOW', 'critical_chance', 'critical_damage', 0.75, 0.5, 1.0)

// 管理
symphony.interaction.remove('crit_overflow')
val.all = symphony.interaction.list()
```

### 2.12 symphony.environment — 环境系统

```aria
// 注册环境修正器
symphony.environment.register('deep_ocean', '深海之力', 'BIOME')

// 查询
val.active = symphony.environment.getActive(player)
val.all = symphony.environment.list()
```

### 2.13 symphony.world — 世界信息

```aria
// 世界查询
val.time = symphony.world.getTime(entity)
val.raining = symphony.world.isRaining(entity)
val.thundering = symphony.world.isThundering(entity)
val.dimension = symphony.world.getDimension(entity)
val.biome = symphony.world.getBiome(entity)
val.outdoor = symphony.world.isOutdoor(entity)
```

## 3. 属性注解系统

属性通过 Aria 的注解系统声明，处理流程：

```
.aria 文件执行
    ↓ 注解在解析阶段进入 AnnotationRegistry（engine 级共享）
    ↓
AttributeAnnotationProcessor.process()
    ↓ findClassesByAnnotation("attribute") 列举所有属性类
    ↓ 对每个类：扫描 getAll() 聚合同类兄弟注解
    ↓ 构建 AttributeDefinition
    ↓
AttributeRegistry.register(def)
```

### 3.1 注解列表

类级：`@attribute` `@displayName` `@description` `@category` `@default` `@min` `@max` `@format` `@priority` `@vanillaBinding` `@readonly` `@tag` `@tags`

方法级（写在类体内的 `name = -> {}` 函数字段上）：`@derive` `@onChange` `@formula`

详见 [guide/02-attribute-config.md](../guide/02-attribute-config.md#3-注解速查)。

### 3.2 处理器容错

- `@attribute` 缺少 ID 参数：WARN 并跳过该类；
- 未识别的类/方法注解：WARN，继续处理已识别部分；
- 同一 ID 重复声明：后加载者覆盖先加载者（由 `AttributeRegistry` 保证）；
- `@derive / @onChange / @formula` 引用的函数通过 `FunctionValue` 以 `${className}#${methodName}` 字符串 ID 记录，供后续计算层解析。

## 4. 公式引擎

### 4.1 设计

所有数值计算公式通过 Aria 脚本定义，使用 `AriaCompiledRoutine` 预编译。FormulaEngine 只负责编译和缓存，不内置沙箱配置（沙箱由调用方控制）：

```kotlin
class FormulaEngine {
    private val compiled = ConcurrentHashMap<String, AriaCompiledRoutine>()
    
    fun register(name: String, code: String) {
        compiled[name] = Aria.compile("formula:$name", code)
    }
    
    fun has(name: String): Boolean = compiled.containsKey(name)
    
    fun get(name: String): AriaCompiledRoutine? = compiled[name]
    
    fun clear() {
        compiled.clear()
    }
}
```

调用方从 `get()` 取出 `AriaCompiledRoutine` 后自行创建 Context 并执行，灵活控制沙箱和变量注入。

### 4.2 内置公式

```yaml
# config/formulas.yml
formulas:
  # 伤害计算
  physical_damage_calc: |
    val.atk = args[0]
    val.def = args[1]
    val.pen = args[2]
    val.effectiveDef = def * (1 - pen)
    return math.max(1, atk - effectiveDef)
  
  # 防御减伤率
  defense_reduction: |
    val.defense = args[0]
    val.attackerLevel = args[1]
    return defense / (defense + 100 + attackerLevel * 5)
  
  # 暴击伤害
  critical_damage_calc: |
    val.baseDamage = args[0]
    val.critMultiplier = args[1]
    return baseDamage * critMultiplier
  
  # 元素伤害
  element_damage_calc: |
    val.elementDmg = args[0]
    val.elementRes = args[1]
    return elementDmg * math.max(0, 1 - elementRes)
  
  # 闪避判定
  dodge_calc: |
    val.dodgeRate = args[0]
    val.accuracy = args[1]
    return math.max(0, dodgeRate - (accuracy - 1))
  
  # 升级经验
  level_exp: |
    val.level = args[0]
    return math.floor(100 * math.pow(level, 1.5) + level * 50)
  
  # 战斗力评分
  combat_power: |
    val.atk = args[0]
    val.def = args[1]
    val.hp = args[2]
    val.critChance = args[3]
    val.critDmg = args[4]
    return math.floor(atk * 2 + def * 1.5 + hp * 0.5 + critChance * 100 + critDmg * 50)
```

## 5. 沙箱安全

### 5.1 沙箱配置

```kotlin
// 属性定义沙箱（允许 symphony.attribute.register，禁止其他副作用）
val attributeSandbox = SandboxConfig.builder()
    .maxExecutionTime(5000)
    .maxCallDepth(100)
    .allowFileSystem(false)
    .allowNetwork(false)
    .allowJavaInterop(false)
    .allowedNamespaces("math", "type", "symphony.attribute", "console")
    .build()

// 公式沙箱（最严格，纯计算）
val formulaSandbox = SandboxConfig.builder()
    .maxExecutionTime(1000)
    .maxCallDepth(50)
    .allowFileSystem(false)
    .allowNetwork(false)
    .allowJavaInterop(false)
    .allowedNamespaces("math", "type")
    .build()

// 技能脚本沙箱（允许 symphony 全命名空间）
val skillSandbox = SandboxConfig.builder()
    .maxExecutionTime(5000)
    .maxCallDepth(100)
    .allowFileSystem(false)
    .allowNetwork(false)
    .allowJavaInterop(false)
    .allowedNamespaces("math", "type", "console", "symphony", "json", "string")
    .build()

// 条件脚本沙箱
val conditionSandbox = SandboxConfig.builder()
    .maxExecutionTime(500)
    .maxCallDepth(20)
    .allowFileSystem(false)
    .allowNetwork(false)
    .allowJavaInterop(false)
    .allowedNamespaces("math", "type", "symphony")
    .build()
```

### 5.2 安全策略

- 所有脚本在沙箱中执行，限制执行时间和调用深度
- 禁止文件系统和网络访问
- 禁止直接 Java 互操作（只能通过 symphony.* 命名空间）
- 公式脚本只允许 math 和 type 命名空间
- 脚本执行超时自动终止，记录警告日志

## 6. 脚本文件组织

```
plugins/Symphony/scripts/
├── attributes/               # 属性定义脚本（最先加载，定义所有属性）
│   ├── combat.aria           #   战斗属性
│   ├── movement.aria         #   移动属性
│   ├── elements.aria         #   元素属性（批量注册）
│   ├── resource.aria         #   资源属性
│   ├── derived.aria          #   派生属性（战斗力等）
│   └── custom-example.aria   #   自定义示例
├── mechanics/                # 战斗机制脚本（伤害计算、闪避判定等）
│   ├── damage.aria           #   伤害计算流程
│   ├── defense.aria          #   防御/减伤计算
│   └── combat.aria           #   战斗状态管理
├── formulas/                 # 公式脚本（预编译，高频调用）
│   ├── exp.aria              #   经验曲线
│   └── enhance.aria          #   强化概率
├── skills/                   # 技能脚本
│   ├── chain_lightning.aria
│   ├── meteor_strike.aria
│   └── healing_wave.aria
├── conditions/               # 自定义条件脚本
│   └── is_boss_fight.aria
└── modules/                  # 公共模块（可被其他脚本 import）
    ├── utils.aria
    └── constants.aria
```

## 7. 脚本热重载

```kotlin
// /symphony reload 命令触发
fun reloadScripts() {
    // 1. 清空属性注册表（核心变更！）
    attributeRegistry.clear()
    
    // 2. 清除公式缓存
    formulaEngine.clear()
    
    // 3. 清除技能脚本缓存
    ariaSkillProvider.reloadAll()
    
    // 4. 清除脚本引擎编译缓存
    scriptEngine.shutdown()
    
    // 5. 清除模块缓存
    Aria.getEngine().moduleLoader.cache.clear()
    
    // 6. 按顺序重新执行脚本
    executeScripts("scripts/attributes/")   // 重新注册所有属性
    executeScripts("scripts/mechanics/")    // 重新注册战斗机制
    executeScripts("scripts/formulas/")     // 重新预编译公式
    
    // 7. 标记所有在线玩家属性为 dirty，触发重算
    Bukkit.getOnlinePlayers().forEach { 
        attributeCache.markDirty(it.uniqueId) 
    }
}
```

重载后，如果某个属性脚本被删除，该属性将不再存在于注册表中。已有物品上引用该属性的修改器会被静默忽略（不报错，只是不生效）。
