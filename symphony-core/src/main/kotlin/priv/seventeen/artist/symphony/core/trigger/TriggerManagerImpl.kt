package priv.seventeen.artist.symphony.core.trigger

import org.bukkit.entity.LivingEntity
import priv.seventeen.artist.symphony.api.trigger.*
import java.util.concurrent.ConcurrentHashMap

class TriggerManagerImpl : ITriggerManager {
    private val triggerMeta = ConcurrentHashMap<String, TriggerMeta>()
    private val conditionFactories = ConcurrentHashMap<String, (Map<String, Any>) -> ITriggerCondition>()

    data class TriggerMeta(val type: TriggerType, val displayName: String, val contextKeys: List<String>)

    override fun registerTrigger(type: TriggerType, displayName: String, contextKeys: List<String>) {
        triggerMeta[type.id] = TriggerMeta(type, displayName, contextKeys)
    }

    override fun getTriggerType(id: String): TriggerType? {
        return try { TriggerType.of(id) } catch (e: Exception) { null }
    }

    override fun getAllTriggerTypes(): Collection<TriggerType> = TriggerType.getAll()

    override fun registerCondition(type: String, factory: (Map<String, Any>) -> ITriggerCondition) {
        conditionFactories[type] = factory
    }

    override fun registerConditionType(type: String, impl: ICustomCondition) {
        CustomConditionRegistry.register(type, impl)
    }

    override fun unregisterConditionType(type: String) {
        CustomConditionRegistry.unregister(type)
    }

    fun createCondition(type: String, params: Map<String, Any>): ITriggerCondition? {
        return conditionFactories[type]?.invoke(params)
    }

    override fun dispatch(type: TriggerType, entity: LivingEntity, context: Map<String, Any>) {
        TriggerDispatcher.dispatch(type, entity) {
            context.forEach { (k, v) -> set(k, v) }
        }
    }

    override fun isOnCooldown(entity: LivingEntity, key: String): Boolean {
        return CooldownManager.isOnCooldown(entity.uniqueId, key)
    }

    override fun getCooldownRemaining(entity: LivingEntity, key: String): Long {
        return CooldownManager.getRemaining(entity.uniqueId, key)
    }

    override fun setCooldown(entity: LivingEntity, key: String, duration: Long) {
        CooldownManager.setCooldown(entity.uniqueId, key, duration)
    }

    override fun clearCooldown(entity: LivingEntity, key: String) {
        CooldownManager.clearCooldown(entity.uniqueId, key)
    }
}
