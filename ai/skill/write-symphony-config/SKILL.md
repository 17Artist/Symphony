---
name: write-symphony-config
description: 为当前 Symphony 与 Overture 编写、修改和验证可部署 YAML 配置包。用于属性、伤害通道、元素反应、回调、状态、共鸣、天赋、环境、战力、套装、词条、宝石、孔位、强化、物品技能、界面、显示格式、Overture 物品组件或 MythicMobs 怪物属性配置；也用于排查跨文件引用、严格 YAML、动态 Lore 标签和配置重载错误。
---

# 编写 Symphony 配置

只交付能被当前仓库生产加载器接受的配置。不要依靠记忆猜字段，不要把 Wiki 片段当成模式定义。

## 1. 确认源码和输出位置

从本 skill 向上定位包含 `settings.gradle.kts`、`symphony-engine` 和 `symphony-bukkit` 的仓库根目录。读取 [configuration-map.md](references/configuration-map.md)，按任务涉及的配置类型打开其中列出的当前加载器和成品文件。

在独立目录创建增量配置包，不直接修改运行中服务器：

```text
<pack>/
├─ Symphony/
│  └─ ...
└─ Overture/          # 没有物品时可省略
   ├─ items/
   └─ displays/
```

若用户给出现有配置目录，以该目录为输出根；保留无关文件和用户已有修改。

配置包以 Symphony 首次安装生成的默认文件和“棱镜武库”为基线，只需包含本次新增或明确替换的文件。“自包含”指本包新增的每个 ID、物品和工具引用都有定义，不要求重复复制无关的 `language.yml`、`display.yml` 或主配置。新增定义使用唯一文件名；与基线同路径的文件会被整份替换，不能误删仍被其它默认配置引用的通道或定义。

## 2. 先建立引用清单

写文件前列出本包的命名空间、ID 和依赖关系：

- 属性 → 伤害通道、条件、战力公式、物品属性；
- 词条 → 词条池 → Overture 物品和成本；
- 宝石 → Overture 宝石物品 → 可接受类别的孔位；
- 打孔器、拆卸道具、强化保护道具 → Overture 物品 ID；
- 套装 → 部位 → Overture `symphony:set` 组件；
- 物品技能 → Overture `symphony:skills` 组件；
- 元素反应 → 已声明且 `element: true` 的伤害通道；
- 回调 → 已注册触发器、条件、动作和目标变量。

使用当前仓库已有 ID 时保持原 ID；新内容使用一致的小写短横线或下划线风格。附属插件注册的属性必须写完整命名空间。

## 3. 选择配置类型

按 [configuration-map.md](references/configuration-map.md) 选择文件位置。涉及物品时必须再读 [overture-items.md](references/overture-items.md)；涉及回调、条件、动作、状态或高级玩法时必须读 [callbacks-advanced.md](references/callbacks-advanced.md)。

优先使用结构化条件和动作。只有结构化动作无法表达需求时才创建 Aria 脚本；脚本只能使用当前源码实际注入的上下文变量。

## 4. 编写规则

- 所有 YAML 使用缩进块；禁止把映射或列表压成单行。
- 新 Symphony 定义使用当前要求的 `schema`，不得复制旧版字段。
- 百分比按字段语义写成 `25%` 或内部小数；普通点数不要误写百分比。
- 词条 `levels` 中供 `{parameter}` 使用的参数必须是 YAML 数值。比例写成 `0.12`，不要写成 `12%`；玩家显示时再用 `{parameter|percent}` 格式化。
- 运算只使用当前支持的 `add`、`multiply_base`、`multiply_total`。
- 不把原版 `Armor` 当作 Symphony 物理防御；使用 `physical_defense` 和伤害公式。
- 显示给玩家的名称、说明和 Lore 使用自然中文，不输出内部 ID、字段名或仅供配置者阅读的过程说明。需要解释类别时写玩家能理解的中文名称。
- Overture 物品使用唯一 ID、存在的展示方案和已注册的 `symphony:*` 组件。
- 不重复定义 YAML 键，不让两个文件声明同一个定义或物品 ID。
- 不用“稍后补充”、伪代码或省略号冒充成品配置。

## 5. 强制验证

完成后阅读 [validation.md](references/validation.md)，运行：

```powershell
& '<skill>/scripts/validate-config-pack.ps1' -PackRoot '<pack>'
```

该脚本会把本体默认配置、内置“棱镜武库”和待验配置合并，再调用当前 `DefinitionLoader`、回调准备流程、Aria 编译器与 Symphony 的 Overture 组件解码器；同时检查玩家可见文本、条件展示和跨文件引用。修复全部错误并重复运行，直到输出：

```text
SYMPHONY_CONFIG_PACK_VALID <pack>
```

不得用普通 YAML 解析成功代替生产加载器验证。若任务改动了加载器、组件格式或 skill 本身，还要运行完整 Gradle 测试。

## 6. 交付

列出生成文件、主要 ID、引用关系和验证命令。将“生产加载器验证通过”与“真实 Paper、玩家操作、动态 Lore、伤害事件验证”分开说明；没有运行服务器时不得声称实服已验收。
