package priv.seventeen.artist.symphony.core.affix.action

import org.bukkit.entity.LivingEntity
import org.bukkit.event.entity.EntityDamageEvent
import priv.seventeen.artist.symphony.api.affix.AffixInstance
import priv.seventeen.artist.symphony.api.trigger.ITriggerContext
import priv.seventeen.artist.symphony.api.trigger.TriggerType
import priv.seventeen.artist.symphony.core.trigger.TriggerDispatcher

class DamageActionHandler : ActionHandler {
    override fun execute(params: Map<String, Any>, context: ITriggerContext, affix: AffixInstance) {
        val amount = resolveDouble(params["amount"], affix.parameters)
        val damageType = resolveParam(params["damage_type"] ?: params["type"], affix.parameters).ifEmpty { null }
        val targets = resolveTargets(params, context, affix.parameters, defaultTarget = context.target as? LivingEntity)
        if (targets.isEmpty()) return
        val isSkillDamage = affix.affixId.startsWith("skill:")
        for (target in targets) {
            if (damageType != null) {
                val cause = runCatching { EntityDamageEvent.DamageCause.valueOf(damageType.uppercase()) }.getOrNull()
                if (cause == EntityDamageEvent.DamageCause.FIRE || cause == EntityDamageEvent.DamageCause.FIRE_TICK) {
                    target.fireTicks = maxOf(target.fireTicks, 60)
                }
            }
            target.damage(amount, context.entity)
            // ON_SKILL_HIT — 技能伤害命中目标时触发
            if (isSkillDamage) {
                TriggerDispatcher.dispatch(TriggerType.ON_SKILL_HIT, context.entity) {
                    target(target)
                    set("damage", amount)
                    set("skillId", affix.affixId.removePrefix("skill:"))
                }
            }
        }
    }
}
