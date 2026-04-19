package priv.seventeen.artist.symphony.core.skill

import org.bukkit.Bukkit
import priv.seventeen.artist.blink.BlinkLog
import priv.seventeen.artist.symphony.api.event.SkillCastEvent
import priv.seventeen.artist.symphony.api.skill.SkillContext

object SkillDispatcher {
    lateinit var providerManager: SkillProviderManagerImpl

    fun dispatch(providerId: String, skillId: String, level: Int, context: SkillContext): Boolean {
        if (!::providerManager.isInitialized) return false
        val provider = providerManager.getProvider(providerId) ?: run {
            BlinkLog.warn("Unknown skill provider: $providerId")
            return false
        }

        if (!provider.hasSkill(skillId)) {
            BlinkLog.warn("Skill not found: $providerId:$skillId")
            return false
        }

        val event = SkillCastEvent(context.caster, providerId, skillId, level, context)
        Bukkit.getPluginManager().callEvent(event)
        if (event.isCancelled) return false

        return provider.cast(skillId, level, context)
    }
}
