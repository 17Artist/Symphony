# SymphonyDamageWatch

`SymphonyDamageWatch` 是一个可运行的伤害事件观察附属，用来在开发服中检查 Symphony 实际完成了哪些伤害运算。它不会修改伤害、取消事件、写入属性来源或保存玩家数据。

## 构建与安装

在 Symphony 仓库根目录执行：

```powershell
./gradlew.bat :example:damage-watch:build
```

成品位于 `example/damage-watch/build/libs/`。将 JAR 与 Symphony 一起放入服务器 `plugins/`；插件名为 `SymphonyDamageWatch`，硬依赖 `Symphony`。

## 会显示什么

本插件在 `MONITOR` 优先级只读监听：

- `SymphonyDamageEvent`：显示事务、攻击者、目标、原因、暴击、克制/普通/被克制伤害总量。
- `SymphonyHitCheckEvent`：未命中时显示命中属性、闪避属性、闪避概率和本次随机值。
- 每个伤害通道：显示请求值、克制换算、暴击换算、减免结果和通道最终值。
- 每个参与运算的属性：显示友好名称、完整属性 ID、归属实体、运算用途、读取值、通道和是否真正触发。
- 元素反应、父事务和请求 metadata。
- `SymphonyDamageConfirmedEvent`：伤害通过 Bukkit 落地后显示最终确认值。

默认把详细内容发给攻击者、受击玩家和控制台，同时给相关玩家显示一条 ActionBar 摘要。怪物对怪物的伤害没有玩家接收者，但控制台仍会收到完整内容。

`SymphonyDamageEvent` 是解析阶段，之后仍可能被 `SymphonyDamageApplyEvent` 或 Bukkit 伤害事件取消；只有出现“伤害已确认”才代表本次伤害真正落地。

主伤害事件只会在命中后发布。若攻击被闪避，观察器会显示独立的“攻击未命中”，不会再出现解析和确认消息。普通攻击中由 `damage-attribute` 提供的 physical/fire/ice/lightning 基础通道是确定性输入；词条 callback 里带 `chance` 的雷电是另一笔概率追加伤害，不要把两者混为一谈。

## 配置

```yaml
output:
  notify-attacker: true
  notify-victim: true
  notify-console: true
  chat-details: true
  action-bar-summary: true
  confirmation-message: true
  miss-message: true

details:
  include-inactive-attributes: false
  include-metadata: true
  maximum-channels: 16
  maximum-attributes: 24
  decimals: 3
```

所有显示文字位于本插件自己的 `language.yml`。动态伤害通道没有语言项时会回退到通道 ID；这是调试附属的有意行为，便于核对配置。

