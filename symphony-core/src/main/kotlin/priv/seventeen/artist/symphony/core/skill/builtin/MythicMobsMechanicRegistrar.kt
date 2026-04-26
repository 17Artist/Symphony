package priv.seventeen.artist.symphony.core.skill.builtin

import io.lumine.mythic.api.adapters.AbstractEntity
import io.lumine.mythic.api.config.MythicLineConfig
import io.lumine.mythic.api.skills.ITargetedEntitySkill
import io.lumine.mythic.api.skills.SkillMetadata
import io.lumine.mythic.api.skills.SkillResult
import io.lumine.mythic.bukkit.BukkitAdapter
import io.lumine.mythic.bukkit.events.MythicMechanicLoadEvent
import org.bukkit.Bukkit
import org.bukkit.attribute.Attribute
import org.bukkit.entity.LivingEntity
import org.bukkit.entity.Player
import org.bukkit.event.EventHandler
import org.bukkit.event.Listener
import org.bukkit.plugin.Plugin
import priv.seventeen.artist.blink.BlinkLog
import priv.seventeen.artist.symphony.api.attribute.Operation
import priv.seventeen.artist.symphony.core.attribute.AttributeCalculator
import priv.seventeen.artist.symphony.core.data.ActiveBuff
import priv.seventeen.artist.symphony.core.storage.PlayerDataManager
import java.util.UUID

/**
 * MythicMobs Mechanic 注册器。
 *
 * 当前注册的 mechanic：
 * - `symphony_damage{amount=10}` — 走 Symphony 伤害事件
 * - `symphony_heal{amount=5}`
 * - `symphony_buff{id=physical_damage;op=flat;value=10;duration=100}` — 临时 Buff
 */
object MythicMobsMechanicRegistrar : Listener {

    private var registered = false

    fun register(plugin: Plugin) {
        if (registered) return
        if (Bukkit.getPluginManager().getPlugin("MythicMobs") == null) return

        try {
            Bukkit.getPluginManager().registerEvents(this, plugin)
            registered = true
            BlinkLog.info("MythicMobs mechanic 注册已挂载: symphony_damage, symphony_heal, symphony_buff")
        } catch (e: Exception) {
            BlinkLog.error("MythicMobs mechanic 注册挂载失败: ${e.message}")
        }
    }

    @EventHandler
    fun onMechanicLoad(event: MythicMechanicLoadEvent) {
        val name = event.mechanicName.lowercase()
        val config = event.config

        val mechanic: ITargetedEntitySkill = when (name) {
            "symphony_damage" -> SymphonyDamageMechanic(config)
            "symphony_heal" -> SymphonyHealMechanic(config)
            "symphony_buff" -> SymphonyBuffMechanic(config)
            else -> return
        }
        event.register(mechanic)
    }

    private class SymphonyDamageMechanic(config: MythicLineConfig) : ITargetedEntitySkill {
        private val amount = config.getDouble(arrayOf("amount", "a"), 1.0)

        override fun castAtEntity(data: SkillMetadata, target: AbstractEntity): SkillResult {
            val caster = BukkitAdapter.adapt(data.caster.entity) as? LivingEntity
            val victim = BukkitAdapter.adapt(target) as? LivingEntity ?: return SkillResult.ERROR
            victim.damage(amount, caster)
            return SkillResult.SUCCESS
        }
    }

    private class SymphonyHealMechanic(config: MythicLineConfig) : ITargetedEntitySkill {
        private val amount = config.getDouble(arrayOf("amount", "a"), 1.0)

        override fun castAtEntity(data: SkillMetadata, target: AbstractEntity): SkillResult {
            val victim = BukkitAdapter.adapt(target) as? LivingEntity ?: return SkillResult.ERROR
            val max = victim.getAttribute(Attribute.GENERIC_MAX_HEALTH)?.value ?: victim.health
            victim.health = (victim.health + amount).coerceIn(0.0, max)
            return SkillResult.SUCCESS
        }
    }

    private class SymphonyBuffMechanic(config: MythicLineConfig) : ITargetedEntitySkill {
        private val attrId = config.getString(arrayOf("id", "i"), "")
        private val op = if (config.getString(arrayOf("op", "o"), "flat").equals("percent", true)) Operation.PERCENT else Operation.FLAT
        private val value = config.getDouble(arrayOf("value", "v"), 0.0)
        private val duration = config.getInteger(arrayOf("duration", "d"), 100)

        override fun castAtEntity(data: SkillMetadata, target: AbstractEntity): SkillResult {
            if (attrId.isEmpty()) return SkillResult.ERROR
            val victim = BukkitAdapter.adapt(target) as? Player ?: return SkillResult.ERROR
            val playerData = PlayerDataManager.getOrCreate(victim.uniqueId)
            playerData.runtime.activeBuffs.add(ActiveBuff(
                id = "mm:$attrId:${UUID.randomUUID()}",
                attribute = attrId,
                operation = op,
                value = value,
                expireTime = System.currentTimeMillis() + duration * 50L,
                source = "mythic"
            ))
            AttributeCalculator.markDirty(victim)
            return SkillResult.SUCCESS
        }
    }
}
