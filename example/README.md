# Symphony 可运行示例

此目录保存独立于 Symphony 本体的可运行附属和外部系统配置，不会被打进 Symphony 主插件。

- [`level-provider`](level-provider/)：单玩家单等级数据，展示 `LevelProvider` 的注册、刷新、注销和持久化。
- [`runes`](runes/)：外置符文玩法，展示外部属性定义、稳定属性来源、等级事件联动和配置回滚。
- [`damage-watch`](damage-watch/)：监听 Symphony 伤害解析与确认事件，把通道、克制关系、触发属性和最终伤害发送给测试参与者。

