package priv.seventeen.artist.symphony.core.affix.action

import org.bukkit.Bukkit
import org.bukkit.attribute.Attribute
import priv.seventeen.artist.symphony.api.affix.AffixInstance
import priv.seventeen.artist.symphony.api.event.SymphonyHealEvent
import priv.seventeen.artist.symphony.api.trigger.ITriggerContext

class HealActionHandler : ActionHandler {
    override fun execute(params: Map<String, Any>, context: ITriggerContext, affix: AffixInstance) {
        val amount = resolveDouble(params["amount"], affix.parameters)
        val targets = resolveTargets(params, context, affix.parameters, defaultTarget = context.entity)
        for (target in targets) {
            val event = SymphonyHealEvent(target, null, amount, "affix:${affix.affixId}")
            Bukkit.getPluginManager().callEvent(event)
            if (event.isCancelled) continue
            val maxHealth = target.getAttribute(Attribute.GENERIC_MAX_HEALTH)?.value ?: 20.0
            target.health = minOf(target.health + event.amount, maxHealth)
        }
    }
}
