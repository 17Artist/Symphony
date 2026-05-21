package priv.seventeen.artist.symphony.plugin.listener

import org.bukkit.Bukkit
import org.bukkit.attribute.Attribute
import org.bukkit.entity.LivingEntity
import org.bukkit.entity.Player
import org.bukkit.entity.Projectile
import org.bukkit.event.EventPriority
import org.bukkit.event.entity.EntityDamageByEntityEvent
import org.bukkit.event.entity.EntityDamageEvent
import priv.seventeen.artist.blink.event.AutoListener
import priv.seventeen.artist.symphony.api.event.SymphonyDamageEvent
import priv.seventeen.artist.symphony.api.event.SymphonyMitigationEvent
import priv.seventeen.artist.symphony.api.event.SymphonyPreDamageEvent
import priv.seventeen.artist.symphony.api.trigger.TriggerType
import priv.seventeen.artist.symphony.core.attribute.AttributeCache
import priv.seventeen.artist.symphony.core.attribute.AttributeCalculator
import priv.seventeen.artist.symphony.core.advanced.element.ElementAuraSystem
import priv.seventeen.artist.symphony.core.advanced.status.StatusDamageGuard
import priv.seventeen.artist.symphony.core.skill.builtin.MythicMobDataStore
import priv.seventeen.artist.symphony.core.storage.PlayerDataManager
import priv.seventeen.artist.symphony.core.trigger.TriggerDispatcher
import priv.seventeen.artist.symphony.plugin.SymphonyPlugin
import java.util.concurrent.ThreadLocalRandom

object DamageListener {

