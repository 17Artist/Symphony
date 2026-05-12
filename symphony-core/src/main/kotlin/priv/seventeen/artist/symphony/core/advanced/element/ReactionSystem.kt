package priv.seventeen.artist.symphony.core.advanced.element

import org.bukkit.Bukkit
import org.bukkit.Particle
import org.bukkit.entity.LivingEntity
import priv.seventeen.artist.symphony.api.attribute.Operation
import priv.seventeen.artist.symphony.core.attribute.AttributeCache
import priv.seventeen.artist.symphony.core.attribute.AttributeCalculator
import priv.seventeen.artist.symphony.core.data.ActiveBuff
import priv.seventeen.artist.symphony.core.script.AriaCallbackManager
import priv.seventeen.artist.symphony.core.storage.PlayerDataManager
import priv.seventeen.artist.symphony.core.trigger.CooldownManager
import java.util.UUID
import java.util.concurrent.ConcurrentHashMap

/**
 * 元素反应系统 — 两种元素叠加触发化学反应。
 */
object ReactionSystem {
    private val reactions = ConcurrentHashMap<String, ReactionDefinition>()
    var reactionCooldown = 500L

    fun register(reaction: ReactionDefinition) {
        val key = "${reaction.trigger}+${reaction.aura}"
        reactions[key] = reaction
        // 注册反向反应
        reaction.reverse?.let { rev ->
            val reverseKey = "${reaction.aura}+${reaction.trigger}"
            reactions[reverseKey] = reaction.copy(
                id = rev.id,
                trigger = reaction.aura,
                aura = reaction.trigger,
                multiplier = rev.multiplier,
                reverse = null
            )
        }
    }

    fun unregister(id: String) {
        reactions.entries.removeIf { it.value.id == id }
    }

    fun getAll(): Collection<ReactionDefinition> = reactions.values

    fun clear() {
        reactions.clear()
    }

    fun tryReaction(entity: LivingEntity, triggerElement: String, auraElement: String, appliedBy: UUID?) {
        // 查找匹配的反应
        val key = "$triggerElement+$auraElement"
        val reaction = reactions[key]
            ?: reactions["$triggerElement+*"]  // 通配符匹配（如扩散）
            ?: return

        // 冷却检查
        val cooldownKey = "reaction:${reaction.id}"
        if (CooldownManager.isOnCooldown(entity.uniqueId, cooldownKey)) return
        CooldownManager.setCooldown(entity.uniqueId, cooldownKey, reactionCooldown)

        // 消耗元素量
        ElementAuraSystem.consumeGauge(entity, auraElement, reaction.gaugeConsume)

        // 执行反应效果
        executeReaction(entity, reaction, appliedBy)

        // 粒子和音效
        reaction.particle?.let { particleName ->
            try {
                val particle = Particle.valueOf(particleName.uppercase())
                entity.world.spawnParticle(particle, entity.location.add(0.0, 1.0, 0.0), 20, 0.5, 0.5, 0.5)
            } catch (_: Exception) {}
        }
        reaction.sound?.let { sound ->
            entity.world.playSound(entity.location, sound, 1.0f, 1.0f)
        }

        // 统计
        val attacker = appliedBy?.let { Bukkit.getPlayer(it) }
        if (attacker != null) {
            PlayerDataManager.getData(attacker.uniqueId)?.let {
                it.persistent.statistics.totalReactionsTriggered++
            }
            // 元素反应文字反馈
            val reactionColor = when (reaction.type) {
                ReactionType.AMPLIFY -> "§6"
                ReactionType.DEBUFF -> "§5"
                ReactionType.DOT_AOE -> "§d"
                ReactionType.CONTROL -> "§b"
                ReactionType.SPREAD -> "§f"
                else -> "§e"
            }
            attacker.sendMessage("${reactionColor}⚡ 元素反应: ${reaction.displayName}")
            attacker.sendTitle("", "${reactionColor}${reaction.displayName}", 0, 25, 10)
        }
    }

