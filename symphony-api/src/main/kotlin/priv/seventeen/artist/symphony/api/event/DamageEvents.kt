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

package priv.seventeen.artist.symphony.api.event

import org.bukkit.event.Cancellable
import org.bukkit.event.Event
import org.bukkit.event.HandlerList
import priv.seventeen.artist.symphony.api.damage.DamageRequest
import priv.seventeen.artist.symphony.api.damage.DamageResult
import priv.seventeen.artist.symphony.api.damage.DamageBreakdown
import java.util.UUID

abstract class CancellableSymphonyEvent : Event(), Cancellable {
    private var cancelled = false
    override fun isCancelled(): Boolean = cancelled
    override fun setCancelled(cancel: Boolean) {
        cancelled = cancel
    }
}

class SymphonyDamagePrepareEvent(
    val transactionId: UUID,
    val request: DamageRequest,
    val channels: MutableMap<String, Double>
) : CancellableSymphonyEvent() {
    override fun getHandlers(): HandlerList = HANDLERS
    companion object {
        @JvmStatic val HANDLERS = HandlerList()
        @JvmStatic fun getHandlerList(): HandlerList = HANDLERS
    }
}

class SymphonyDamageMitigationEvent(
    val transactionId: UUID,
    val request: DamageRequest,
    val channel: String,
    val inputDamage: Double,
    var outputDamage: Double
) : CancellableSymphonyEvent() {
    override fun getHandlers(): HandlerList = HANDLERS
    companion object {
        @JvmStatic val HANDLERS = HandlerList()
        @JvmStatic fun getHandlerList(): HandlerList = HANDLERS
    }
}

class SymphonyDamageApplyEvent(
    val transactionId: UUID,
    val request: DamageRequest,
    var finalDamage: Double
) : CancellableSymphonyEvent() {
    override fun getHandlers(): HandlerList = HANDLERS
    companion object {
        @JvmStatic val HANDLERS = HandlerList()
        @JvmStatic fun getHandlerList(): HandlerList = HANDLERS
    }
}

/**
 * 每次由攻击者发起的 Symphony 伤害事务都会在减伤前发布此事件。
 * [hit] 保存已经计算的命中判定，监听器可以覆盖该结果。
 */
class SymphonyHitCheckEvent(
    val transactionId: UUID,
    val request: DamageRequest,
    val accuracy: Double,
    val dodge: Double,
    val dodgeChance: Double,
    val roll: Double,
    var hit: Boolean
) : Event() {
    init {
        require(listOf(accuracy, dodge, dodgeChance, roll).all(Double::isFinite)) {
            "命中判定数值必须全部是有限数"
        }
        require(dodgeChance in 0.0..1.0 && roll >= 0.0 && roll < 1.0) {
            "命中判定概率或随机值超出有效范围"
        }
    }

    override fun getHandlers(): HandlerList = HANDLERS
    companion object {
        @JvmStatic val HANDLERS = HandlerList()
        @JvmStatic fun getHandlerList(): HandlerList = HANDLERS
    }
}

/**
 * Symphony 伤害结算的主事件。伤害明细已经缓存且不可变；监听器可以取消本次伤害，
 * 或替换 [finalDamage]，不会因此触发重新计算。
 */
class SymphonyDamageEvent(
    val transactionId: UUID,
    val request: DamageRequest,
    val breakdown: DamageBreakdown,
    var finalDamage: Double
) : CancellableSymphonyEvent() {
    /** 仅在命中判定成功后发布此事件。 */
    val hit: Boolean get() = true
    val critical: Boolean get() = breakdown.critical
    val triggeredAttributes get() = breakdown.triggeredAttributes
    val advantagedDamage: Double get() = breakdown.advantagedDamage
    val neutralDamage: Double get() = breakdown.neutralDamage
    val disadvantagedDamage: Double get() = breakdown.disadvantagedDamage

    override fun getHandlers(): HandlerList = HANDLERS
    companion object {
        @JvmStatic val HANDLERS = HandlerList()
        @JvmStatic fun getHandlerList(): HandlerList = HANDLERS
    }
}

class SymphonyDamageConfirmedEvent(
    val result: DamageResult
) : Event() {
    override fun getHandlers(): HandlerList = HANDLERS
    companion object {
        @JvmStatic val HANDLERS = HandlerList()
        @JvmStatic fun getHandlerList(): HandlerList = HANDLERS
    }
}
