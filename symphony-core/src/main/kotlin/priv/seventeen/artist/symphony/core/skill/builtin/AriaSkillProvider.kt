package priv.seventeen.artist.symphony.core.skill.builtin

import priv.seventeen.artist.blink.BlinkLog
import priv.seventeen.artist.symphony.api.skill.*
import priv.seventeen.artist.symphony.core.script.SymphonyScriptEngine
import java.util.concurrent.ConcurrentHashMap

class AriaSkillProvider(private val scriptEngine: SymphonyScriptEngine) : ISkillProvider {
    override val id = "aria"
    override val displayName = "Aria Script"

    data class ScriptSkill(
        val id: String,
        val displayName: String,
        val description: List<String> = emptyList(),
        val maxLevel: Int = 1,
        val cooldown: Long = 0,
        val manaCost: Double = 0.0,
        val script: String
    )

    private val skills = ConcurrentHashMap<String, ScriptSkill>()

    fun register(skill: ScriptSkill) { skills[skill.id] = skill }
    fun clear() = skills.clear()

    override fun cast(skillId: String, level: Int, context: SkillContext): Boolean {
        val skill = skills[skillId] ?: return false
        val vars = buildMap<String, Any> {
            put("caster", context.caster)
            put("skill_id", skillId)
            put("skill_level", level)
            put("origin", context.origin)
            context.target?.let { put("target", it) }
            put("targets", context.targets)
            context.triggerContext?.let { tc ->
                put("trigger_type", tc.triggerType.id)
                tc.target?.let { put("trigger_target", it) }
            }
        }
        return try {
            scriptEngine.eval(skill.script, vars)
            true
        } catch (e: Exception) {
            BlinkLog.warn("Aria 技能执行失败 $skillId: ${e.message}")
            false
        }
    }

    override fun hasSkill(skillId: String) = skills.containsKey(skillId)

    override fun getSkillInfo(skillId: String, level: Int): SkillInfo? {
        val s = skills[skillId] ?: return null
        return SkillInfo(s.id, s.displayName, s.description, s.maxLevel, s.cooldown, s.manaCost)
    }

    override fun getSkillIds(): List<String> = skills.keys.toList()
}
