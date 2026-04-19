package priv.seventeen.artist.symphony.core.affix.action

import priv.seventeen.artist.symphony.api.affix.AffixInstance
import priv.seventeen.artist.symphony.api.skill.SkillContext
import priv.seventeen.artist.symphony.api.trigger.ITriggerContext
import priv.seventeen.artist.symphony.core.skill.SkillDispatcher

class SkillActionHandler : ActionHandler {
    override fun execute(params: Map<String, Any>, context: ITriggerContext, affix: AffixInstance) {
        val provider = resolveParam(params["provider"], affix.parameters)
        val skill = resolveParam(params["skill"], affix.parameters)
        val level = resolveInt(params["level"], affix.parameters).coerceAtLeast(1)

        val skillContext = SkillContext(
            caster = context.entity,
            target = context.target,
            targets = listOfNotNull(context.target),
            origin = context.location,
            triggerContext = context,
            parameters = affix.parameters
        )

        SkillDispatcher.dispatch(provider, skill, level, skillContext)
    }
}
