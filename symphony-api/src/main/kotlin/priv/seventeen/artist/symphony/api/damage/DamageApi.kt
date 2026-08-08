/*
 * Copyright 2026 17Artist
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package priv.seventeen.artist.symphony.api.damage

import org.bukkit.entity.LivingEntity
import priv.seventeen.artist.symphony.api.attribute.AttributeKey
import java.util.UUID

data class DamageChannelAmount(
    val channel: String,
    val amount: Double
) {
    init {
        require(channel.isNotBlank()) { "伤害通道不能为空" }
        require(amount.isFinite() && amount >= 0.0) { "伤害数值必须是非负有限数" }
    }
}

data class DamageRequest(
    val attacker: LivingEntity?,
    val victim: LivingEntity,
    val channels: List<DamageChannelAmount>,
    val cause: String,
    val parentTransactionId: UUID? = null,
    val allowCritical: Boolean = true,
    val metadata: Map<String, String> = emptyMap()
) {
    init {
        require(channels.size <= 64) { "一次伤害请求最多包含 64 个伤害通道" }
        require(cause.isNotBlank() && cause.length <= 128) { "伤害原因的长度必须为 1 到 128 个字符" }
        require(metadata.size <= 64) { "伤害元数据最多包含 64 项" }
        require(metadata.all { (key, value) -> key.isNotBlank() && key.length <= 64 && value.length <= 256 }) {
            "伤害元数据包含无效的键或值"
        }
    }
}

enum class DamageResultState {
    CONFIRMED,
    CANCELLED,
    REJECTED
}

/** 命中的语义结果，与事务执行状态相互独立。 */
enum class DamageOutcome {
    HIT,
    MISSED,
    CANCELLED,
    REJECTED
}

/** 应用元素抗性和全局减伤之前的元素克制关系。 */
enum class DamageRelation {
    ADVANTAGED,
    NEUTRAL,
    DISADVANTAGED
}

enum class DamageAttributeOwner {
    ATTACKER,
    VICTIM
}

/** 说明本次伤害事务读取某项自定义属性的原因。 */
enum class DamageAttributeRole {
    ATTACK_POWER,
    CRITICAL_CHANCE,
    CRITICAL_DAMAGE,
    ACCURACY,
    DODGE,
    DEFENSE,
    PERCENT_PENETRATION,
    FLAT_PENETRATION,
    RESISTANCE,
    AMPLIFICATION,
    DAMAGE_REDUCTION,
    BLOCK_CHANCE,
    BLOCK_POWER,
    SHIELD_CAPACITY,
    LIFESTEAL,
    THORNS
}

data class DamageAttributeUse(
    val owner: DamageAttributeOwner,
    val entityId: UUID,
    val attribute: AttributeKey,
    val role: DamageAttributeRole,
    val value: Double,
    val channel: String? = null,
    /** 某项概率或数值实际改变了本次伤害事务时为 true。 */
    val activated: Boolean
) {
    init {
        require(value.isFinite()) { "伤害属性值必须是有限数" }
    }
}

data class DamageChannelResult(
    val channel: String,
    val requestedAmount: Double,
    val afterRelationAmount: Double,
    val afterCriticalAmount: Double,
    val afterMitigationAmount: Double,
    val finalAmount: Double,
    val relation: DamageRelation,
    val critical: Boolean,
    val reactions: Set<String> = emptySet(),
    val attributes: List<DamageAttributeUse> = emptyList()
) {
    init {
        require(channel.isNotBlank()) { "伤害通道不能为空" }
        require(listOf(requestedAmount, afterRelationAmount, afterCriticalAmount, afterMitigationAmount, finalAmount)
            .all { it.isFinite() && it >= 0.0 }) { "伤害通道中的所有数值都必须是非负有限数" }
    }
}

/**
 * 预先计算且不可变的事务明细。查询伤害通道、克制关系合计或已触发属性时，
 * 不会重新计算实体属性。
 */