    private fun executeReaction(entity: LivingEntity, reaction: ReactionDefinition, appliedBy: UUID?) {
        val attacker = appliedBy?.let { Bukkit.getEntity(it) as? LivingEntity }
        val em = if (attacker != null) AttributeCalculator.getValue(attacker, "elemental_mastery") else 0.0
        val emBonus = 2.78 * em / (em + 1400.0)

        when (reaction.type) {
            ReactionType.AMPLIFY -> {
                // 倍率反应：增强攻击者的下次伤害
                val finalMultiplier = reaction.multiplier * (1.0 + emBonus)
                val attackerUuid = appliedBy ?: return
                val data = PlayerDataManager.getData(attackerUuid)
                if (data != null) {
                    data.runtime.activeBuffs.add(ActiveBuff(
                        id = "reaction:${reaction.id}",
                        attribute = "${reaction.trigger}_damage",
                        operation = Operation.PERCENT,
                        value = finalMultiplier - 1.0,
                        expireTime = System.currentTimeMillis() + 100,
                        source = "reaction:${reaction.id}"
                    ))
                }
            }
            ReactionType.DEBUFF -> {
                // 减益反应：降低目标属性
                val data = PlayerDataManager.getData(entity.uniqueId)
                    ?: return
                data.runtime.activeBuffs.add(ActiveBuff(
                    id = "reaction:${reaction.id}",
                    attribute = "physical_defense",
                    operation = Operation.PERCENT,
                    value = -0.4,
                    expireTime = System.currentTimeMillis() + 12000,
                    source = "reaction:${reaction.id}"
                ))
                AttributeCache.markDirty(entity.uniqueId)
                // AOE 伤害
                if (reaction.radius > 0 && attacker != null) {
                    val damage = 50.0 + em * 2.0
                    entity.getNearbyEntities(reaction.radius, reaction.radius, reaction.radius)
                        .filterIsInstance<LivingEntity>()
                        .forEach { nearby -> nearby.damage(damage, attacker) }
                }
            }
            ReactionType.DOT_AOE -> {
                val callbackId = "reaction:${reaction.id}:on_tick"
                if (AriaCallbackManager.has(callbackId) && attacker != null) {
                    // 使用脚本回调实现多 tick 伤害
                    val baseDamage = 20.0 + em * 1.5
                    val plugin = Bukkit.getPluginManager().getPlugin("Symphony")
                    if (plugin != null) {
                        for (tick in 0 until reaction.ticks) {
                            Bukkit.getScheduler().runTaskLater(plugin, Runnable {
                                if (entity.isValid && !entity.isDead) {
                                    AriaCallbackManager.invoke(callbackId, entity, tick, baseDamage, attacker)
                                }
                            }, (tick * (reaction.interval / 50)))
                        }
                    }
                } else if (attacker != null) {
                    // fallback: 一次性伤害
                    val damage = (20.0 + em * 1.5) * reaction.ticks
                    entity.damage(damage, attacker)
                }
            }
            ReactionType.CONTROL -> {
                // 控制反应：冻结
                val duration = (3000 + em * 100).toInt()
                entity.freezeTicks = duration / 50
            }
            ReactionType.SPREAD -> {
                // 扩散反应：传播元素
                if (reaction.radius > 0) {
                    entity.getNearbyEntities(reaction.radius, reaction.radius, reaction.radius)
                        .filterIsInstance<LivingEntity>()
                        .forEach { nearby ->
                            ElementAuraSystem.applyAura(nearby, reaction.aura, 0.5, 6000, appliedBy)
                            if (attacker != null) {
                                nearby.damage(30.0 + em, attacker)
                            }
                        }
                }
            }
        }
    }

    fun registerDefaults() {
        register(ReactionDefinition("vaporize", "蒸发", "fire", "water", ReactionType.AMPLIFY, 2.0, 0.5, "CLOUD", "block.fire.extinguish", reverse = ReactionReverse("vaporize_reverse", 1.5)))
        register(ReactionDefinition("melt", "融化", "fire", "ice", ReactionType.AMPLIFY, 2.0, 0.5, "LAVA", null, reverse = ReactionReverse("melt_reverse", 1.5)))
        register(ReactionDefinition("superconduct", "超导", "lightning", "ice", ReactionType.DEBUFF, 1.0, 0.5, "ELECTRIC_SPARK", "entity.lightning_bolt.impact", radius = 3.0))
        register(ReactionDefinition("electro_charged", "感电", "lightning", "water", ReactionType.DOT_AOE, 1.0, 0.5, "ELECTRIC_SPARK", null, radius = 3.0, ticks = 6, interval = 1000))
        register(ReactionDefinition("frozen", "冻结", "ice", "water", ReactionType.CONTROL, 1.0, 0.5, "SNOWFLAKE", "block.glass.break"))
        register(ReactionDefinition("swirl", "扩散", "wind", "*", ReactionType.SPREAD, 1.0, 0.3, "CLOUD", null, radius = 5.0))
    }
}
