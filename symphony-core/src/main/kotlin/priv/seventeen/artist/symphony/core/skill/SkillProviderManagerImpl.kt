package priv.seventeen.artist.symphony.core.skill

import priv.seventeen.artist.symphony.api.skill.*
import java.util.concurrent.ConcurrentHashMap

class SkillProviderManagerImpl : ISkillProviderManager {
    private val providers = ConcurrentHashMap<String, ISkillProvider>()

    override fun registerProvider(provider: ISkillProvider) {
        providers[provider.id] = provider
    }

    override fun unregisterProvider(id: String) {
        providers.remove(id)
    }

    override fun getProvider(id: String): ISkillProvider? = providers[id]

    override fun getAllProviders(): Collection<ISkillProvider> = providers.values

    override fun castSkill(providerId: String, skillId: String, level: Int, context: SkillContext): Boolean {
        return SkillDispatcher.dispatch(providerId, skillId, level, context)
    }

    override fun hasSkill(providerId: String, skillId: String): Boolean {
        return providers[providerId]?.hasSkill(skillId) ?: false
    }

    override fun getSkillInfo(providerId: String, skillId: String, level: Int): SkillInfo? {
        return providers[providerId]?.getSkillInfo(skillId, level)
    }
}
