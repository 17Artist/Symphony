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
```

`scripts/modules/*.aria` 不会自动加载，通过 Aria 的 `import` 语句按需引入。技能脚本通过 `skills/*.yml` 的 `script: |` 字段注册。

## 2. Symphony Aria 命名空间

通过 `SymphonyBridge` + `NamespaceRegistrar` 注册到 `global.symphony`，在所有脚本中可见：

### 2.1 symphony.attribute — 属性查询与运行时操作


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
val.nearby = symphony.entity.getNearby(entity, 5.0)

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

// 获取装备（返回包含所有槽位的 Map）
val.equip = symphony.item.getEquipment(player)
val.helmet = equip.helmet
val.chest = equip.chestplate

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
symphony.effect.particleAt(x, y, z, world, 'HEART', 5)

// 音效
symphony.effect.sound(location, 'entity.blaze.shoot', 1.0, 1.2)
symphony.effect.soundAt(x, y, z, world, 'entity.player.levelup', 1.0, 1.0)

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
symphony.trigger.dispatch('ON_CUSTOM', player)

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

公式通过 `scripts/formulas/*.aria` 文件定义，按文件名注册（`physical_damage.aria` → 公式名 `physical_damage`）。启动时由 `SymphonyScriptEngine` 扫描该目录，将每个文件的源代码交给 `FormulaEngine.register(name, code)` 预编译。

简短示例：

```aria
// scripts/formulas/physical_damage.aria
val.atk = args[0]
val.def = args[1]
val.pen = args[2]
val.effectiveDef = def * (1 - pen)
return math.max(1, atk - effectiveDef)
```

## 5. 沙箱安全

脚本在 Aria 引擎自带的沙箱中执行，Symphony 不额外包装沙箱配置层。执行时间、调用深度、命名空间白名单等安全策略由 Aria 统一管理，Symphony 仅通过注册 `symphony.*` 命名空间暴露可用能力。

## 6. 脚本文件组织

**自动加载**（启动 / `reload` 时扫描并执行）：

```
plugins/Symphony/scripts/
├── attributes/               # 属性定义脚本（最先加载，递归扫描子目录）
│   ├── combat/              # 战斗属性（一属性一文件）
│   │   ├── physical_damage.aria
│   │   ├── max_health.aria
│   │   └── ...
│   ├── movement/
│   ├── elements/
│   ├── resource/
│   ├── custom/
│   └── special/
├── formulas/                 # 公式脚本（按文件名注册到 FormulaEngine）
│   ├── exp.aria
│   └── enhance.aria
└── mechanics/                # 战斗机制脚本（伤害计算、闪避判定等）
    ├── damage.aria
    └── combat.aria
```

**按需加载**（不会被自动扫描，通过 Aria 的 `import` 语句由其他脚本引入）：

```
plugins/Symphony/scripts/
└── modules/                  # 公共模块
    ├── utils.aria
    └── constants.aria
```

**非脚本目录**（脚本以内联字段形式存在，由 YAML 加载器间接注册）：

- `skills/*.yml` — 技能定义中的 `script: |` 字段被提取后注册为技能脚本；
- 词条 YAML（`affixes/*.yml` 等）— 条件脚本通过 `type: SCRIPT` + `code: |` 内联声明，由词条加载器解析。

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
