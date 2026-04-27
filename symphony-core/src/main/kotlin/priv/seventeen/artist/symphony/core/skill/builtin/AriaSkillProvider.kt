package priv.seventeen.artist.symphony.core.skill.builtin

import priv.seventeen.artist.aria.Aria
import priv.seventeen.artist.aria.api.AriaCompiledRoutine
import priv.seventeen.artist.blink.BlinkLog
import priv.seventeen.artist.symphony.api.skill.*
import priv.seventeen.artist.symphony.core.script.SymphonyScriptEngine
import java.util.concurrent.ConcurrentHashMap

class AriaSkillProvider : ISkillProvider {
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
    private val compiled = ConcurrentHashMap<String, AriaCompiledRoutine>()

    fun register(skill: ScriptSkill) {
        skills[skill.id] = skill
        try {
            compiled[skill.id] = Aria.compile("skill:${skill.id}", skill.script)
        } catch (e: Exception) {
            BlinkLog.warn("Aria 技能编译失败 ${skill.id}: ${e.message}")
        }
    }

    fun clear() {
        skills.clear()
        compiled.clear()
    }

    override fun cast(skillId: String, level: Int, context: SkillContext): Boolean {
        val routine = compiled[skillId] ?: return false
        return try {
            val ctx = Aria.createContext()
            val vars = buildMap<String, Any?> {
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
            SymphonyScriptEngine.injectVars(ctx, vars)
            routine.execute(ctx)
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
