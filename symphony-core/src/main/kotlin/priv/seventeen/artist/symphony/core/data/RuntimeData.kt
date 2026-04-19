package priv.seventeen.artist.symphony.core.data

import priv.seventeen.artist.symphony.api.attribute.Operation
import java.util.UUID

class RuntimeData {
    val activeBuffs: MutableList<ActiveBuff> = mutableListOf()
    val tempAffixes: MutableList<TempAffix> = mutableListOf()
    val activeVirtualSets: MutableMap<String, VirtualSetRuntime> = mutableMapOf()
    val statusStacks: MutableMap<String, StatusStackData> = mutableMapOf()
    val elementAuras: MutableMap<String, ElementAuraData> = mutableMapOf()
    val cooldowns: MutableMap<String, Long> = mutableMapOf()
    var inCombat: Boolean = false
    var combatStartTime: Long = 0
    var lastCombatActionTime: Long = 0
    var comboCount: Int = 0
    var lastComboTime: Long = 0
    val dynamicTriggers: MutableMap<String, DynamicTriggerData> = mutableMapOf()
    val activeResonances: MutableSet<String> = mutableSetOf()
    val activeEnvironmentModifiers: MutableSet<String> = mutableSetOf()
    val activeThresholds: MutableSet<String> = mutableSetOf()
    val activeAmplifiers: MutableSet<String> = mutableSetOf()
    var currentMana: Double = 0.0
    var currentHealth: Double = 0.0
    val activeShields: MutableList<ShieldData> = mutableListOf()
}

data class ActiveBuff(
    val id: String,
    val attribute: String,
    val operation: Operation,
    val value: Double,
    val expireTime: Long,
    val source: String,
    val stackable: Boolean = false,
    val maxStacks: Int = 1,
    var currentStacks: Int = 1
) {
    fun isExpired(): Boolean = expireTime != -1L && System.currentTimeMillis() > expireTime
    fun remainingMs(): Long = if (expireTime == -1L) Long.MAX_VALUE else expireTime - System.currentTimeMillis()
}

data class TempAffix(
    val uuid: UUID,
    val affixId: String,
    val level: Int,
    val params: Map<String, Any>,
    val source: String,
    val expireTime: Long,
    val slot: TempAffixSlot
) {
    fun isExpired(): Boolean = expireTime != -1L && System.currentTimeMillis() > expireTime
}

enum class TempAffixSlot {
    PLAYER, MAIN_HAND, ARMOR, ANY
}

data class VirtualSetRuntime(
    val setId: String,
    var totalPieces: Int,
    val sources: MutableMap<String, VirtualSetSource> = mutableMapOf()
)

data class VirtualSetSource(
    val source: String,
    val pieces: Int,
    val expireTime: Long
)

data class StatusStackData(
    val statusId: String,
    var stacks: Int,
    val stackTimestamps: MutableList<Long>,
    var lastRefreshTime: Long,
    val appliedBy: UUID?
)

data class ElementAuraData(
    val element: String,
    var gauge: Double,
    val applyTime: Long,
    val duration: Long,
    val appliedBy: UUID?
) {
    fun isExpired(): Boolean = System.currentTimeMillis() > applyTime + duration
    fun remainingGauge(decayRate: Double): Double {
        val elapsed = (System.currentTimeMillis() - applyTime) / 1000.0
        return maxOf(0.0, gauge - elapsed * decayRate)
    }
}

data class DynamicTriggerData(
    val triggerId: String,
    val triggerType: String,
    val source: String,
    val conditions: List<Map<String, Any>>,
    val actions: List<Map<String, Any>>
)

data class ShieldData(
    val id: String,
    var amount: Double,
    val maxAmount: Double,
    val expireTime: Long,
    val source: String,
    val element: String? = null
) {
    fun isExpired(): Boolean = expireTime != -1L && System.currentTimeMillis() > expireTime
}
