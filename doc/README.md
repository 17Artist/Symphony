# Symphony — Minecraft 全能属性插件

Symphony 是一个基于 Blink 框架开发的 Minecraft 全能属性系统插件，集成 Aria 脚本引擎，为服务器提供完整的 RPG 属性解决方案。

## 核心特性

- 脚本驱动属性系统 — 所有属性由 Aria 脚本定义，零硬编码，附带默认属性包（50+ 属性），可完全自定义
- 属性交互网络 — 属性之间可定义溢出转化、阈值突变、协同增幅、互斥衰减等关系，告别孤立数字
- 元素反应系统 — 参考原神，两种元素叠加触发化学反应（蒸发/融化/超导/感电/冻结/扩散），支持自定义反应
- 词条共鸣系统 — 词条组合激活质变效果，参考 Lost Ark 铭刻 + PoE 词缀协同
- 天赋门系统 — 属性达到阈值解锁全新机制，参考 PoE Keystone + D4 巅峰
- 状态层系统 — 持续攻击叠加层数，满层触发爆发效果，参考 Lost Ark/PoE2 异常状态
- 动态环境属性 — 属性随生物群系/天气/时间/对手类型动态调整
- 词条系统 — 附着在物品上的触发式效果，支持随机生成
- 类 MythicMobs 触发器系统 — 60+ 内置触发器类型，支持条件组合与自定义触发器
- 技能提供者机制 — 统一的技能调用接口，支持内置技能、Aria 脚本技能、MythicMobs 桥接
- 成长系统 — 等级/宝石/符文/强化/套装，完整的角色养成体系

## 技术栈

| 组件 | 技术 |
|------|------|
| 语言 | Kotlin |
| 框架 | Blink（@Awake 生命周期 / @AutoListener 事件 / BlinkConfig 配置） |
| 脚本 | Aria（JVM 嵌入式脚本引擎，ASM JIT，沙箱安全） |
| 目标版本 | 1.18.2 ~ 最新 |

## 文档索引

### 设计文档

| 文档 | 说明 |
|------|------|
| [系统架构设计](docs/design/01-architecture.md) | 模块划分、数据流、计算管线 |
| [属性系统设计](docs/design/02-attribute-system.md) | 属性分类、来源、计算公式 |
| [词条系统设计](docs/design/03-affix-system.md) | 词条结构、效果类型、随机生成 |
| [触发器系统设计](docs/design/04-trigger-system.md) | 触发器类型、条件系统、上下文 |
| [技能提供者设计](docs/design/05-skill-provider.md) | 提供者接口、内置技能、脚本技能 |
| [成长系统设计](docs/design/06-growth-system.md) | 等级/宝石/符文/强化/套装 |
| [Aria 脚本集成](docs/design/07-script-integration.md) | 命名空间、公式引擎、沙箱 |
| [数据存储设计](docs/design/08-data-storage.md) | 数据结构、存储后端、缓存策略 |
| [API 设计](docs/design/09-api-design.md) | 对外接口、事件系统、PAPI 集成 |
| [划时代高级系统](docs/design/10-advanced-systems.md) | 属性交互/元素反应/词条共鸣/天赋门/状态层/环境属性 |
| [NMS 适配层设计](docs/design/11-nms-adapter.md) | 版本断代分析、适配器接口、各版本实现要点、构建配置 |

### 使用文档

| 文档 | 说明 |
|------|------|
| [快速上手](docs/guide/01-quick-start.md) | 安装、基础配置、第一个词条 |
| [属性配置](docs/guide/02-attribute-config.md) | 属性列表、自定义属性、公式配置 |
| [词条配置](docs/guide/03-affix-config.md) | 词条编写、词条池、随机生成 |
| [触发器参考](docs/guide/04-trigger-reference.md) | 所有触发器类型与条件的完整参考 |
| [技能提供者指南](docs/guide/05-skill-provider-guide.md) | 技能配置、脚本技能、第三方桥接 |
| [成长系统配置](docs/guide/06-growth-config.md) | 等级/宝石/符文/强化/套装配置 |
| [Aria 脚本示例](docs/guide/07-script-examples.md) | 常用脚本示例与最佳实践 |
| [高级系统指南](docs/guide/08-advanced-guide.md) | 属性交互/元素反应/词条共鸣/天赋门/状态层/环境属性 |