class DamageBreakdown(
    channels: List<DamageChannelResult>,
    attributes: List<DamageAttributeUse>
) {
    val channels: List<DamageChannelResult> = channels.toList()
    val attributes: List<DamageAttributeUse> = attributes.toList()
    val byChannel: Map<String, DamageChannelResult> = this.channels.associateBy(DamageChannelResult::channel)
    private val totalsByRelation: Map<DamageRelation, Double> = DamageRelation.values().associateWith { relation ->
        this.channels.asSequence()
            .filter { it.relation == relation }
            .sumOf(DamageChannelResult::finalAmount)
    }
    val triggeredAttributes: Set<AttributeKey> = this.attributes.asSequence()
        .filter(DamageAttributeUse::activated)
        .mapTo(linkedSetOf(), DamageAttributeUse::attribute)
    val critical: Boolean = this.channels.any(DamageChannelResult::critical)
    val advantagedDamage: Double = totalsByRelation.getValue(DamageRelation.ADVANTAGED)
    val neutralDamage: Double = totalsByRelation.getValue(DamageRelation.NEUTRAL)
    val disadvantagedDamage: Double = totalsByRelation.getValue(DamageRelation.DISADVANTAGED)

    fun channel(id: String): DamageChannelResult? = byChannel[id]
    fun total(relation: DamageRelation): Double = totalsByRelation.getValue(relation)

    companion object {
        @JvmField
        val EMPTY = DamageBreakdown(emptyList(), emptyList())
    }
}

data class DamageResult(
    val transactionId: UUID,
    val parentTransactionId: UUID?,
    val state: DamageResultState,
    val requested: List<DamageChannelAmount>,
    val applied: List<DamageChannelAmount>,
    val finalDamage: Double,
    val critical: Boolean,
    val reason: String? = null,
    val breakdown: DamageBreakdown = DamageBreakdown.EMPTY,
    val outcome: DamageOutcome = when (state) {
        DamageResultState.CONFIRMED -> DamageOutcome.HIT
        DamageResultState.CANCELLED -> DamageOutcome.CANCELLED
        DamageResultState.REJECTED -> DamageOutcome.REJECTED
    }
) {
    val triggeredAttributes: Set<AttributeKey> get() = breakdown.triggeredAttributes
    val hit: Boolean get() = outcome == DamageOutcome.HIT
    val missed: Boolean get() = outcome == DamageOutcome.MISSED
}

interface DamageService {
    /**
     * 使用攻击者当前的伤害属性执行一次普通 Symphony 攻击。
     * 该调用与游戏中的普通攻击使用相同的命中、伤害通道、暴击、减伤、回调和事件流程。
     * 双参数重载使用 `1.0` 伤害倍率。
     */
    fun attack(
        attacker: LivingEntity,
        victim: LivingEntity
    ): DamageResult = attack(attacker, victim, 1.0)

    /**
     * 将每个输入伤害通道乘以 [damageMultiplier] 后执行一次普通 Symphony 攻击。
     * 倍率会在元素反应、暴击和减伤之前应用。
     */
    fun attack(
        attacker: LivingEntity,
        victim: LivingEntity,
        damageMultiplier: Double
    ): DamageResult

    fun damage(request: DamageRequest): DamageResult

    /** 便于 Java/Kotlin 调用的主动单通道 Symphony 伤害入口。 */
    fun damage(
        attacker: LivingEntity?,
        victim: LivingEntity,
        channel: String,
        amount: Double
    ): DamageResult = damage(attacker, victim, channel, amount, "api", true, emptyMap())

    fun damage(
        attacker: LivingEntity?,
        victim: LivingEntity,
        channel: String,
        amount: Double,
        cause: String
    ): DamageResult = damage(attacker, victim, channel, amount, cause, true, emptyMap())

    fun damage(
        attacker: LivingEntity?,
        victim: LivingEntity,
        channel: String,
        amount: Double,
        cause: String,
        allowCritical: Boolean,
        metadata: Map<String, String>
    ): DamageResult = damage(
        DamageRequest(
            attacker = attacker,
            victim = victim,
            channels = listOf(DamageChannelAmount(channel, amount)),
            cause = cause,
            allowCritical = allowCritical,
            metadata = metadata
        )
    )

    fun recentTransactions(entity: LivingEntity, limit: Int = 20): List<DamageResult>
    fun shield(entity: LivingEntity): Double
    fun setShield(entity: LivingEntity, amount: Double): Double
}
