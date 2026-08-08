# 配置类型与源码索引

## 目录

- [证据顺序](#证据顺序)
- [配置包结构](#配置包结构)
- [配置类型](#配置类型)
- [通用约束](#通用约束)
- [源码检索](#源码检索)

## 证据顺序

依次采用：当前生产加载器、同版本测试、JAR 内默认配置、JAR 内“棱镜武库”、Wiki。文档和 skill 与源码冲突时，以当前加载器及其测试为准并报告漂移。

## 配置包结构

验证器把 `Symphony/` 覆盖到首次安装后的 Symphony 配置快照，并把 `Overture/` 与内置物品一起加载。全局单例文件会完整替换默认值，因此必须写全：

```text
Symphony/
├─ config.yml
├─ combat-power.yml
├─ language.yml
├─ display.yml
├─ attributes/*.yml
├─ damage/*.yml
├─ affixes/*.yml
├─ affix-pools/*.yml
├─ skills/*.yml
├─ items/
│  ├─ enhancement.yml
│  ├─ socket-removal.yml
│  ├─ gems/*.yml
│  ├─ socket-tools/*.yml
│  └─ sets/*.yml
├─ advanced/
│  ├─ interactions/*.yml
│  ├─ reactions/*.yml
│  ├─ resonances/*.yml
│  ├─ talents/*.yml
│  ├─ statuses/*.yml
│  └─ environments/*.yml
├─ gui/*.yml
└─ scripts/

Overture/
├─ displays/*.yml
└─ items/**
```

只新增某类定义时，不要复制无关的全局文件。

## 配置类型

| 目标 | 输出位置 | 当前权威实现 | 可复制的当前成品 |
| --- | --- | --- | --- |
| 主开关、副手、性能、Epic Fight | `config.yml` | `DefinitionLoader.loadSettings` | `assets/config.yml` |
| 战力 | `combat-power.yml` | `DefinitionLoader.loadCombatPower`、`PowerExpression` | `showcase/prismatic-arsenal/symphony/combat-power.yml` |
| 属性和属性回调 | `attributes/*.yml` | `DefinitionLoader.loadAttributes` | `assets/attributes/`、`showcase/.../attributes/` |
| 伤害通道与护甲公式 | `damage/*.yml` | `DefinitionLoader.loadDamage` | `assets/damage/channels.yml` |
| 词条 | `affixes/*.yml` | `DefinitionLoader.loadGeneric("affixes", AFFIX_FIELDS, ...)` | `assets/affixes/`、`showcase/.../affixes/` |
| 词条池与成本 | `affix-pools/*.yml` | `DefinitionLoader.loadGeneric` + `compileAffixPools` | `showcase/.../affix-pools/` |
| 宝石 | `items/gems/*.yml` | `DefinitionLoader.loadGeneric("items/gems", GEM_FIELDS, ...)` | `showcase/.../items/gems/` |
| 打孔器 | `items/socket-tools/*.yml` | `DefinitionLoader.loadSocketTools` | `showcase/.../items/socket-tools/` |
| 拆卸道具 | `items/socket-removal.yml` | `DefinitionLoader.loadSocketRemoval` | `showcase/.../items/socket-removal.yml` |
| 强化 | `items/enhancement.yml` | `DefinitionLoader.loadEnhancement` | `assets/items/enhancement.yml` |
| 套装 | `items/sets/*.yml` | `DefinitionLoader.loadSets` | `showcase/.../items/sets/` |
| 物品技能 | `skills/*.yml` | `DefinitionLoader.loadGeneric("skills", SKILL_FIELDS, ...)` + `AriaCallbackRuntime.prepare` | `showcase/.../skills/` |
| 属性联动 | `advanced/interactions/*.yml` | `DefinitionLoader.loadGeneric` + `compileInteractions` | `showcase/.../advanced/interactions/` |
| 元素反应 | `advanced/reactions/*.yml` | `DefinitionLoader.loadGeneric("advanced/reactions", REACTION_FIELDS, ...)` | `showcase/.../advanced/reactions/` |
| 共鸣 | `advanced/resonances/*.yml` | `DefinitionLoader.loadGeneric(..., RESONANCE_FIELDS, ...)` | `showcase/.../advanced/resonances/` |
| 天赋 | `advanced/talents/*.yml` | `DefinitionLoader.loadGeneric(..., TALENT_FIELDS, ...)` | `showcase/.../advanced/talents/` |
| 状态 | `advanced/statuses/*.yml` | `DefinitionLoader.loadGeneric(..., STATUS_FIELDS, ...)` | `showcase/.../advanced/statuses/` |
| 环境 | `advanced/environments/*.yml` | `DefinitionLoader.loadGeneric(..., ENVIRONMENT_FIELDS, ...)` | `showcase/.../advanced/environments/` |
| 玩家文字 | `language.yml` | `LanguageBundle` | `assets/language.yml`，必须是完整文件 |
| Lore 行格式 | `display.yml` | `ItemDisplayFormats` | `assets/display.yml`，必须是完整文件 |
| 界面布局 | `gui/*.yml` | `GuiLayoutRepository` | `assets/gui/` |
| MythicMobs 怪物属性 | MythicMobs 的 `Mobs/*.yml` | `MythicMobsListener` | `example/mythicmobs/` |

以上路径均相对于 `symphony-bukkit/src/main/resources/`；`assets/` 是本体默认配置，`showcase/` 是首次安装样例。

## 通用约束

- 主配置当前使用 `schema: 2`；玩法定义通常使用 `schema: 1`。以对应加载函数为准。
- 未写命名空间的 Symphony 定义按 `symphony:` 解析；外部属性写完整命名空间。
- ID 使用小写字母、数字、下划线、短横线、点和合法命名空间分隔符。
- 百分比 `25%` 解析为 `0.25`。只在比例字段使用。
- 词条 `levels` 参数由通用加载器按数值读取；需要百分比显示的参数写 `0.25`，再在 `display.description` 使用 `{参数|percent}`。
- 属性运算值：`add`、`multiply_base`、`multiply_total`。
- 严格加载会拒绝未知字段、重复键、非有限数字、越界值、缺失引用和行内错误结构。
- `language.yml` 与 `display.yml` 是完整契约，不能只写局部覆盖。
- `combat-power.yml`、`items/enhancement.yml`、`items/socket-removal.yml` 是全局单例；一个配置包只能各有一份。
- 增量包可以依赖首次安装基线已有的定义，但本包新增内容之间的引用必须闭合；不要为了“完整”复制并改写无关全局文件。
- 验证器按相对路径覆盖文件，不按 YAML 键合并。新增属性、通道或高级定义应使用本包独有的文件名；确需覆盖基线文件时，先检查基线中哪些 ID 仍被状态、技能、回调、战力和物品引用，并把它们完整保留。

## 源码检索

从仓库根执行：

```powershell
rg -n "fun load[A-Z]|StrictObject|finish\(\)" symphony-engine/src/main/kotlin/priv/seventeen/artist/symphony/engine/config
rg -n "class .*ComponentCodec|override fun decode" symphony-overture/src/main/kotlin
rg -n "object .*Trigger" symphony-engine/src/main/kotlin/priv/seventeen/artist/symphony/engine/trigger
```

加载器报未知字段时，打开报错所属的 `load*` 函数，不要通过试错猜拼写。
