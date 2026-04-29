package priv.seventeen.artist.symphony.core.skill

import org.bukkit.Bukkit
import priv.seventeen.artist.blink.BlinkLog
import priv.seventeen.artist.symphony.api.event.SkillCastEvent
import priv.seventeen.artist.symphony.api.skill.SkillContext
import priv.seventeen.artist.symphony.api.trigger.TriggerType
import priv.seventeen.artist.symphony.core.trigger.TriggerDispatcher

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

        val result = provider.cast(skillId, level, context)

        // ON_SKILL_CAST 触发器
        if (result) {
            TriggerDispatcher.dispatch(TriggerType.ON_SKILL_CAST, context.caster) {
                context.target?.let { target(it) }
                set("skillId", skillId)
                set("providerId", providerId)
                set("skillLevel", level)
            }
        }

        return result
    }
}
