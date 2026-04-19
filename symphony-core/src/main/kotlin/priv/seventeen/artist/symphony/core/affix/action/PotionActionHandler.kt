package priv.seventeen.artist.symphony.core.affix.action

import org.bukkit.potion.PotionEffect
import org.bukkit.potion.PotionEffectType
import priv.seventeen.artist.symphony.api.affix.AffixInstance
import priv.seventeen.artist.symphony.api.trigger.ITriggerContext

class PotionActionHandler : ActionHandler {
    override fun execute(params: Map<String, Any>, context: ITriggerContext, affix: AffixInstance) {
        val effectName = resolveParam(params["effect"], affix.parameters)
        val duration = resolveInt(params["duration"], affix.parameters) * 20 // seconds to ticks
        val amplifier = resolveInt(params["amplifier"], affix.parameters)
        val effectType = PotionEffectType.getByName(effectName) ?: return
        val target = context.target ?: context.entity
        target.addPotionEffect(PotionEffect(effectType, duration, amplifier))
    }
}
