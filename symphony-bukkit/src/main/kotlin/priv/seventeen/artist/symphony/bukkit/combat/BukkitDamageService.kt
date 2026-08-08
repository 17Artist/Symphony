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

package priv.seventeen.artist.symphony.bukkit.combat

import org.bukkit.Bukkit
import org.bukkit.attribute.Attribute
import org.bukkit.entity.LivingEntity
import org.bukkit.entity.Projectile
import org.bukkit.event.entity.EntityDamageByEntityEvent
import org.bukkit.event.entity.EntityDamageEvent
import org.bukkit.plugin.Plugin
import org.bukkit.scheduler.BukkitTask
import priv.seventeen.artist.symphony.api.attribute.AttributeKey
import priv.seventeen.artist.symphony.api.attribute.AttributeSnapshot
import priv.seventeen.artist.symphony.api.damage.DamageChannelAmount
import priv.seventeen.artist.symphony.api.damage.DamageOutcome
import priv.seventeen.artist.symphony.api.damage.DamageAttributeOwner
import priv.seventeen.artist.symphony.api.damage.DamageAttributeRole
import priv.seventeen.artist.symphony.api.damage.DamageAttributeUse
import priv.seventeen.artist.symphony.api.damage.DamageBreakdown
import priv.seventeen.artist.symphony.api.damage.DamageChannelResult
import priv.seventeen.artist.symphony.api.damage.DamageRelation
import priv.seventeen.artist.symphony.api.damage.DamageRequest
import priv.seventeen.artist.symphony.api.damage.DamageResult
import priv.seventeen.artist.symphony.api.damage.DamageResultState
import priv.seventeen.artist.symphony.api.damage.DamageService
import priv.seventeen.artist.symphony.api.event.SymphonyDamageApplyEvent
import priv.seventeen.artist.symphony.api.event.SymphonyDamageConfirmedEvent
import priv.seventeen.artist.symphony.api.event.SymphonyDamageEvent
import priv.seventeen.artist.symphony.api.event.SymphonyDamageMitigationEvent
import priv.seventeen.artist.symphony.api.event.SymphonyDamagePrepareEvent
import priv.seventeen.artist.symphony.api.event.SymphonyHitCheckEvent
import priv.seventeen.artist.symphony.bukkit.service.BukkitAttributeService
import priv.seventeen.artist.symphony.bukkit.service.BukkitTriggerService
import priv.seventeen.artist.symphony.engine.attribute.AttributeStateStore
import priv.seventeen.artist.symphony.engine.damage.ArmorFormula
import priv.seventeen.artist.symphony.engine.damage.ElementalDamageFormula
import priv.seventeen.artist.symphony.engine.damage.composeDamageAttributeChannels
import priv.seventeen.artist.symphony.engine.damage.HitChanceFormula
import priv.seventeen.artist.symphony.engine.definition.DefinitionRepository
import priv.seventeen.artist.symphony.engine.trigger.CombatAttackConfirmedTrigger
import priv.seventeen.artist.symphony.engine.trigger.CombatAttackPrepareTrigger
import priv.seventeen.artist.symphony.engine.trigger.CombatBlockConfirmedTrigger
import priv.seventeen.artist.symphony.engine.trigger.CombatCriticalTrigger
import priv.seventeen.artist.symphony.engine.trigger.CombatDamageConfirmedTrigger
import priv.seventeen.artist.symphony.engine.trigger.CombatDamageDealtTrigger
import priv.seventeen.artist.symphony.engine.trigger.CombatDamageTakenTrigger
import priv.seventeen.artist.symphony.engine.trigger.CombatDeathTrigger
import priv.seventeen.artist.symphony.engine.trigger.CombatDodgeTrigger
import priv.seventeen.artist.symphony.engine.trigger.CombatKillTrigger
import priv.seventeen.artist.symphony.engine.trigger.CombatLowHealthTrigger
import priv.seventeen.artist.symphony.engine.trigger.CombatEnterTrigger
import priv.seventeen.artist.symphony.engine.trigger.CombatLeaveTrigger
import priv.seventeen.artist.symphony.engine.trigger.CombatMeleeTrigger
import priv.seventeen.artist.symphony.engine.trigger.CombatRangedTrigger
import priv.seventeen.artist.symphony.engine.trigger.EntityTriggerContext
import java.util.ArrayDeque
import java.util.Collections
import java.util.IdentityHashMap
import java.util.UUID
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.ThreadLocalRandom
import kotlin.math.max
import kotlin.math.min

