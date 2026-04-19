package priv.seventeen.artist.symphony.core.affix.action

import priv.seventeen.artist.symphony.api.affix.AffixInstance
import priv.seventeen.artist.symphony.api.trigger.ITriggerContext
import priv.seventeen.artist.symphony.core.advanced.status.StatusLayerSystem

class StatusStackActionHandler : ActionHandler {
    override fun execute(params: Map<String, Any>, context: ITriggerContext, affix: AffixInstance) {
        val statusId = resolveParam(params["status"], affix.parameters)
        val stacks = resolveInt(params["stacks"], affix.parameters).coerceAtLeast(1)
        val target = context.target ?: context.entity
        StatusLayerSystem.addStacks(target, statusId, stacks, context.entity.uniqueId)
    }
}
