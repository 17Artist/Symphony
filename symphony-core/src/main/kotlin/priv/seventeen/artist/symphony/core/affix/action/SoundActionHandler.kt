package priv.seventeen.artist.symphony.core.affix.action

import org.bukkit.entity.LivingEntity
import priv.seventeen.artist.symphony.api.affix.AffixInstance
import priv.seventeen.artist.symphony.api.trigger.ITriggerContext

class SoundActionHandler : ActionHandler {
    override fun execute(params: Map<String, Any>, context: ITriggerContext, affix: AffixInstance) {
        val sound = resolveParam(params["sound"], affix.parameters)
        val volume = resolveDouble(params["volume"], affix.parameters).let { if (it == 0.0) 1.0 else it }.toFloat()
        val pitch = resolveDouble(params["pitch"], affix.parameters).let { if (it == 0.0) 1.0 else it }.toFloat()
        val targets = resolveTargets(params, context, affix.parameters,
            defaultTarget = (context.target ?: context.entity) as? LivingEntity)
        for (target in targets) {
            target.world.playSound(target.location, sound, volume, pitch)
        }
    }
}
