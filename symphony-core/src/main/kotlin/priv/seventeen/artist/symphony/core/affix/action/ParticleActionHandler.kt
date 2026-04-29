package priv.seventeen.artist.symphony.core.affix.action

import org.bukkit.Particle
import org.bukkit.entity.LivingEntity
import priv.seventeen.artist.symphony.api.affix.AffixInstance
import priv.seventeen.artist.symphony.api.trigger.ITriggerContext

class ParticleActionHandler : ActionHandler {
    override fun execute(params: Map<String, Any>, context: ITriggerContext, affix: AffixInstance) {
        val particleName = resolveParam(params["particle"], affix.parameters)
        val count = resolveInt(params["count"], affix.parameters).coerceAtLeast(1)
        val particle = try { Particle.valueOf(particleName.uppercase()) } catch (e: Exception) { return }
        // 解析 offset 参数，支持 [x, y, z] 列表格式
        val offset = (params["offset"] as? List<*>)?.let {
            Triple(
                (it.getOrNull(0) as? Number)?.toDouble() ?: 0.5,
                (it.getOrNull(1) as? Number)?.toDouble() ?: 0.5,
                (it.getOrNull(2) as? Number)?.toDouble() ?: 0.5
            )
        } ?: Triple(0.5, 0.5, 0.5)
        val targets = resolveTargets(params, context, affix.parameters,
            defaultTarget = (context.target ?: context.entity) as? LivingEntity)
        for (target in targets) {
            target.world.spawnParticle(particle, target.location.add(0.0, 1.0, 0.0), count, offset.first, offset.second, offset.third)
        }
    }
}
