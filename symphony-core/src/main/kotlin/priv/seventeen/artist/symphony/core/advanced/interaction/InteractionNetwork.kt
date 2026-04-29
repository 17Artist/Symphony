package priv.seventeen.artist.symphony.core.advanced.interaction

import org.bukkit.attribute.Attribute
import org.bukkit.entity.LivingEntity
import priv.seventeen.artist.symphony.core.attribute.AttributeCache
import priv.seventeen.artist.symphony.core.attribute.AttributeRegistry
import priv.seventeen.artist.symphony.core.script.AriaCallbackManager
import priv.seventeen.artist.symphony.core.storage.PlayerDataManager
import java.util.UUID
import java.util.concurrent.ConcurrentHashMap

/**
 * 属性交互网络 — 属性之间的有向图关系。
 * 在属性重算后执行，可能需要多轮迭代。
 */
object InteractionNetwork {
    private val interactions = ConcurrentHashMap<String, InteractionDefinition>()
    var maxIterations = 3

    fun register(interaction: InteractionDefinition) {
        interactions[interaction.id] = interaction
    }

    fun unregister(id: String) {
        interactions.remove(id)
    }

    fun getAll(): Collection<InteractionDefinition> = interactions.values

    fun clear() {
        interactions.clear()
    }

    /**
     * 在属性重算后执行交互网络。
     * 可能需要多轮迭代（CONVERSION 结果可能触发 THRESHOLD）。
     */
    fun process(entity: LivingEntity) {
        val entityId = entity.uniqueId
        val applied = mutableSetOf<String>()
        var changed = true
        var iteration = 0

        while (changed && iteration < maxIterations) {
            changed = false
            iteration++

            for (interaction in interactions.values) {
                if (interaction.id in applied) continue
                val result = processInteraction(entityId, interaction, entity)
                if (result) {
                    applied.add(interaction.id)
                    changed = true
                }
            }
        }
    }

    private fun processInteraction(entityId: UUID, def: InteractionDefinition, entity: LivingEntity): Boolean {
        return when (def.type) {
            InteractionType.CONVERSION -> processConversion(entityId, def)
            InteractionType.OVERFLOW -> processOverflow(entityId, def)
            InteractionType.SYNERGY -> processSynergy(entityId, def)
            InteractionType.CONFLICT -> processConflict(entityId, def)
            InteractionType.AMPLIFY -> processAmplify(entityId, def, entity)
            InteractionType.THRESHOLD -> processThreshold(entityId, def, entity)
            InteractionType.DIMINISH -> processDiminish(entityId, def)
        }
    }

    private fun processConversion(entityId: UUID, def: InteractionDefinition): Boolean {
        val source = def.source ?: return false
        val target = def.target ?: return false
        val sourceValue = AttributeCache.get(entityId, source) ?: return false
        val bonus = sourceValue * def.ratio
        val current = AttributeCache.get(entityId, target) ?: 0.0
        val newValue = current + bonus
        val attr = AttributeRegistry.get(target)
        val clamped = if (attr != null) newValue.coerceIn(attr.minValue, attr.maxValue) else newValue
        if (clamped != current) {
            AttributeCache.set(entityId, target, clamped)
            return true
        }
        return false
    }

    private fun processOverflow(entityId: UUID, def: InteractionDefinition): Boolean {
        val source = def.source ?: return false
        val target = def.target ?: return false
        val sourceValue = AttributeCache.get(entityId, source) ?: return false
        if (sourceValue <= def.threshold) return false
        val overflow = (sourceValue - def.threshold) * def.ratio
        val current = AttributeCache.get(entityId, target) ?: 0.0
        AttributeCache.set(entityId, target, current + overflow)
        AttributeCache.set(entityId, source, def.threshold)
        return true
    }

    private fun processSynergy(entityId: UUID, def: InteractionDefinition): Boolean {
        if (def.attributes.size < 2) return false
        val allAbove = def.attributes.all { attrId ->
            (AttributeCache.get(entityId, attrId) ?: 0.0) >= def.threshold
        }
        if (!allAbove) return false
        var changed = false
        for (attrId in def.attributes) {
            val current = AttributeCache.get(entityId, attrId) ?: continue
            val boosted = current * (1.0 + def.bonus)
            if (boosted != current) {
                AttributeCache.set(entityId, attrId, boosted)
                changed = true
            }
        }
        return changed
    }

    private fun processConflict(entityId: UUID, def: InteractionDefinition): Boolean {
        val attrA = def.attributeA ?: return false
        val attrB = def.attributeB ?: return false
        val valueA = AttributeCache.get(entityId, attrA) ?: return false
        if (valueA < def.thresholdA) return false
        val valueB = AttributeCache.get(entityId, attrB) ?: return false
        val penalized = valueB * (1.0 - def.penaltyB)
        if (penalized != valueB) {
            AttributeCache.set(entityId, attrB, penalized)
            return true
        }
        return false
    }

    private fun processAmplify(entityId: UUID, def: InteractionDefinition, entity: LivingEntity): Boolean {
        val target = def.target ?: return false

        // 优先使用脚本条件
        val conditionId = "interaction:${def.id}:condition"
        val conditionMet = if (AriaCallbackManager.has(conditionId)) {
            AriaCallbackManager.invokeCondition(conditionId, entity)
        } else {
            // fallback: 基于生命值百分比的默认条件
            val hp = entity.health
            val maxHp = entity.getAttribute(Attribute.GENERIC_MAX_HEALTH)?.value ?: 20.0
            hp / maxHp < (def.threshold.takeIf { it in 0.01..1.0 } ?: 0.3)
        }

        if (!conditionMet) return false
        val current = AttributeCache.get(entityId, target) ?: return false
        val amplified = current * def.multiplier
        if (amplified != current) {
            AttributeCache.set(entityId, target, amplified)
            return true
        }
        return false
    }

    private fun processThreshold(entityId: UUID, def: InteractionDefinition, entity: LivingEntity): Boolean {
        val source = def.source ?: return false
        val value = AttributeCache.get(entityId, source) ?: return false
        val data = PlayerDataManager.getData(entityId) ?: return false
        val thresholdKey = "threshold:${def.id}"
        val wasActive = thresholdKey in data.runtime.activeThresholds
        val isActive = value >= def.threshold

        if (isActive && !wasActive) {
            data.runtime.activeThresholds.add(thresholdKey)
            // 激活时执行 effect 回调
            val effectId = "interaction:${def.id}:effect"
            if (AriaCallbackManager.has(effectId)) {
                AriaCallbackManager.invoke(effectId, entity, value)
            }
            return true
        } else if (!isActive && wasActive) {
            data.runtime.activeThresholds.remove(thresholdKey)
            return true
        }
        return false
    }

    private fun processDiminish(entityId: UUID, def: InteractionDefinition): Boolean {
        val source = def.source ?: return false
        val value = AttributeCache.get(entityId, source) ?: return false
        val attr = AttributeRegistry.get(source) ?: return false
        // 递减收益曲线只应用一次，且只对超过阈值的部分
        val threshold = def.threshold.takeIf { it > 0 } ?: 0.0
        if (value <= threshold) return false
        val excess = value - threshold
        val diminished = threshold + excess / (excess + 100.0) * 100.0
        val clamped = diminished.coerceIn(attr.minValue, attr.maxValue)
        if (clamped != value) {
            AttributeCache.set(entityId, source, clamped)
            return true
        }
        return false
    }
}
