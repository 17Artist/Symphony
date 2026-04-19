package priv.seventeen.artist.symphony.api.affix

import priv.seventeen.artist.symphony.api.trigger.ITriggerContext

/**
 * 词条动作处理器 — 让第三方插件扩展可用的 action type。
 *
 * 在 YAML / Aria 词条定义里使用：
 * ```yaml
 * triggers:
 *   ON_ATTACK:
 *     actions:
 *       - type: MY_CUSTOM_ACTION
 *         foo: 10
 * ```
 * 注册：`SymphonyAPI.getAffixManager().registerActionHandler("MY_CUSTOM_ACTION", impl)`
 */
interface IActionHandler {
    fun execute(params: Map<String, Any>, context: ITriggerContext, affix: AffixInstance)
}
