package priv.seventeen.artist.symphony.core.skill.builtin

import priv.seventeen.artist.symphony.api.skill.*
import priv.seventeen.artist.symphony.core.affix.AffixProcessor
import priv.seventeen.artist.symphony.api.affix.AffixInstance
import java.util.UUID
import java.util.concurrent.ConcurrentHashMap

/**
 * Symphony 内置技能提供者 — YAML 配置的 Action 序列。
 */
class SymphonySkillProvider : ISkillProvider {
    override val id = "symphony"
    override val displayName = "Symphony"

    private val skills = ConcurrentHashMap<String, SkillDefinition>()

    data class SkillDefinition(
        val id: String,
        val displayName: String,
        val description: List<String> = emptyList(),
        val maxLevel: Int = 1,
        val cooldown: Long = 0,
        val manaCost: Double = 0.0,
        val levels: Map<Int, Map<String, Any>> = emptyMap(),
        val actions: List<Map<String, Any>> = emptyList()
    )

    fun registerSkill(skill: SkillDefinition) {
        skills[skill.id] = skill
    }

    override fun cast(skillId: String, level: Int, context: SkillContext): Boolean {
        val skill = skills[skillId] ?: return false
        val params = skill.levels[level] ?: skill.levels[1] ?: emptyMap()
        val fakeAffix = AffixInstance(UUID.randomUUID(), "skill:$skillId", level, params)

        if (context.triggerContext != null) {
            AffixProcessor.executeActions(skill.actions, context.triggerContext!!, fakeAffix)
        }
        return true
    }

    override fun hasSkill(skillId: String): Boolean = skills.containsKey(skillId)

    override fun getSkillInfo(skillId: String, level: Int): SkillInfo? {
        val skill = skills[skillId] ?: return null
        return SkillInfo(skill.id, skill.displayName, skill.description, skill.maxLevel, skill.cooldown, skill.manaCost)
    }

    override fun getSkillIds(): List<String> = skills.keys.toList()

    fun clear() = skills.clear()
}
