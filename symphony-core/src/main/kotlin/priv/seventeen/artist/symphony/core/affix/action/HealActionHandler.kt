package priv.seventeen.artist.symphony.core.affix.action

import org.bukkit.Bukkit
import org.bukkit.attribute.Attribute
import priv.seventeen.artist.symphony.api.affix.AffixInstance
import priv.seventeen.artist.symphony.api.event.SymphonyHealEvent
import priv.seventeen.artist.symphony.api.trigger.ITriggerContext

class HealActionHandler : ActionHandler {
    override fun execute(params: Map<String, Any>, context: ITriggerContext, affix: AffixInstance) {
        val amount = resolveDouble(params["amount"], affix.parameters)
        val target = context.entity
        val event = SymphonyHealEvent(target, null, amount, "affix:${affix.affixId}")
        Bukkit.getPluginManager().callEvent(event)
        if (event.isCancelled) return
        val maxHealth = target.getAttribute(Attribute.GENERIC_MAX_HEALTH)?.value ?: 20.0
        target.health = minOf(target.health + event.amount, maxHealth)
    }
}
