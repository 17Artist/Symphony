package priv.seventeen.artist.symphony.api.trigger

import org.bukkit.entity.LivingEntity

interface ITriggerManager {
    fun registerTrigger(type: TriggerType, displayName: String, contextKeys: List<String>)
    fun getTriggerType(id: String): TriggerType?
    fun getAllTriggerTypes(): Collection<TriggerType>

    fun registerCondition(type: String, factory: (Map<String, Any>) -> ITriggerCondition)

    /** 注册第三方插件自定义条件类型（用于 ConditionEvaluator）。 */
    fun registerConditionType(type: String, impl: ICustomCondition)
    fun unregisterConditionType(type: String)

    fun dispatch(type: TriggerType, entity: LivingEntity, context: Map<String, Any>)

    fun isOnCooldown(entity: LivingEntity, key: String): Boolean
    fun getCooldownRemaining(entity: LivingEntity, key: String): Long
    fun setCooldown(entity: LivingEntity, key: String, duration: Long)
    fun clearCooldown(entity: LivingEntity, key: String)
}
