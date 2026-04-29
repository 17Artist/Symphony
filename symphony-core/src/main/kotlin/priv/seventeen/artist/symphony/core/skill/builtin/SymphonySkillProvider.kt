package priv.seventeen.artist.symphony.core.skill.builtin

import org.bukkit.Bukkit
import org.bukkit.entity.Player
import priv.seventeen.artist.symphony.api.event.SymphonyManaConsumeEvent
import priv.seventeen.artist.symphony.api.skill.*
import priv.seventeen.artist.symphony.core.affix.AffixProcessor
import priv.seventeen.artist.symphony.api.affix.AffixInstance
import priv.seventeen.artist.symphony.core.attribute.AttributeCache
import priv.seventeen.artist.symphony.core.storage.PlayerDataManager
import priv.seventeen.artist.symphony.core.trigger.CooldownManager
import priv.seventeen.artist.symphony.api.trigger.TriggerType
import priv.seventeen.artist.symphony.core.trigger.TriggerDispatcher
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
        val caster = context.caster

        // 冷却检查（只检查，不设置）
        if (skill.cooldown > 0) {
            val cdKey = "skill:$skillId"
            if (CooldownManager.isOnCooldown(caster.uniqueId, cdKey)) return false
        }

        // 法力消耗
        if (skill.manaCost > 0 && caster is Player) {
            val data = PlayerDataManager.getData(caster.uniqueId)
            if (data != null) {
                if (data.runtime.currentMana < skill.manaCost) return false
                // 发布法力消耗事件，允许外部取消或修改消耗量
                val manaEvent = SymphonyManaConsumeEvent(caster, skill.manaCost, "skill:$skillId")
                Bukkit.getPluginManager().callEvent(manaEvent)
                if (manaEvent.isCancelled) return false
                val maxMana = AttributeCache.get(caster.uniqueId, "max_mana") ?: 100.0
                data.runtime.currentMana = (data.runtime.currentMana - manaEvent.amount).coerceIn(0.0, maxMana)
                // ON_MANA_USE 触发器
                TriggerDispatcher.dispatch(TriggerType.ON_MANA_USE, caster) {
                    set("manaCost", manaEvent.amount)
                    set("skillId", skillId)
                    set("currentMana", data.runtime.currentMana)
                }
            }
        }

        // 所有前置检查通过后才设置冷却
        if (skill.cooldown > 0) {
            CooldownManager.setCooldown(caster.uniqueId, "skill:$skillId", skill.cooldown)
        }

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
