package priv.seventeen.artist.symphony.api.skill

interface ISkillProvider {
    val id: String
    val displayName: String

    fun cast(skillId: String, level: Int, context: SkillContext): Boolean
    fun hasSkill(skillId: String): Boolean
    fun getSkillInfo(skillId: String, level: Int): SkillInfo?
    fun getSkillIds(): List<String>
}
