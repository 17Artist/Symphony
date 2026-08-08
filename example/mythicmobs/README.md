# MythicMobs 怪物属性示例

把 `Mobs/symphony-attributes.yml` 复制到 MythicMobs 的 `Mobs/` 目录后执行 MythicMobs 的配置重载，再生成：

```text
/mm mobs spawn SymphonyAttributeGuardian:3 1
```

三级属性守卫会取得：

- `max_health`：默认基础 20 + 配置 80 + 两级成长 20，最终 120；
- `physical_damage`：12 + 两级成长 4，配置贡献 16；
- `physical_defense`：60 + 两级成长 10，配置贡献 70；
- `accuracy`：10% + 两级成长 4%，配置贡献 14%；
- 固定暴击、元素抗性与移动速度增幅。

这里的 `max_health` 说明的是 Symphony 最终属性。默认定义基础值为 20，因此配置贡献为 100 时最终结果是 120；MythicMobs 自己的 `Health` 字段只负责其原生生命设置，不能代替 Symphony 属性。


属性来源使用怪物 UUID 形成稳定的 `mythicmobs:<uuid>` 来源。死亡或 despawn 后来源会被移除；重复处理同一实体时执行整来源替换，不会把属性叠加多次。
