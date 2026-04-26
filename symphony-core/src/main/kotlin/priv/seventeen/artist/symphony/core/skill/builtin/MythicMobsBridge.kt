package priv.seventeen.artist.symphony.core.skill.builtin

import io.lumine.mythic.bukkit.BukkitAdapter
import io.lumine.mythic.bukkit.MythicBukkit
import org.bukkit.Bukkit
import org.bukkit.entity.LivingEntity
import priv.seventeen.artist.blink.BlinkLog
import priv.seventeen.artist.symphony.api.skill.*

/**
 * MythicMobs 技能桥接提供者。
 * 通过 MythicMobs API 直接调用，需要 compileOnly 依赖。
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
            val mm = MythicBukkit.inst()
            val skill = mm.skillManager.getSkill(skillId).orElse(null) ?: return false
            val casterEntity = context.caster as? LivingEntity ?: return false
            mm.apiHelper.castSkill(casterEntity, skillId)
            true
        } catch (e: Exception) {
            BlinkLog.warn("MythicMobs 技能执行失败 $skillId: ${e.message}")
            false
        }
    }

    override fun hasSkill(skillId: String): Boolean {
        if (!available) return false
        return try {
            MythicBukkit.inst().skillManager.getSkill(skillId).isPresent
        } catch (_: Exception) { false }
    }

    override fun getSkillInfo(skillId: String, level: Int): SkillInfo? {
        if (!hasSkill(skillId)) return null
        return SkillInfo(skillId, skillId, emptyList(), 1, 0, 0.0)
    }

    override fun getSkillIds(): List<String> {
        if (!available) return emptyList()
        return try {
            MythicBukkit.inst().skillManager.skillNames.toList()
        } catch (_: Exception) { emptyList() }
    }
}
