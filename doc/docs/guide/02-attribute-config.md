# 属性配置说明

## 1. 核心概念

Symphony 不内置任何属性。所有属性由 `plugins/Symphony/scripts/attributes/<category>/<id>.aria` 目录下的 Aria 脚本声明，**一个属性一个文件**、**使用注解**描述元数据。插件附带一套默认属性包，可自由修改、删除或从零开始编写。

```
plugins/Symphony/scripts/attributes/
├── combat/                # 战斗属性（20 个）
│   ├── physical_damage.aria
│   ├── max_health.aria
│   └── ...
├── movement/              # 移动属性（4 个）
├── elements/              # 元素伤害/抗性（12 个）
├── resource/              # 资源属性（4 个：等级/经验加成/掉落/幸运）
├── custom/                # 自定义示例（4 个）
├── special/               # 派生属性（combat_power）
└── ...                    # 你自己的子目录，随便加
```

加载器会 **递归扫描** 全部子目录下的 `.aria` 文件。所有 `@attribute` 类在脚本执行阶段进入注解注册表，脚本全部执行完毕后由 `AttributeAnnotationProcessor` 统一聚合注册到 `AttributeRegistry`。

修改后执行 `/symphony reload` 热加载，无需重启。

## 2. 编写属性脚本

### 最简属性

```aria
// scripts/attributes/custom/vampirism.aria
@attribute('vampirism')
@displayName('吸血')
@default(0.0)
class Vampirism {}
```

一个文件 + 一串注解 = 注册一个属性。默认叠加公式 `(base + flat) × (1 + percent)`。

### 完整属性

```aria
// scripts/attributes/combat/physical_damage.aria
@attribute('physical_damage')
@displayName('物理攻击力')
@description('物理伤害基础值')
@category('combat')
@default(1.0) @min(0.0) @max(999999.0)
@format('number')                 // number | percent | integer
@priority(10)                     // 显示排序（越小越靠前）
@vanillaBinding('generic.attack_damage')
@tag('offensive') @tag('physical')
class PhysicalDamage {}
```

同一行写多条注解是合法的（`@default(1.0) @min(0.0)`），仅作视觉紧凑。

### 只读属性

```aria
// scripts/attributes/resource/level.aria
@attribute('level')
@displayName('等级')
@category('resource')
@default(1.0) @min(1.0)
@format('integer')
@priority(150)
@readonly                         // 无参注解等价于 @readonly(true)
class Level {}
```

### 派生属性（`@derive`）

派生属性的计算函数作为类内方法声明，用 `@derive` 标注。Processor 会把函数引用保存为 `deriveId`，运行时由 `AttributeCalculator` 在 `@readonly` 属性计算阶段自动调用。

```aria
// scripts/attributes/special/combat_power.aria
@attribute('combat_power')
@displayName('战斗力')
@category('resource')
@default(0.0)
@format('integer')
@priority(200)
@readonly
class CombatPower {
    @derive
    calc = -> {
        val.h = args[0]
        val.atk = symphony.attribute.getRaw(h, 'physical_damage')
        val.hp = symphony.attribute.getRaw(h, 'max_health')
        val.crit = symphony.attribute.getRaw(h, 'critical_chance')
        return math.floor(atk * 2 + hp * 0.5 + crit * 200)
    }
}
```

### 元素家族

元素伤害/抗性各自成文件，不再用循环生成：

```
scripts/attributes/elements/
├── fire_damage.aria
├── fire_resistance.aria
├── ice_damage.aria
...
```

每个文件极简（7 行），复制模板改 id/displayName 即可。

## 3. 注解速查

### 类级注解

| 注解                | 参数类型      | 必填    | 说明                               |
|-------------------|-----------|-------|----------------------------------|
| `@attribute`      | String    | **是** | 属性 ID，唯一标识                       |
| `@displayName`    | String    | 否     | 显示名称（未填则用 id）                    |
| `@description`    | String    | 否     | 描述文本                             |
| `@category`       | String    | 否     | 分类（默认 `custom`）                  |
| `@default`        | Number    | 否     | 默认值（默认 0.0）                      |
| `@min`            | Number    | 否     | 最小约束（默认 `-Double.MAX_VALUE`）     |
| `@max`            | Number    | 否     | 最大约束（默认 `+Double.MAX_VALUE`）     |
| `@format`         | String    | 否     | `number` / `percent` / `integer` |
| `@priority`       | Number    | 否     | 显示排序                             |
| `@vanillaBinding` | String    | 否     | 绑定 Minecraft 原版属性                |
| `@readonly`       | Boolean/无 | 否     | 只读（派生属性）                         |
| `@dependsOn`      | String…   | 否     | 声明静态依赖的其他属性 ID                  |
| `@when`           | String    | 否     | 条件门控表达式，为 false 时属性不生效          |
| `@defaultExpr`    | String    | 否     | 动态默认值表达式                         |
| `@minExpr`        | String    | 否     | 动态最小值表达式                         |
| `@maxExpr`        | String    | 否     | 动态最大值表达式                         |
| `@tag`            | String    | 否     | 单个标签，可重复使用                       |
| `@tags`           | String…   | 否     | 批量标签，等价于多个 `@tag`                |

### 方法级注解

| 注解          | 位置   | 说明                       |
|-------------|------|--------------------------|
| `@derive`   | 类内方法 | 派生计算函数；入参 `args[0]` 为持有者 |
| `@onChange` | 类内方法 | 值变更回调                    |
| `@formula`  | 类内方法 | 自定义聚合公式；入参为 `base, flatSum, percentSum, holder`，返回最终值 |

未识别的注解会在启动时打印 `[WARN] 使用了未知类注解 @xxx`，便于发现拼写错误。

## 4. 默认属性包清单

- **combat/** — `physical_damage` `physical_defense` `magic_damage` `magic_defense` `attack_speed` `critical_chance` `critical_damage` `max_health` `health_regen` `max_mana` `mana_regen` `lifesteal` `elemental_mastery` `damage_reduction` `penetration` `accuracy` `dodge` `block_chance` `block_power` `thorns`
- **movement/** — `movement_speed` `jump_height` `fly_speed` `knockback_resistance`
- **elements/** — `fire/ice/lightning/poison/holy/dark` × `{_damage, _resistance}`
- **resource/** — `level`（只读） `exp_bonus` `drop_bonus` `luck`
- **custom/** — `bleed` `combo_rate` `exp_multiplier` `cooldown_reduction`
- **special/** — `combat_power`（派生只读）

## 5. 在词条/装备中使用属性

属性注册后，可通过 ID 在词条、装备、宝石等配置中引用：

```yaml
passive_attributes:
  physical_damage:
    operation: FLAT
    value: 25
  vampirism:
    operation: FLAT
    value: 0.05
```

```
/sym player attr get <玩家> vampirism
/sym player attr set <玩家> vampirism 0.1
```

引用不存在的属性 ID 时，系统静默忽略并在控制台警告。