class BukkitDamageService(
    private val plugin: Plugin,
    private val definitions: DefinitionRepository,
    private val attributes: BukkitAttributeService,
    private val stateStore: AttributeStateStore,
    private val triggers: BukkitTriggerService,
    private val combatEnabled: Boolean,
    private val mappedEnvironmentalCauses: Map<String, String>,
    private val minimumDamage: Double,
    private val maxDepth: Int,
    private val confirmationDelayTicks: Long,
    private val random: () -> Double = { ThreadLocalRandom.current().nextDouble() }
) : DamageService {
    private data class DirectCall(
        val request: DamageRequest,
        val attributeTracker: AttributeTracker? = null,
        var result: DamageResult? = null
    )

    private data class PendingDamage(
        val request: DamageRequest,
        val transactionId: UUID,
        val requested: List<DamageChannelAmount>,
        val breakdown: DamageBreakdown,
        val blocked: Boolean,
        val dodged: Boolean,
        val shieldAbsorbed: Double,
        val reactionPlan: DamageReactionPlan,
        val attacker: LivingEntity?,
        val victim: LivingEntity,
        val directCall: DirectCall?
    )

    private data class ChannelDraft(
        val channel: String,
        val requested: Double,
        val afterRelation: Double,
        val afterCritical: Double,
        val afterMitigation: Double,
        val relation: DamageRelation,
        val critical: Boolean,
        val reactions: Set<String>
    )

    private data class AttributeUseKey(
        val owner: DamageAttributeOwner,
        val entityId: UUID,
        val attribute: AttributeKey,
        val role: DamageAttributeRole,
        val channel: String?
    )

    private inner class AttributeTracker {
        private val snapshots = hashMapOf<UUID, AttributeSnapshot>()
        private val values = hashMapOf<Pair<UUID, AttributeKey>, Double>()
        private val uses = linkedMapOf<AttributeUseKey, DamageAttributeUse>()

        fun value(entity: LivingEntity, key: AttributeKey): Double =
            values.getOrPut(entity.uniqueId to key) {
                snapshots.getOrPut(entity.uniqueId) { attributes.snapshot(entity) }.values[key]
                    ?: definitions.current().snapshot.attributes[key]?.definition?.base
                    ?: 0.0
            }

        fun use(
            entity: LivingEntity,
            key: AttributeKey,
            owner: DamageAttributeOwner,
            role: DamageAttributeRole,
            channel: String? = null,
            activated: Boolean
        ): Double {
            val value = value(entity, key)
            val useKey = AttributeUseKey(owner, entity.uniqueId, key, role, channel)
            val previous = uses[useKey]
            uses[useKey] = DamageAttributeUse(owner, entity.uniqueId, key, role, value, channel, activated || previous?.activated == true)
            return value
        }

        fun snapshot(): List<DamageAttributeUse> = uses.values.toList()
    }

    private val directStack = ThreadLocal.withInitial { ArrayDeque<DirectCall>() }
    private val pendingEvents = Collections.synchronizedMap(IdentityHashMap<EntityDamageEvent, PendingDamage>())
    private val history = ConcurrentHashMap<UUID, ArrayDeque<DamageResult>>()
    private val shields = ConcurrentHashMap<UUID, Double>()
    private val combatUntil = ConcurrentHashMap<UUID, Long>()
    private var combatTask: BukkitTask? = null
    @Volatile
    var reactionPlanner: DamageReactionPlanner = DamageReactionPlanner.NONE

    fun start() {
        if (!combatEnabled || combatTask != null) return
        combatTask = Bukkit.getScheduler().runTaskTimer(plugin, Runnable(::expireCombatStates), 20L, 20L)
    }

    fun isInCombat(entity: LivingEntity): Boolean = combatRemainingMillis(entity) > 0L

    fun combatRemainingMillis(entity: LivingEntity): Long =
        ((combatUntil[entity.uniqueId] ?: 0L) - System.currentTimeMillis()).coerceAtLeast(0L)

    override fun attack(attacker: LivingEntity, victim: LivingEntity, damageMultiplier: Double): DamageResult {
        requirePrimaryThread()
        val tracker = AttributeTracker()
        val request = DamageRequest(
            attacker = attacker,
            victim = victim,
            channels = attributeAttackChannels(attacker, tracker, damageMultiplier),
            cause = "api:attack"
        )
        return damageDirect(request, tracker)
    }

    override fun damage(request: DamageRequest): DamageResult = damageDirect(request)

    private fun damageDirect(request: DamageRequest, attributeTracker: AttributeTracker? = null): DamageResult {
        requirePrimaryThread()
        if (!combatEnabled) return rejected(request, "Symphony 战斗系统未启用")
        val stack = directStack.get()
        if (stack.size >= maxDepth) return rejected(request, "伤害事务超过最大深度 $maxDepth")
        if (request.channels.isEmpty()) return rejected(request, "伤害请求不包含任何通道")
        val call = DirectCall(request, attributeTracker)
        stack.addLast(call)
        // 显式发起的 Symphony 伤害是一项新的权威事务。若受害者仍处于原版受伤无敌时间，
        // Bukkit 可能在 EntityDamageEvent 前直接丢弃伤害，导致调用方既拿不到事务，
        // 也收不到任何 Symphony 事件。伤害被拒绝或取消时恢复之前的无敌时间；
        // 命中确认后则由本次伤害建立新的原版无敌时间。
        val previousNoDamageTicks = request.victim.noDamageTicks
        val previousLastDamage = request.victim.lastDamage
        var result: DamageResult? = null
        try {
            request.victim.noDamageTicks = 0
            request.victim.lastDamage = 0.0
            val raw = request.channels.sumOf { it.amount }.coerceAtLeast(0.0)
            if (request.attacker != null) request.victim.damage(raw, request.attacker) else request.victim.damage(raw)
            result = call.result ?: rejected(request, "Bukkit 未产生伤害事件")
            return requireNotNull(result)
        } catch (error: Throwable) {
            return rejected(request, error.message ?: "Bukkit 伤害调用失败")
        } finally {
            if (result?.state != DamageResultState.CONFIRMED && request.victim.isValid) {
                request.victim.noDamageTicks = previousNoDamageTicks
                request.victim.lastDamage = previousLastDamage
            }
            stack.removeLast()
            if (stack.isEmpty()) directStack.remove()
        }
    }

    override fun recentTransactions(entity: LivingEntity, limit: Int): List<DamageResult> {
        require(limit in 1..100) { "limit 必须位于 1 到 100 之间" }
        val deque = history[entity.uniqueId] ?: return emptyList()
        return synchronized(deque) { deque.toList().takeLast(limit).reversed() }
    }

    override fun shield(entity: LivingEntity): Double {
        val maximum = attributes.value(entity, SHIELD_CAPACITY).coerceAtLeast(0.0)
        return shields.compute(entity.uniqueId) { _, current -> (current ?: maximum).coerceIn(0.0, maximum) } ?: 0.0
    }

    override fun setShield(entity: LivingEntity, amount: Double): Double {
        requirePrimaryThread()
        require(amount.isFinite() && amount >= 0.0) { "护盾值必须是非负有限数" }
        val value = amount.coerceAtMost(attributes.value(entity, SHIELD_CAPACITY).coerceAtLeast(0.0))
        shields[entity.uniqueId] = value
        return value
    }

    fun prepare(event: EntityDamageEvent) {
        requirePrimaryThread()
        val victim = event.entity as? LivingEntity ?: return
        val direct = directStack.get().lastOrNull()
        val attacker = direct?.request?.attacker ?: resolveAttacker(event)
        if (direct == null && !shouldTakeOver(attacker, victim, event.cause)) return

        val transactionId = UUID.randomUUID()
        val tracker = direct?.attributeTracker ?: AttributeTracker()
        val mappedChannel = mappedEnvironmentalCauses[event.cause.name]
            ?: mappedEnvironmentalCauses[event.cause.name.lowercase()]
        val request = direct?.request ?: DamageRequest(
            attacker = attacker,
            victim = victim,
            channels = if (mappedChannel == null && attacker != null && hasCombatSources(attacker)) {
                attributeAttackChannels(attacker, tracker).ifEmpty {
                    listOf(DamageChannelAmount("physical", event.damage))
                }
            } else {
                listOf(DamageChannelAmount(mappedChannel ?: "physical", event.damage))
            },
            cause = "bukkit:${event.cause.name.lowercase()}"
        )

        val channels = linkedMapOf<String, Double>()
        request.channels.forEach { channels.merge(it.channel, it.amount, Double::plus) }
        if (attacker != null) {
            val prepareContext = context(
                transactionId,
                attacker,
                victim,
                mapOf("attacker" to attacker, "victim" to victim, "channels" to channels.keys.toList(), "amount" to channels.values.sum())
            )
            val triggerResult = triggers.dispatch(CombatAttackPrepareTrigger, prepareContext)
            if (triggerResult.cancelled) {
                event.isCancelled = true
                val result = cancelled(transactionId, request, "战斗攻击准备回调取消了本次伤害")
                direct?.result = result
                remember(result, attacker, victim)
                return
            }
        }
        val prepareEvent = SymphonyDamagePrepareEvent(transactionId, request, channels)
        Bukkit.getPluginManager().callEvent(prepareEvent)
        if (prepareEvent.isCancelled || event.isCancelled) {
            event.isCancelled = true
            val result = cancelled(transactionId, request, "伤害准备事件取消了本次伤害")
            direct?.result = result
            remember(result, attacker, victim)
            return
        }
        require(channels.values.all { it.isFinite() && it >= 0.0 }) { "伤害准备事件产生了无效的通道数值" }
        val preparedChannels = channels.toMap()
        val reactionPlan = reactionPlanner.prepare(request, victim, channels)
        require(reactionPlan.channels.values.all { it.isFinite() && it >= 0.0 }) {
            "元素反应产生了无效的通道伤害值"
        }
        channels.clear()
        channels.putAll(reactionPlan.channels)

        if (attacker != null) {
            val dodge = tracker.value(victim, DODGE)
            val accuracy = tracker.value(attacker, ACCURACY)
            val dodgeChance = HitChanceFormula.dodgeChance(accuracy, dodge)
            val roll = random()
            val hitCheck = SymphonyHitCheckEvent(
                transactionId,
                request,
                accuracy,
                dodge,
                dodgeChance,
                roll,
                hit = roll >= dodgeChance
            )
            Bukkit.getPluginManager().callEvent(hitCheck)
            val dodged = !hitCheck.hit
            tracker.use(victim, DODGE, DamageAttributeOwner.VICTIM, DamageAttributeRole.DODGE, activated = dodged)
            tracker.use(attacker, ACCURACY, DamageAttributeOwner.ATTACKER, DamageAttributeRole.ACCURACY, activated = dodged && accuracy != 1.0)
            if (dodged) {
                event.isCancelled = true
                val result = cancelled(
                    transactionId,
                    request,
                    "攻击未命中",
                    DamageOutcome.MISSED,
                    DamageBreakdown(emptyList(), tracker.snapshot())
                )
                direct?.result = result
                remember(result, attacker, victim)
                val dodgeContext = context(
                    transactionId,
                    victim,
                    attacker,
                    mapOf("attacker" to attacker, "victim" to victim, "channels" to channels.keys.toList())
                )
                triggers.dispatch(CombatDodgeTrigger, dodgeContext)
                return
            }
        }

        val drafts = channels.toSortedMap().map { (channelId, input) ->
            val channel = definitions.current().snapshot.damageChannels[channelId]
                ?: throw IllegalArgumentException("未知伤害通道：$channelId")
            var amount = input
            val criticalChance = if (request.allowCritical && channel.canCrit && attacker != null) {
                tracker.value(attacker, CRITICAL_CHANCE).coerceIn(0.0, 1.0)
            } else 0.0
            val critical = criticalChance > 0.0 && random() < criticalChance
            if (attacker != null && request.allowCritical && channel.canCrit) {
                tracker.use(
                    attacker,
                    CRITICAL_CHANCE,
                    DamageAttributeOwner.ATTACKER,
                    DamageAttributeRole.CRITICAL_CHANCE,
                    channelId,
                    activated = critical
                )
            }
            if (critical && attacker != null) {
                val multiplier = tracker.use(
                    attacker,
                    CRITICAL_DAMAGE,
                    DamageAttributeOwner.ATTACKER,
                    DamageAttributeRole.CRITICAL_DAMAGE,
                    channelId,
                    activated = true
                ).coerceAtLeast(1.0)
                amount *= multiplier
                triggers.dispatch(
                    CombatCriticalTrigger,
                    context(
                        transactionId,
                        attacker,
                        victim,
                        mapOf("attacker" to attacker, "victim" to victim, "channel" to channelId, "amount" to amount)
                    )
                )
            }
            val afterCritical = amount
            amount = mitigate(channelId, amount, attacker, victim, request, transactionId, tracker)
            val requestedAmount = preparedChannels[channelId] ?: 0.0
            ChannelDraft(
                channelId,
                requestedAmount,
                input,
                afterCritical,
                amount,
                relation(requestedAmount, input),
                critical,
                reactionPlan.reactionsByChannel[channelId].orEmpty()
            )
        }

        var finalDamage = drafts.sumOf { it.afterMitigation }
        val damageReduction = tracker.value(victim, DAMAGE_REDUCTION).coerceIn(0.0, 0.9)
        tracker.use(victim, DAMAGE_REDUCTION, DamageAttributeOwner.VICTIM, DamageAttributeRole.DAMAGE_REDUCTION, activated = damageReduction > 0.0)
        finalDamage *= 1.0 - damageReduction
        val blockChance = tracker.value(victim, BLOCK_CHANCE).coerceIn(0.0, 0.9)
        val blocked = random() < blockChance
        tracker.use(victim, BLOCK_CHANCE, DamageAttributeOwner.VICTIM, DamageAttributeRole.BLOCK_CHANCE, activated = blocked)
        if (blocked) {
            val power = tracker.use(victim, BLOCK_POWER, DamageAttributeOwner.VICTIM, DamageAttributeRole.BLOCK_POWER, activated = true)
                .coerceIn(0.0, 1.0)
            finalDamage *= 1.0 - power
        }
        val shieldAbsorbed = reserveShield(victim, finalDamage, tracker)
        finalDamage -= shieldAbsorbed
        if (finalDamage > 0.0) finalDamage = max(minimumDamage, finalDamage)
        val resolvedEvent = SymphonyDamageEvent(
            transactionId,
            request,
            buildBreakdown(drafts, tracker.snapshot(), finalDamage),
            finalDamage
        )
        Bukkit.getPluginManager().callEvent(resolvedEvent)
        require(resolvedEvent.finalDamage.isFinite() && resolvedEvent.finalDamage >= 0.0) {
            "Symphony 伤害事件产生了无效的最终伤害"
        }
        val applyEvent = SymphonyDamageApplyEvent(transactionId, request, resolvedEvent.finalDamage)
        Bukkit.getPluginManager().callEvent(applyEvent)
        if (resolvedEvent.isCancelled || applyEvent.isCancelled || event.isCancelled) {
            if (shieldAbsorbed > 0.0) shields.merge(victim.uniqueId, shieldAbsorbed, Double::plus)
            event.isCancelled = true
            val result = cancelled(transactionId, request, "伤害应用事件取消了本次伤害")
            direct?.result = result
            remember(result, attacker, victim)
            return
        }
        require(applyEvent.finalDamage.isFinite() && applyEvent.finalDamage >= 0.0) {
            "伤害应用事件产生了无效的最终伤害"
        }
        val finalBreakdown = buildBreakdown(drafts, tracker.snapshot(), applyEvent.finalDamage)
        applySymphonyDamage(event, applyEvent.finalDamage)
        pendingEvents[event] = PendingDamage(
            request,
            transactionId,
            request.channels,
            finalBreakdown,
            blocked,
            false,
            shieldAbsorbed,
            reactionPlan,
            attacker,
            victim,
            direct
        )
    }

    fun monitor(event: EntityDamageEvent) {
        requirePrimaryThread()
        val pending = pendingEvents.remove(event) ?: return
        val state = if (event.isCancelled) DamageResultState.CANCELLED else DamageResultState.CONFIRMED
        if (state == DamageResultState.CANCELLED && pending.shieldAbsorbed > 0.0) {
            shields.merge(pending.victim.uniqueId, pending.shieldAbsorbed, Double::plus)
        }
        val finalDamage = if (state == DamageResultState.CONFIRMED) event.finalDamage.coerceAtLeast(0.0) else 0.0
        val breakdown = if (state == DamageResultState.CONFIRMED) {
            reallocate(pending.breakdown, finalDamage)
        } else DamageBreakdown.EMPTY
        val result = DamageResult(
            transactionId = pending.transactionId,
            parentTransactionId = pending.request.parentTransactionId,
            state = state,
            requested = pending.requested,
            applied = breakdown.channels.map { DamageChannelAmount(it.channel, it.finalAmount) },
            finalDamage = finalDamage,
            critical = state == DamageResultState.CONFIRMED && breakdown.critical,
            reason = if (state == DamageResultState.CANCELLED) "Bukkit 伤害事件已被取消" else null,
            breakdown = breakdown
        )
        pending.directCall?.result = result
        remember(result, pending.attacker, pending.victim)
        if (state == DamageResultState.CONFIRMED) {
            Bukkit.getScheduler().runTaskLater(plugin, Runnable { commitConfirmed(pending, result) }, confirmationDelayTicks)
        }
    }

    fun clear() {
        combatTask?.cancel()
        combatTask = null
        pendingEvents.clear()
        directStack.remove()
        history.clear()
        shields.clear()
        combatUntil.clear()
    }

    fun forget(entityId: UUID) {
        history.remove(entityId)
        shields.remove(entityId)
        combatUntil.remove(entityId)
    }

    private fun mitigate(
        channelId: String,
        input: Double,
        attacker: LivingEntity?,
        victim: LivingEntity,
        request: DamageRequest,
        transactionId: UUID,
        tracker: AttributeTracker
    ): Double {
        val channel = definitions.current().snapshot.damageChannels.getValue(channelId)
        var output = if (channel.mitigation == "armor") {
            val formula = definitions.current().snapshot.armorFormula
            val defense = tracker.value(victim, formula.defense)
            val percentPenetration = attacker?.let { tracker.value(it, formula.percentPenetration) } ?: 0.0
            val flatPenetration = attacker?.let { tracker.value(it, formula.flatPenetration) } ?: 0.0
            tracker.use(victim, formula.defense, DamageAttributeOwner.VICTIM, DamageAttributeRole.DEFENSE, channelId, defense > 0.0)
            if (attacker != null) {
                tracker.use(attacker, formula.percentPenetration, DamageAttributeOwner.ATTACKER, DamageAttributeRole.PERCENT_PENETRATION, channelId, percentPenetration > 0.0)
                tracker.use(attacker, formula.flatPenetration, DamageAttributeOwner.ATTACKER, DamageAttributeRole.FLAT_PENETRATION, channelId, flatPenetration > 0.0)
            }
            ArmorFormula.calculate(
                input,
                defense,
                percentPenetration,
                flatPenetration,
                formula
            ).afterArmor
        } else {
            val resistance = channel.resistanceAttribute?.let { tracker.value(victim, it) }?.coerceIn(0.0, 1.0) ?: 0.0
            val amplification = channel.amplificationAttribute?.let { key -> attacker?.let { tracker.value(it, key) } }
                ?.coerceAtLeast(-1.0) ?: 0.0
            channel.resistanceAttribute?.let {
                tracker.use(victim, it, DamageAttributeOwner.VICTIM, DamageAttributeRole.RESISTANCE, channelId, resistance > 0.0)
            }
            if (attacker != null) channel.amplificationAttribute?.let {
                tracker.use(attacker, it, DamageAttributeOwner.ATTACKER, DamageAttributeRole.AMPLIFICATION, channelId, amplification != 0.0)
            }
            ElementalDamageFormula.calculate(input, resistance, amplification).output
        }
        val event = SymphonyDamageMitigationEvent(transactionId, request, channelId, input, output)
        Bukkit.getPluginManager().callEvent(event)
        if (event.isCancelled) return 0.0
        require(event.outputDamage.isFinite() && event.outputDamage >= 0.0) {
            "伤害减免事件产生了无效输出"
        }
        output = event.outputDamage
        return output
    }

    @Suppress("DEPRECATION")
    private fun applySymphonyDamage(event: EntityDamageEvent, finalDamage: Double) {
        event.damage = finalDamage
        EntityDamageEvent.DamageModifier.values().forEach { modifier ->
            if (modifier != EntityDamageEvent.DamageModifier.BASE && event.isApplicable(modifier)) {
                runCatching { event.setDamage(modifier, 0.0) }
            }
        }
        event.setDamage(EntityDamageEvent.DamageModifier.BASE, finalDamage)
    }

    private fun commitConfirmed(pending: PendingDamage, result: DamageResult) {
        pending.reactionPlan.commitConfirmed()
        val attacker = pending.attacker
        markCombat(pending.victim, attacker, result.transactionId)
        if (attacker != null) markCombat(attacker, pending.victim, result.transactionId)
        val commonValues = mapOf(
            "attacker" to attacker,
            "victim" to pending.victim,
            "channels" to result.applied.map { it.channel },
            "amount" to result.finalDamage,
            "critical" to result.critical,
            "blocked" to pending.blocked,
            "shieldAbsorbed" to pending.shieldAbsorbed,
            "inCombat" to true,
            "result" to result
        )
        if (attacker != null) {
            val attackContext = context(result.transactionId, attacker, pending.victim, commonValues)
            triggers.dispatch(CombatAttackConfirmedTrigger, attackContext)
            triggers.dispatch(CombatDamageDealtTrigger, attackContext)
            val ingress = pending.request.cause
            triggers.dispatch(if (ingress.contains("projectile") || ingress.contains("arrow")) CombatRangedTrigger else CombatMeleeTrigger, attackContext)
        }
        val victimContext = context(result.transactionId, pending.victim, attacker, commonValues)
        triggers.dispatch(CombatDamageTakenTrigger, victimContext)
        triggers.dispatch(CombatDamageConfirmedTrigger, victimContext)
        if (pending.blocked) triggers.dispatch(CombatBlockConfirmedTrigger, victimContext)

        if (!pending.victim.isValid || pending.victim.isDead) {
            triggers.dispatch(CombatDeathTrigger, victimContext)
            if (attacker != null) triggers.dispatch(CombatKillTrigger, context(result.transactionId, attacker, pending.victim, commonValues))
            Bukkit.getPluginManager().callEvent(SymphonyDamageConfirmedEvent(result))
            return
        }
        if (pending.victim.health / (pending.victim.getAttribute(Attribute.GENERIC_MAX_HEALTH)?.value
                ?: pending.victim.health.coerceAtLeast(1.0)) <= LOW_HEALTH_THRESHOLD) {
            triggers.dispatch(CombatLowHealthTrigger, victimContext)
        }
        if (attacker != null && attacker.isValid && !attacker.isDead) {
            val lifesteal = attributes.value(attacker, LIFESTEAL).coerceIn(0.0, 1.0)
            if (lifesteal > 0.0) {
                attacker.health = min(attacker.getAttribute(Attribute.GENERIC_MAX_HEALTH)?.value ?: attacker.health,
                attacker.health + result.finalDamage * lifesteal)
            }
            val thorns = attributes.value(pending.victim, THORNS).coerceAtLeast(0.0)
            if (thorns > 0.0 && result.finalDamage > 0.0) {
                damage(
                    DamageRequest(
                        attacker = pending.victim,
                        victim = attacker,
                        channels = listOf(DamageChannelAmount("physical", result.finalDamage * thorns)),
                        cause = "symphony:thorns",
                        parentTransactionId = result.transactionId,
                        allowCritical = false
                    )
                )
            }
        }
        Bukkit.getPluginManager().callEvent(SymphonyDamageConfirmedEvent(result))
    }

    private fun shouldTakeOver(attacker: LivingEntity?, victim: LivingEntity, cause: EntityDamageEvent.DamageCause): Boolean {
        if (!combatEnabled) return false
        if (mappedEnvironmentalCauses.containsKey(cause.name) || mappedEnvironmentalCauses.containsKey(cause.name.lowercase())) return true
        if (cause in ENVIRONMENTAL_CAUSES) return false
        return hasCombatSources(victim) || (attacker != null && hasCombatSources(attacker))
    }

    private fun attributeAttackChannels(
        attacker: LivingEntity,
        tracker: AttributeTracker,
        damageMultiplier: Double = 1.0
    ): List<DamageChannelAmount> = composeDamageAttributeChannels(
        definitions.current().snapshot.damageChannels.values,
        damageMultiplier
    ) { key -> tracker.value(attacker, key) }.map { input ->
            tracker.use(
                attacker,
                input.attribute,
                DamageAttributeOwner.ATTACKER,
                DamageAttributeRole.ATTACK_POWER,
                input.channel,
                activated = true
            )
            DamageChannelAmount(input.channel, input.amount)
        }

    private fun reserveShield(victim: LivingEntity, incoming: Double, tracker: AttributeTracker): Double {
        if (incoming <= 0.0) return 0.0
        val maximum = tracker.value(victim, SHIELD_CAPACITY).coerceAtLeast(0.0)
        tracker.use(victim, SHIELD_CAPACITY, DamageAttributeOwner.VICTIM, DamageAttributeRole.SHIELD_CAPACITY, activated = false)
        if (maximum <= 0.0) return 0.0
        val remaining = shields.compute(victim.uniqueId) { _, current -> (current ?: maximum).coerceAtMost(maximum) } ?: 0.0
        val absorbed = min(remaining, incoming)
        shields[victim.uniqueId] = remaining - absorbed
        tracker.use(victim, SHIELD_CAPACITY, DamageAttributeOwner.VICTIM, DamageAttributeRole.SHIELD_CAPACITY, activated = absorbed > 0.0)
        return absorbed
    }

    private fun buildBreakdown(
        drafts: List<ChannelDraft>,
        uses: List<DamageAttributeUse>,
        finalDamage: Double
    ): DamageBreakdown {
        val allocated = allocate(drafts.map(ChannelDraft::afterMitigation), finalDamage)
        val channels = drafts.mapIndexed { index, draft ->
            DamageChannelResult(
                channel = draft.channel,
                requestedAmount = draft.requested,
                afterRelationAmount = draft.afterRelation,
                afterCriticalAmount = draft.afterCritical,
                afterMitigationAmount = draft.afterMitigation,
                finalAmount = allocated[index],
                relation = draft.relation,
                critical = draft.critical,
                reactions = draft.reactions,
                attributes = uses.filter { it.channel == draft.channel }
            )
        }
        return DamageBreakdown(channels, uses)
    }

    private fun reallocate(breakdown: DamageBreakdown, finalDamage: Double): DamageBreakdown {
        val allocated = allocate(breakdown.channels.map(DamageChannelResult::finalAmount), finalDamage)
        return DamageBreakdown(
            breakdown.channels.mapIndexed { index, channel -> channel.copy(finalAmount = allocated[index]) },
            breakdown.attributes
        )
    }

    private fun allocate(weights: List<Double>, total: Double): List<Double> {
        if (weights.isEmpty()) return emptyList()
        val sum = weights.sum()
        if (sum <= 0.0 || total <= 0.0) return List(weights.size) { 0.0 }
        var assigned = 0.0
        return weights.mapIndexed { index, weight ->
            if (index == weights.lastIndex) (total - assigned).coerceAtLeast(0.0)
            else (total * weight / sum).also { assigned += it }
        }
    }

    private fun relation(before: Double, after: Double): DamageRelation {
        val tolerance = max(1.0, before) * 1.0e-9
        return when {
            after > before + tolerance -> DamageRelation.ADVANTAGED
            after < before - tolerance -> DamageRelation.DISADVANTAGED
            else -> DamageRelation.NEUTRAL
        }
    }

    private fun context(
        transactionId: UUID,
        self: LivingEntity,
        target: LivingEntity?,
        values: Map<String, Any?>
    ) = EntityTriggerContext(transactionId, self, target, System.currentTimeMillis(), values)

    private fun hasCombatSources(entity: LivingEntity): Boolean =
        stateStore.stateIfPresent(entity.uniqueId)?.sources?.values?.any { it.modifiers.isNotEmpty() || it.item != null } == true

    private fun resolveAttacker(event: EntityDamageEvent): LivingEntity? {
        val damager = (event as? EntityDamageByEntityEvent)?.damager ?: return null
        return when (damager) {
            is LivingEntity -> damager
            is Projectile -> damager.shooter as? LivingEntity
            else -> null
        }
    }

    private fun remember(result: DamageResult, attacker: LivingEntity?, victim: LivingEntity) {
        (listOfNotNull(attacker?.uniqueId, victim.uniqueId)).distinct().forEach { id ->
            val deque = history.computeIfAbsent(id) { ArrayDeque() }
            synchronized(deque) {
                deque.addLast(result)
                while (deque.size > HISTORY_LIMIT) deque.removeFirst()
            }
        }
    }

    private fun markCombat(entity: LivingEntity, opponent: LivingEntity?, transactionId: UUID) {
        val now = System.currentTimeMillis()
        val wasActive = (combatUntil[entity.uniqueId] ?: 0L) > now
        combatUntil[entity.uniqueId] = Math.addExact(now, COMBAT_TIMEOUT_MILLIS)
        if (!wasActive) {
            triggers.dispatch(
                CombatEnterTrigger,
                context(transactionId, entity, opponent, mapOf("inCombat" to true, "opponent" to opponent))
            )
        }
    }

    private fun expireCombatStates() {
        val now = System.currentTimeMillis()
        combatUntil.forEach { (entityId, expiresAt) ->
            if (expiresAt > now || !combatUntil.remove(entityId, expiresAt)) return@forEach
            val entity = Bukkit.getEntity(entityId) as? LivingEntity ?: return@forEach
            if (!entity.isValid) return@forEach
            triggers.dispatch(
                CombatLeaveTrigger,
                context(UUID.randomUUID(), entity, null, mapOf("inCombat" to false))
            )
        }
    }

    private fun cancelled(
        id: UUID,
        request: DamageRequest,
        reason: String,
        outcome: DamageOutcome = DamageOutcome.CANCELLED,
        breakdown: DamageBreakdown = DamageBreakdown.EMPTY
    ) = DamageResult(
        id,
        request.parentTransactionId,
        DamageResultState.CANCELLED,
        request.channels,
        emptyList(),
        0.0,
        false,
        reason,
        breakdown,
        outcome
    )

    private fun rejected(request: DamageRequest, reason: String) = DamageResult(
        UUID.randomUUID(), request.parentTransactionId, DamageResultState.REJECTED,
        request.channels, emptyList(), 0.0, false, reason
    )

    private fun requirePrimaryThread() {
        check(Bukkit.isPrimaryThread()) { "伤害事务必须在 Bukkit 主线程执行" }
    }

    companion object {
        private const val HISTORY_LIMIT = 100
        private const val COMBAT_TIMEOUT_MILLIS = 10_000L
        private val PHYSICAL_DAMAGE = AttributeKey.symphony("physical_damage")
        private val CRITICAL_CHANCE = AttributeKey.symphony("critical_chance")
        private val CRITICAL_DAMAGE = AttributeKey.symphony("critical_damage")
        private val DAMAGE_REDUCTION = AttributeKey.symphony("damage_reduction")
        private val BLOCK_CHANCE = AttributeKey.symphony("block_chance")
        private val BLOCK_POWER = AttributeKey.symphony("block_power")
        private val LIFESTEAL = AttributeKey.symphony("lifesteal")
        private val ACCURACY = AttributeKey.symphony("accuracy")
        private val DODGE = AttributeKey.symphony("dodge")
        private val THORNS = AttributeKey.symphony("thorns")
        private val SHIELD_CAPACITY = AttributeKey.symphony("shield_capacity")
        private const val LOW_HEALTH_THRESHOLD = 0.25
        private val ENVIRONMENTAL_CAUSES = setOf(
            EntityDamageEvent.DamageCause.FALL,
            EntityDamageEvent.DamageCause.DROWNING,
            EntityDamageEvent.DamageCause.STARVATION,
            EntityDamageEvent.DamageCause.VOID,
            EntityDamageEvent.DamageCause.SUFFOCATION
        )
    }
}
