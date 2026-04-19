package priv.seventeen.artist.symphony.core.advanced.element

import org.bukkit.entity.LivingEntity
import priv.seventeen.artist.symphony.core.data.ElementAuraData
import priv.seventeen.artist.symphony.core.data.EntityRuntimeCache
import priv.seventeen.artist.symphony.core.storage.PlayerDataManager
import java.util.UUID
import java.util.concurrent.ConcurrentHashMap

/**
 * 元素附着系统 — 管理实体身上的元素状态。
 */
object ElementAuraSystem {
    var maxAuras = 2
    var defaultDuration = 8000L
    var defaultGauge = 1.0
    var decayRate = 0.125

    val registeredElements = mutableSetOf(
        "fire", "ice", "lightning", "poison", "holy", "dark", "water", "wind"
    )

    fun applyAura(entity: LivingEntity, element: String, gauge: Double = defaultGauge, duration: Long = defaultDuration, appliedBy: UUID? = null) {
        val auras = getAuras(entity)

        // 检查是否已有同元素附着
        val existing = auras[element]
        if (existing != null) {
            // 刷新：增加元素量和持续时间
            auras[element] = existing.copy(
                gauge = existing.gauge + gauge,
                applyTime = System.currentTimeMillis(),
                duration = duration
            )
        } else {
            // 检查是否超过最大附着数
            if (auras.size >= maxAuras) {
                // 移除最旧的
                val oldest = auras.entries.minByOrNull { it.value.applyTime }
                if (oldest != null) auras.remove(oldest.key)
            }
            auras[element] = ElementAuraData(element, gauge, System.currentTimeMillis(), duration, appliedBy)
        }

        // 检查元素反应
        checkReactions(entity, element, appliedBy)
    }

    fun getAura(entity: LivingEntity, element: String): ElementAuraData? {
        return getAuras(entity)[element]?.takeIf { !it.isExpired() }
    }

    fun removeAura(entity: LivingEntity, element: String) {
        getAuras(entity).remove(element)
    }

    fun getAllAuras(entity: LivingEntity): Map<String, ElementAuraData> {
        val auras = getAuras(entity)
        // 清理过期的
        auras.entries.removeIf { it.value.isExpired() }
        return auras.toMap()
    }

    fun consumeGauge(entity: LivingEntity, element: String, amount: Double) {
        val auras = getAuras(entity)
        val aura = auras[element] ?: return
        val remaining = aura.gauge - amount
        if (remaining <= 0) {
            auras.remove(element)
        } else {
            auras[element] = aura.copy(gauge = remaining)
        }
    }

    fun tickDecay() {
        // 由 RuntimeTickTask 每秒调用
        // 元素量自然衰减在 ElementAuraData.remainingGauge() 中计算
    }

    private fun getAuras(entity: LivingEntity): MutableMap<String, ElementAuraData> {
        val uuid = entity.uniqueId
        val playerData = PlayerDataManager.getData(uuid)
        if (playerData != null) return playerData.runtime.elementAuras
        return EntityRuntimeCache.get(uuid).elementAuras
    }

    private fun checkReactions(entity: LivingEntity, triggerElement: String, appliedBy: UUID?) {
        val auras = getAuras(entity)
        for ((auraElement, auraData) in auras.toMap()) {
            if (auraElement == triggerElement) continue
            ReactionSystem.tryReaction(entity, triggerElement, auraElement, appliedBy)
        }
    }
}
