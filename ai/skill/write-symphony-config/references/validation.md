# 配置包验证

## 验证层次

1. 检查目录结构和 YAML 缩进。
2. 使用 `scripts/validate-config-pack.ps1` 调用生产加载器。
3. 涉及加载器或 skill 修改时运行项目测试与构建。
4. 在真实 Paper 上检查命令、物品 Lore、界面和战斗。

## 运行校验器

```powershell
& '<skill>/scripts/validate-config-pack.ps1' -PackRoot '<pack>'
```

需要 JDK 17。脚本会执行仓库的 `:symphony-bukkit:validateConfigPack`：

同一仓库中并发启动的校验会自动排队，避免多个代理同时写 Gradle 输出目录。

- 把本体默认配置和内置“棱镜武库”作为真实首次安装基线；
- 用待验 `Symphony/` 覆盖同路径文件；
- 使用 `DefinitionLoader` 严格加载全部 Symphony 定义；
- 读取内置及待验 Overture 物品与展示方案；
- 使用生产 `AttributeComponentCodec`、`SocketComponentCodec`、`SkillComponentCodec`、`SetComponentCodec`、`OffhandComponentCodec` 解码组件；
- 使用与重载相同的结构化条件、Action 和触发器校验，并预编译 Aria 脚本；
- 检查重复物品/展示 ID、普通与条件展示、分组描述文件、玩家文案和跨文件 Overture 引用。

只有出现以下结尾才算通过：

```text
SYMPHONY_CONFIG_PACK_VALID <absolute-path>
```

## 常见失败

| 报错 | 处理 |
| --- | --- |
| unknown field | 打开对应 `DefinitionLoader.load*`，删除或改正字段 |
| unknown attribute/channel/set/skill | 补齐定义或修正完整 ID |
| duplicate item/display ID | 为本包使用唯一 ID，不依赖文件覆盖顺序 |
| unknown Symphony component | 只使用当前五种 Overture 组件 |
| unknown Overture item | 在 `Overture/items/` 创建物品或修正成本/工具引用 |
| unknown display | 创建展示方案并确保物品的 `display` 精确匹配 |
| language/display validation | 写完整文件，不能只写局部覆盖 |
| exposes internal identifier | 把玩家文案里的属性、类别或字段 ID 改为自然中文；动态标签和 `{参数}` 不受此检查影响 |

## 真实服务器边界

生产加载器验证不覆盖：Bukkit 事件先后、玩家输入、动态 Lore 实际渲染、客户端动画、Epic Fight 混合端、MythicMobs 版本差异和高并发伤害。部署后至少执行 `/sym validate`、`/sym reload`，再领取物品并验证相关玩法。
