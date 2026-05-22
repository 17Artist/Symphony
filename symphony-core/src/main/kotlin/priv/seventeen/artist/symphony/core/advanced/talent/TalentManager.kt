package priv.seventeen.artist.symphony.core.advanced.talent

import org.bukkit.entity.Player
import priv.seventeen.artist.blink.BlinkLog
import priv.seventeen.artist.symphony.api.attribute.AttributeModifier
import priv.seventeen.artist.symphony.api.attribute.Operation
import priv.seventeen.artist.symphony.core.attribute.AttributeCache
import priv.seventeen.artist.symphony.core.attribute.AttributeCalculator
import priv.seventeen.artist.symphony.core.script.AriaCallbackManager
import priv.seventeen.artist.symphony.core.storage.PlayerDataManager
import java.util.concurrent.ConcurrentHashMap

/**
 * 天赋门系统 — 属性达到阈值解锁全新机制。
 */
object TalentManager {
    private val talents = ConcurrentHashMap<String, TalentDefinition>()
    var maxSlots = 5

    fun register(talent: TalentDefinition) {
        talents[talent.id] = talent
    }

    fun unregister(id: String): Boolean = talents.remove(id) != null

    fun getAll(): Collection<TalentDefinition> = talents.values

    fun get(id: String): TalentDefinition? = talents[id]

    fun clear() {
        talents.clear()
    }

    /**
     * 检查并更新玩家的天赋门状态。
     */
    fun checkTalents(player: Player) {
        val data = PlayerDataManager.getData(player.uniqueId) ?: return
        var changed = false

        for (talent in talents.values) {
            val wasUnlocked = talent.id in data.persistent.unlockedTalents
            val isUnlocked = evaluateGate(player, talent.gate)

            if (isUnlocked && !wasUnlocked) {
                data.persistent.unlockedTalents.add(talent.id)
                data.dirty = true
                changed = true
                BlinkLog.info("${player.name} 解锁天赋门: ${talent.displayName}")
                // 激活回调
                val effectId = "talent:${talent.id}:effect"
                if (AriaCallbackManager.has(effectId)) {
                    AriaCallbackManager.invoke(effectId, player)
                }
            } else if (!isUnlocked && wasUnlocked) {
                data.persistent.unlockedTalents.remove(talent.id)
                data.dirty = true
                changed = true
                // 失活回调
                val deactivateId = "talent:${talent.id}:on_deactivate"
                if (AriaCallbackManager.has(deactivateId)) {
                    AriaCallbackManager.invoke(deactivateId, player)
                }
                // 兜底清理：移除该天赋通过 effect 回调添加的永久 buff
                val talentSource = "talent:${talent.id}"
                priv.seventeen.artist.symphony.core.data.BuffOps.removeWhere(
                    player, data.runtime,
                    priv.seventeen.artist.symphony.api.event.BuffExpireEvent.ExpireReason.MANUAL_REMOVE
                ) { it.source == talentSource }
                data.persistent.savedBuffs.removeAll { it.source == talentSource }
            }
        }

        if (changed) {
            AttributeCache.markDirty(player.uniqueId)
        }
    }

    /**
     * 获取天赋门提供的属性修改器。
     */
    fun getTalentModifiers(player: Player): List<AttributeModifier> {
        val data = PlayerDataManager.getData(player.uniqueId) ?: return emptyList()
        val modifiers = mutableListOf<AttributeModifier>()

        for (talentId in data.persistent.unlockedTalents) {
            val talent = talents[talentId] ?: continue
            for ((attrId, attr) in talent.passiveAttributes) {
                val op = if (attr.operation.uppercase() == "PERCENT") Operation.PERCENT else Operation.FLAT
                modifiers.add(AttributeModifier(attrId, op, attr.value, "talent:$talentId"))
            }
        }

        return modifiers
    }

    fun isUnlocked(player: Player, talentId: String): Boolean {
        return PlayerDataManager.getData(player.uniqueId)?.persistent?.unlockedTalents?.contains(talentId) ?: false
    }

    fun getStatus(player: Player): Map<String, TalentStatus> {
        val result = mutableMapOf<String, TalentStatus>()
        for (talent in talents.values) {
            val unlocked = isUnlocked(player, talent.id)
            val progress = getProgress(player, talent)
            result[talent.id] = TalentStatus(talent, unlocked, progress)
        }
        return result
    }

    private fun evaluateGate(player: Player, gate: TalentGate): Boolean {
        return when (gate.type.uppercase()) {
            "AND", "ALL" -> gate.conditions.isNotEmpty() && gate.conditions.all { evaluateCondition(player, it) }
            "OR", "ANY" -> gate.conditions.isNotEmpty() && gate.conditions.any { evaluateCondition(player, it) }
            else -> {
                val attr = gate.attribute ?: return false
                val value = AttributeCalculator.getValue(player, attr)
                compareValue(value, gate.threshold, gate.operator)
            }
        }
    }

    private fun evaluateCondition(player: Player, condition: TalentGateCondition): Boolean {
        val value = AttributeCalculator.getValue(player, condition.attribute)
        return compareValue(value, condition.threshold, condition.operator)
    }

    private fun compareValue(value: Double, threshold: Double, operator: String): Boolean {
        return when (operator) {
            ">=" -> value >= threshold
            ">" -> value > threshold
            "<=" -> value <= threshold
            "<" -> value < threshold
            "==" -> value == threshold
            else -> value >= threshold
        }
    }

    private fun getProgress(player: Player, talent: TalentDefinition): Double {
        val gate = talent.gate
        val attr = gate.attribute ?: return 0.0
        val value = AttributeCalculator.getValue(player, attr)
        return if (gate.threshold > 0) (value / gate.threshold).coerceIn(0.0, 1.0) else 1.0
    }

    data class TalentStatus(val definition: TalentDefinition, val unlocked: Boolean, val progress: Double)
}
