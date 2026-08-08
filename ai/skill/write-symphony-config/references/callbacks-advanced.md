# 回调与高级玩法

## 目录

- [读取当前注册表](#读取当前注册表)
- [结构化回调](#结构化回调)
- [元素与状态](#元素与状态)
- [共鸣、天赋和环境](#共鸣天赋和环境)
- [Aria 脚本](#aria-脚本)

## 读取当前注册表

触发器、条件、动作和脚本上下文可能随版本变化。写配置前从仓库根执行：

```powershell
Get-Content -Raw symphony-engine/src/main/kotlin/priv/seventeen/artist/symphony/engine/trigger/BuiltInTriggers.kt
Get-Content -Raw symphony-bukkit/src/main/kotlin/priv/seventeen/artist/symphony/bukkit/script/ConfiguredCallbackSchema.kt
rg -n 'context\[|put\(' symphony-bukkit/src/main/kotlin/priv/seventeen/artist/symphony/bukkit/script/AriaCallbackRuntime.kt
```

然后打开 `assets/` 与 `showcase/prismatic-arsenal/symphony/` 中使用同一机制的成品。不要编造触发器名或上下文变量。

## 结构化回调

回调由稳定 ID、触发器、可选条件、动作和可选冷却组成：

```yaml
callbacks:
  arcane_notice:
    trigger: combat.damage_taken
    conditions:
      - type: attribute
        attribute: arcane_resistance
        operator: '>='
        value: 25%
        target: self
      - type: cooldown
        key: arcane_notice
        duration-ms: 2000
    actions:
      - type: message
        target: self
        message: '&d奥术抗性抵消了部分伤害。'
```

- 单实体触发器只使用 `self`；只有触发器确实提供另一实体时才使用 `target`。
- 伤害准备阶段修改本轮数值，确认阶段执行消息、状态和其它不可逆副作用。
- 回调产生的新伤害受最大事务深度限制，不要构造无限反伤链。
- 冷却键在同一玩法域内保持稳定，避免每次触发生成新键。
- 词条参数占位符来自 `levels` 的 YAML 数值；例如 12% 概率应存为 `chance: 0.12`，条件中写 `value: '{chance}'`。

## 元素与状态

元素反应的 `trigger` 与 `aura` 必须引用 `element: true` 的伤害通道。`aura` 是目标已有的元素附着，`gauge-consume` 是消耗的附着值：

```yaml
schema: 1
id: vaporize
trigger: fire
aura: ice
type: amplify
multiplier: 2
gauge-consume: 1
```

状态定义的每层属性写在 `per-stack.modifiers`，周期效果写在 `callbacks.tick`。`duration-ms` 与 `tick-ms` 都是毫秒；`tick-ms` 不得低于加载器下限。

## 共鸣、天赋和环境

- 共鸣条件用于词条标签数量、套装件数或属性比较。
- 天赋门槛使用 `all`、`any`、`none` 组合属性比较；至少存在一个非空组。
- 环境的 `when` 可组合世界、群系、露天、天气和时间；写出的条件全部成立才生效。
- 三者提供的属性修改放在 `modifiers`，条件失效后来源自动移除。
- 条件读取的属性若又被同一被动修改，必须检查是否会在启用与停用之间振荡。

## Aria 脚本

仅在结构化动作不能表达需求时使用。脚本放入 `Symphony/scripts/`，配置中使用相对路径。先核对 `AriaCallbackRuntime` 注入的对象和变量；单人触发器不能假定存在 `target`。

脚本视为可信服务器代码。限制循环、递归和高频实体扫描；属性最终值仍由 Symphony 计算，脚本不应绕过来源系统直接写入最终快照。
