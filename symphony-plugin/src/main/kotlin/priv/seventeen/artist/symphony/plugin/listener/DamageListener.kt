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
import priv.seventeen.artist.symphony.core.attribute.AttributeCalculator
import priv.seventeen.artist.symphony.core.storage.PlayerDataManager
import priv.seventeen.artist.symphony.core.trigger.TriggerDispatcher
import java.util.concurrent.ThreadLocalRandom

object DamageListener {

    @AutoListener(priority = EventPriority.NORMAL)
    fun onDamage(event: EntityDamageEvent) {
        if (event !is EntityDamageByEntityEvent) return
        val victim = event.entity as? LivingEntity ?: return
        var attacker = event.damager as? LivingEntity
        if (attacker == null && event.damager is Projectile) {
            attacker = (event.damager as Projectile).shooter as? LivingEntity
        }
        if (attacker == null) return

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
        val elements = listOf("fire", "ice", "lightning", "poison", "holy", "dark")
        for (elem in elements) {
            val elemDmg = AttributeCalculator.getValue(attacker, "${elem}_damage")
            if (elemDmg > 0) {
                val elemRes = AttributeCalculator.getValue(victim, "${elem}_resistance")
                val elemFinal = elemDmg * maxOf(0.0, 1.0 - elemRes)
                if (elemFinal > 0) {
                    symphonyEvent.elementDamages[elem] = elemFinal
                    symphonyEvent.finalDamage += elemFinal
                }
            }
        }

        event.damage = symphonyEvent.finalDamage

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
}
