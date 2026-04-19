package priv.seventeen.artist.symphony.core.affix.action

import priv.seventeen.artist.symphony.api.affix.AffixInstance
import priv.seventeen.artist.symphony.api.trigger.ITriggerContext

class DamageActionHandler : ActionHandler {
    override fun execute(params: Map<String, Any>, context: ITriggerContext, affix: AffixInstance) {
        val amount = resolveDouble(params["amount"], affix.parameters)
        val target = context.target ?: return
        target.damage(amount, context.entity)
    }
}
