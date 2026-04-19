package priv.seventeen.artist.symphony.core.skill.builtin

import org.bukkit.Bukkit
import priv.seventeen.artist.blink.BlinkLog
import priv.seventeen.artist.symphony.api.skill.*

/**
 * MythicMobs 技能桥接提供者。
 * 通过反射调用 MythicMobs API，避免硬依赖。
 */
class MythicMobsBridge : ISkillProvider {
    override val id = "mythicmobs"
    override val displayName = "MythicMobs"
    private var available = false

    init {
        available = Bukkit.getPluginManager().getPlugin("MythicMobs") != null
        if (available) BlinkLog.info("MythicMobs 桥接已启用")
    }

    fun isAvailable() = available

    override fun cast(skillId: String, level: Int, context: SkillContext): Boolean {
        if (!available) return false
        return try {
            val mmClass = Class.forName("io.lumine.mythic.bukkit.MythicBukkit")
            val inst = mmClass.getMethod("inst").invoke(null)
            val skillManager = inst.javaClass.getMethod("getSkillManager").invoke(inst)
            val optionalSkill = skillManager.javaClass.getMethod("getSkill", String::class.java).invoke(skillManager, skillId)

            val isPresent = optionalSkill.javaClass.getMethod("isPresent").invoke(optionalSkill) as Boolean
            if (!isPresent) return false

            val skill = optionalSkill.javaClass.getMethod("get").invoke(optionalSkill)

            // 创建 caster
            val getCaster = skillManager.javaClass.getMethod("getCaster", Any::class.java)
            val caster = getCaster.invoke(skillManager, context.caster)

            // 执行技能
            val executeMethod = skill.javaClass.methods.find { it.name == "execute" && it.parameterCount == 1 }
            if (executeMethod != null) {
                executeMethod.invoke(skill, caster)
                return true
            }
            false
        } catch (e: Exception) {
            BlinkLog.warn("MythicMobs 技能执行失败 $skillId: ${e.message}")
            false
        }
    }

    override fun hasSkill(skillId: String): Boolean {
        if (!available) return false
        return try {
            val mmClass = Class.forName("io.lumine.mythic.bukkit.MythicBukkit")
            val inst = mmClass.getMethod("inst").invoke(null)
            val skillManager = inst.javaClass.getMethod("getSkillManager").invoke(inst)
            val optional = skillManager.javaClass.getMethod("getSkill", String::class.java).invoke(skillManager, skillId)
            optional.javaClass.getMethod("isPresent").invoke(optional) as Boolean
        } catch (e: Exception) { false }
    }

    override fun getSkillInfo(skillId: String, level: Int): SkillInfo? {
        if (!hasSkill(skillId)) return null
        return SkillInfo(skillId, skillId, emptyList(), 1, 0, 0.0)
    }

    override fun getSkillIds(): List<String> {
        if (!available) return emptyList()
        return try {
            val mmClass = Class.forName("io.lumine.mythic.bukkit.MythicBukkit")
            val inst = mmClass.getMethod("inst").invoke(null)
            val skillManager = inst.javaClass.getMethod("getSkillManager").invoke(inst)
            val skills = skillManager.javaClass.getMethod("getSkillNames").invoke(skillManager)
            @Suppress("UNCHECKED_CAST")
            (skills as? Collection<String>)?.toList() ?: emptyList()
        } catch (e: Exception) { emptyList() }
    }
}