    @AutoListener(priority = EventPriority.NORMAL)
    fun onDamage(event: EntityDamageEvent) {
        // 状态层 DOT/爆发伤害不进入 Symphony 攻击管线，避免触发 ON_ATTACK 词条循环叠层
        if (!StatusDamageGuard.shouldHandle(event)) return

        if (event !is EntityDamageByEntityEvent) return
        val victim = event.entity as? LivingEntity ?: return
        var attacker = event.damager as? LivingEntity
        if (attacker == null && event.damager is Projectile) {
            attacker = (event.damager as Projectile).shooter as? LivingEntity
        }
        if (attacker == null) return

        // 非玩家攻击者且无 Symphony 属性数据 → 跳过 Symphony 伤害管线，使用原版伤害
        val attackerIsPlayer = attacker is Player
        val attackerHasData = attackerIsPlayer || PlayerDataManager.getData(attacker.uniqueId) != null
            || MythicMobDataStore.get(attacker.uniqueId) != null
            || AttributeCache.get(attacker.uniqueId, "physical_damage") != null
        if (!attackerHasData) {
            // 仍然对玩家受害者触发防御触发器
            if (victim is Player) {
                TriggerDispatcher.dispatch(TriggerType.ON_DEFEND, victim) {
                    target(attacker)
                    set("damage", event.damage)
                }
                TriggerDispatcher.dispatch(TriggerType.ON_DAMAGED, victim) {
                    target(attacker)
                    set("damage", event.damage)
                    set("damageType", "vanilla")
                }
                updateCombatState(victim)
            }
            return
        }

        // 读取攻击者属性
        val physicalDamage = AttributeCalculator.getValue(attacker, "physical_damage")
        val critChance = AttributeCalculator.getValue(attacker, "critical_chance")
        val critDamage = AttributeCalculator.getValue(attacker, "critical_damage")
        val penetration = AttributeCalculator.getValue(attacker, "penetration")
        val dodge = AttributeCalculator.getValue(victim, "dodge")
        val physicalDefense = AttributeCalculator.getValue(victim, "physical_defense")
        val damageReduction = AttributeCalculator.getValue(victim, "damage_reduction")
        val lifesteal = AttributeCalculator.getValue(attacker, "lifesteal")

        // 格挡判定
        val blockChance = AttributeCalculator.getValue(victim, "block_chance")
        val blockPower = AttributeCalculator.getValue(victim, "block_power")
        var blocked = false
        val effectiveBlock = blockChance.coerceIn(0.0, 0.75)
        if (ThreadLocalRandom.current().nextDouble() < effectiveBlock) {
            blocked = true
            TriggerDispatcher.dispatch(TriggerType.ON_BLOCK, victim) {
                target(attacker)
                set("blockedDamage", event.damage * blockPower.coerceIn(0.0, 0.9))
            }
        }

        // 闪避判定
        val effectiveDodge = dodge.coerceIn(0.0, 0.95)
        if (ThreadLocalRandom.current().nextDouble() < effectiveDodge) {
            event.isCancelled = true
            TriggerDispatcher.dispatch(TriggerType.ON_DODGE, victim) {
                target(attacker)
                set("dodgedDamage", event.damage)
            }
            return
        }

        // 暴击判定
        val isCritical = ThreadLocalRandom.current().nextDouble() < critChance
        val critMultiplier = if (isCritical) critDamage else 1.0

        // 【Pre】允许插件改 base / 取消
        val preEvent = SymphonyPreDamageEvent(attacker, victim, physicalDamage, isCritical, "physical")
        Bukkit.getPluginManager().callEvent(preEvent)
        if (preEvent.isCancelled) { event.isCancelled = true; return }
        val effectiveCritMul = if (preEvent.isCritical) critDamage else 1.0

        // 伤害计算
        val effectiveDef = (physicalDefense * (1.0 - penetration)).coerceAtLeast(0.0)
        var finalDamage = maxOf(1.0, preEvent.baseDamage - effectiveDef) * effectiveCritMul
        finalDamage *= (1.0 - damageReduction)

        // 格挡减伤
        if (blocked) {
            finalDamage *= (1.0 - blockPower.coerceIn(0.0, 0.9))
        }

        // 【Mitigation】允许插件进一步改 physical
        val mitEvent = SymphonyMitigationEvent(
            attacker, victim, preEvent.baseDamage, finalDamage,
            damageReduction, preEvent.isCritical, blocked
        )
        Bukkit.getPluginManager().callEvent(mitEvent)
        if (mitEvent.isCancelled) { event.isCancelled = true; return }
        finalDamage = mitEvent.finalPhysical

        // 【Post/Final】合并事件（保持历史兼容，承载元素伤害）
        val symphonyEvent = SymphonyDamageEvent(attacker, victim, physicalDamage, finalDamage, "physical", preEvent.isCritical)
        Bukkit.getPluginManager().callEvent(symphonyEvent)
        if (symphonyEvent.isCancelled) {
            event.isCancelled = true
            return
        }

        // 元素伤害计算
        for (elem in ELEMENTS) {
            val elemDmg = AttributeCalculator.getValue(attacker, elem.dmgAttr)
            if (elemDmg > 0) {
                val elemRes = AttributeCalculator.getValue(victim, elem.resAttr)
                val elemFinal = elemDmg * maxOf(0.0, 1.0 - elemRes)
                if (elemFinal > 0) {
                    symphonyEvent.elementDamages[elem.id] = elemFinal
                    symphonyEvent.finalDamage += elemFinal
                    // 自动附着元素到目标，触发元素反应
                    if (SymphonyPlugin.config.elementEnabled) {
                        val gauge = (elemFinal / 10.0).coerceIn(0.5, 4.0)
                        ElementAuraSystem.applyAura(victim, elem.id, gauge, appliedBy = attacker.uniqueId)
                    }
                }
            }
        }

        event.damage = symphonyEvent.finalDamage

        // ═══ 伤害反馈 ActionBar ═══
        if (attacker is Player) {
            val sb = StringBuilder()
            val critMark = if (symphonyEvent.isCritical) " §6✦暴击" else ""
            sb.append("§f⚔ §c${formatDmg(symphonyEvent.finalDamage - symphonyEvent.elementDamages.values.sum())}$critMark")
            for ((elemId, dmg) in symphonyEvent.elementDamages) {
                if (dmg <= 0) continue
                val def = ELEMENTS.firstOrNull { it.id == elemId }
                val color = def?.color ?: "§7"
                val name = def?.name ?: elemId
                sb.append(" $color+${formatDmg(dmg)} $name")
            }
            if (symphonyEvent.elementDamages.isNotEmpty()) {
                sb.append(" §8| §e总 ${formatDmg(symphonyEvent.finalDamage)}")
            }
            attacker.spigot().sendMessage(
                net.md_5.bungee.api.ChatMessageType.ACTION_BAR,
                net.md_5.bungee.api.chat.TextComponent(sb.toString())
            )
        }

        // 吸血
        if (lifesteal > 0 && attacker is LivingEntity) {
            val healAmount = symphonyEvent.finalDamage * lifesteal
            val maxHealth = attacker.getAttribute(org.bukkit.attribute.Attribute.GENERIC_MAX_HEALTH)?.value ?: 20.0
            attacker.health = minOf(attacker.health + healAmount, maxHealth)
        }

        // 触发器
        TriggerDispatcher.dispatch(TriggerType.ON_ATTACK, attacker) {
            target(victim)
            set("damage", symphonyEvent.finalDamage)
            set("isCritical", preEvent.isCritical)
        }

        // 近战/远程攻击区分
        val isRanged = event.damager is Projectile
        if (isRanged) {
            TriggerDispatcher.dispatch(TriggerType.ON_RANGED_ATTACK, attacker) {
                target(victim)
                set("damage", symphonyEvent.finalDamage)
                set("projectile", event.damager.type.name)
            }
        } else {
            TriggerDispatcher.dispatch(TriggerType.ON_MELEE_ATTACK, attacker) {
                target(victim)
                set("damage", symphonyEvent.finalDamage)
                set("weapon", attacker.equipment?.itemInMainHand?.type?.name ?: "AIR")
            }
        }

        TriggerDispatcher.dispatch(TriggerType.ON_DEFEND, victim) {
            target(attacker)
            set("damage", symphonyEvent.finalDamage)
        }

        // ON_DAMAGED — 对受害者分发
        TriggerDispatcher.dispatch(TriggerType.ON_DAMAGED, victim) {
            target(attacker)
            set("damage", symphonyEvent.finalDamage)
            set("damageType", "physical")
        }

        if (preEvent.isCritical) {
            TriggerDispatcher.dispatch(TriggerType.ON_ATTACK_CRITICAL, attacker) {
                target(victim)
                set("damage", symphonyEvent.finalDamage)
                set("critMultiplier", effectiveCritMul)
            }
        }

        // ON_LOW_HEALTH — 伤害后检查受害者血量
        if (victim is LivingEntity) {
            val healthAfter = victim.health - symphonyEvent.finalDamage
            val maxHp = victim.getAttribute(Attribute.GENERIC_MAX_HEALTH)?.value ?: 20.0
            if (healthAfter > 0 && healthAfter / maxHp <= 0.3) {
                TriggerDispatcher.dispatch(TriggerType.ON_LOW_HEALTH, victim) {
                    target(attacker)
                    set("healthPercent", healthAfter / maxHp)
                    set("healthRemaining", healthAfter)
                }
            }
        }

        // 更新战斗状态
        updateCombatState(attacker)
        updateCombatState(victim)

        // 更新统计
        if (attacker is Player) {
            val data = PlayerDataManager.getData(attacker.uniqueId)
            if (data != null) {
                data.persistent.statistics.totalDamageDealt += symphonyEvent.finalDamage
                if (symphonyEvent.finalDamage > data.persistent.statistics.highestDamageDealt) {
                    data.persistent.statistics.highestDamageDealt = symphonyEvent.finalDamage
                }
            }
        }
        if (victim is Player) {
            PlayerDataManager.getData(victim.uniqueId)?.let {
                it.persistent.statistics.totalDamageTaken += symphonyEvent.finalDamage
            }
        }

        // ON_DAMAGE 触发（最终伤害确认后）
        TriggerDispatcher.dispatch(TriggerType.ON_DAMAGE, attacker) {
            target(victim)
            set("finalDamage", symphonyEvent.finalDamage)
            set("rawDamage", physicalDamage)
        }
    }

