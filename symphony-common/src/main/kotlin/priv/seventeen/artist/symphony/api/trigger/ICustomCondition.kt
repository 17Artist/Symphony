package priv.seventeen.artist.symphony.api.trigger

/**
 * 自定义条件谓词 — 让第三方插件扩展 ConditionEvaluator 支持的条件类型。
 *
 * ```kotlin
 * SymphonyAPI.getTriggerManager().registerConditionType("ABOVE_Y", object : ICustomCondition {
 *     override fun evaluate(cond, context, params): Boolean {
 *         val y = (cond["value"] as? Number)?.toDouble() ?: 0.0
 *         return context.entity.location.y > y
 *     }
 * })
 * ```
 */
interface ICustomCondition {
    fun evaluate(condition: Map<String, Any>, context: ITriggerContext, params: Map<String, Any>): Boolean
}
