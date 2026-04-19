package priv.seventeen.artist.symphony.core.affix.action

import priv.seventeen.artist.symphony.api.affix.AffixInstance
import priv.seventeen.artist.symphony.api.trigger.ITriggerContext

class SoundActionHandler : ActionHandler {
    override fun execute(params: Map<String, Any>, context: ITriggerContext, affix: AffixInstance) {
        val sound = resolveParam(params["sound"], affix.parameters)
        val volume = resolveDouble(params["volume"], affix.parameters).let { if (it == 0.0) 1.0 else it }.toFloat()
        val pitch = resolveDouble(params["pitch"], affix.parameters).let { if (it == 0.0) 1.0 else it }.toFloat()
        val target = context.target ?: context.entity
        target.world.playSound(target.location, sound, volume, pitch)
    }
}
