package priv.seventeen.artist.symphony.api.skill

interface ISkillProviderManager {
    fun registerProvider(provider: ISkillProvider)
    fun unregisterProvider(id: String)
    fun getProvider(id: String): ISkillProvider?
    fun getAllProviders(): Collection<ISkillProvider>

    fun castSkill(providerId: String, skillId: String, level: Int, context: SkillContext): Boolean
    fun hasSkill(providerId: String, skillId: String): Boolean
    fun getSkillInfo(providerId: String, skillId: String, level: Int): SkillInfo?
}