    private fun updateCombatState(entity: LivingEntity) {
        if (entity !is Player) return
        val data = PlayerDataManager.getData(entity.uniqueId) ?: return
        val now = System.currentTimeMillis()
        if (!data.runtime.inCombat) {
            data.runtime.inCombat = true
            data.runtime.combatStartTime = now
            TriggerDispatcher.dispatch(TriggerType.ON_ENTER_COMBAT, entity) {}
        }
        data.runtime.lastCombatActionTime = now
    }

    private data class ElementDef(val id: String, val dmgAttr: String, val resAttr: String, val color: String, val name: String)

    private val ELEMENTS = arrayOf(
        ElementDef("fire", "fire_damage", "fire_resistance", "§c", "火"),
        ElementDef("ice", "ice_damage", "ice_resistance", "§b", "冰"),
        ElementDef("lightning", "lightning_damage", "lightning_resistance", "§d", "雷"),
        ElementDef("poison", "poison_damage", "poison_resistance", "§a", "毒"),
        ElementDef("holy", "holy_damage", "holy_resistance", "§e", "圣"),
        ElementDef("dark", "dark_damage", "dark_resistance", "§8", "暗")
    )

    /** 轻量数字格式化，避免 String.format 开销 */
    private fun formatDmg(value: Double): String {
        val rounded = (value * 10.0 + 0.5).toLong() / 10.0
        return rounded.toString()
    }
}
