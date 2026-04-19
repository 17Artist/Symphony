package priv.seventeen.artist.symphony.core.affix.action

import org.bukkit.Particle
import priv.seventeen.artist.symphony.api.affix.AffixInstance
import priv.seventeen.artist.symphony.api.trigger.ITriggerContext

class ParticleActionHandler : ActionHandler {
    override fun execute(params: Map<String, Any>, context: ITriggerContext, affix: AffixInstance) {
        val particleName = resolveParam(params["particle"], affix.parameters)
        val count = resolveInt(params["count"], affix.parameters).coerceAtLeast(1)
        val particle = try { Particle.valueOf(particleName.uppercase()) } catch (e: Exception) { return }
        val target = context.target ?: context.entity
        target.world.spawnParticle(particle, target.location.add(0.0, 1.0, 0.0), count, 0.5, 0.5, 0.5)
    }
}
