# SymphonyLevel

`SymphonyLevel` 是一个可运行的最小等级提供者，用来展示外部插件如何向 Symphony 注册 `LevelProvider`。它只维护每名玩家的一份等级与经验，不包含角色、职业、技能树或数据库。

## 构建与安装

在 Symphony 仓库根目录执行：

```powershell
./gradlew.bat :example:level-provider:build
```

成品位于 `example/level-provider/build/libs/`。把 `Symphony` 与本模块 JAR 放入服务器 `plugins/`；插件名为 `SymphonyLevel`，硬依赖 `Symphony`。


## 配置

首次启动生成 `config.yml`、`language.yml` 和 `players/`：

```yaml
provider:
  display-name: '玩家等级'
  priority: 100

level-curve:
  maximum-level: 100
  base-experience: 100
  growth-factor: 1.18
```

升级到下一级所需经验为 `base-experience × growth-factor^(当前等级 - 1)`，结果四舍五入为整数。达到最高等级后经验归零，继续增加的经验会作为溢出量返回给命令调用者。

每名玩家的数据保存在 `players/<uuid>.yml`：

```yaml
schema-version: 1
level: 20
experience: 42
```

保存时先写同目录临时文件，再原子替换正式文件；文件系统不支持原子移动时退回普通替换。

## 命令

| 命令                             | 权限                      | 用途               |
|--------------------------------|-------------------------|------------------|
| `/symlevel show [玩家]`          | 无                       | 查看在线玩家的等级快照      |
| `/symlevel addexp <玩家> <数量>`   | `symlevel.admin.addexp` | 增加经验并按曲线升级       |
| `/symlevel set <玩家> <等级> [经验]` | `symlevel.admin.set`    | 设置等级与当前经验        |
| `/symlevel reload`             | `symlevel.admin.reload` | 校验并重载配置；失败时恢复旧注册 |

`/slevel` 是根命令别名。所有文字均来自 `language.yml`。

## API 接入重点

核心注册位于 `LevelRuntime.registerProvider`：

- Provider ID 为 `symphonylevel:player_level`；
- `snapshot` 只读取已加载的内存数据，不访问文件；
- 玩家加入时先加载数据，再调用 `api.levels.refresh`；
- 等级或经验改变后调用 `refresh`，让 `LevelChangeEvent`、等级条件和相关外部 Provider 得到一致快照；
- 插件停用时先关闭注册，再刷新在线玩家，避免留下幽灵 Provider。

这个示例刻意不填写 `characterId`、`characterName` 和角色 metadata。需要多角色时，应由实际角色插件在自己的数据模型中维护当前角色，再把当前角色映射为 `ProvidedLevel`。

