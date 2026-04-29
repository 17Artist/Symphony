package priv.seventeen.artist.symphony.core.growth.level

import org.bukkit.Bukkit
import org.bukkit.Particle
import org.bukkit.Sound
import org.bukkit.entity.Player
import priv.seventeen.artist.blink.BlinkLog
import priv.seventeen.artist.symphony.api.event.LevelChangeEvent
import priv.seventeen.artist.symphony.api.trigger.TriggerType
import priv.seventeen.artist.symphony.core.attribute.AttributeCache
import priv.seventeen.artist.symphony.core.attribute.AttributeCalculator
import priv.seventeen.artist.symphony.core.script.AriaCallbackManager
import priv.seventeen.artist.symphony.core.storage.PlayerDataManager
import priv.seventeen.artist.symphony.core.trigger.TriggerDispatcher

class LevelManager {
    var maxLevel = 100
    var expFormulaScript: String? = null
    var attributeGrowth: Map<String, GrowthEntry> = emptyMap()
    var levelUpSound: String? = null
    var levelUpParticle: String? = null
    var levelUpTitleMain: String? = null
    var levelUpTitleSub: String? = null
    var levelUpTitleFadeIn: Int = 10
    var levelUpTitleStay: Int = 40
    var levelUpTitleFadeOut: Int = 10

    data class GrowthEntry(val base: Double, val perLevel: Double, val formula: String? = null)

    fun getLevel(player: Player): Int {
        return PlayerDataManager.getData(player.uniqueId)?.persistent?.level ?: 1
    }

    fun setLevel(player: Player, level: Int) {
        val data = PlayerDataManager.getData(player.uniqueId) ?: return
        val old = data.persistent.level
        val capped = level.coerceIn(1, maxLevel)
        if (old == capped) return
        val event = LevelChangeEvent(player, old, capped)
        Bukkit.getPluginManager().callEvent(event)
        if (event.isCancelled) return
        data.persistent.level = capped
        data.dirty = true
        AttributeCalculator.markDirty(player)
    }

    fun addExp(player: Player, amount: Long, source: String) {
        val data = PlayerDataManager.getData(player.uniqueId) ?: return
        val bonusMultiplier = 1.0 + (AttributeCache.get(player.uniqueId, "exp_bonus") ?: 0.0)
        val effectiveAmount = (amount * bonusMultiplier).toLong()
        data.persistent.exp += effectiveAmount

        while (data.persistent.exp >= getRequiredExp(data.persistent.level) && data.persistent.level < maxLevel) {
            data.persistent.exp -= getRequiredExp(data.persistent.level)
            val oldLevel = data.persistent.level
            data.persistent.level++
            onLevelUp(player, oldLevel, data.persistent.level)
        }
        data.dirty = true
    }

    fun getExp(player: Player): Long {
        return PlayerDataManager.getData(player.uniqueId)?.persistent?.exp ?: 0
    }

    fun getRequiredExp(level: Int): Long {
        expFormulaScript?.let { script ->
            try {
                val callbackId = "exp_formula"
                if (!AriaCallbackManager.has(callbackId)) {
                    AriaCallbackManager.compile(callbackId, script)
                }
                val result = AriaCallbackManager.invoke(callbackId, level)
                return (result as? Number)?.toLong() ?: (100 * Math.pow(level.toDouble(), 1.5) + level * 50).toLong()
            } catch (_: Exception) {}
        }
        return (100 * Math.pow(level.toDouble(), 1.5) + level * 50).toLong()
    }

    private fun onLevelUp(player: Player, oldLevel: Int, newLevel: Int) {
        Bukkit.getPluginManager().callEvent(LevelChangeEvent(player, oldLevel, newLevel))
        TriggerDispatcher.dispatch(TriggerType.ON_LEVEL_UP, player) {
            set("oldLevel", oldLevel)
            set("newLevel", newLevel)
        }
        AttributeCalculator.markDirty(player)
        BlinkLog.info("${player.name} 升级: $oldLevel → $newLevel")
        // 升级特效
        levelUpSound?.let { soundName ->
            try {
                // 兼容点分隔格式（entity.player.levelup → ENTITY_PLAYER_LEVELUP）
                val enumName = soundName.uppercase().replace(".", "_")
                val sound = Sound.valueOf(enumName)
                player.playSound(player.location, sound, 1.0f, 1.0f)
            } catch (_: Exception) {
                // fallback: 直接用字符串播放（支持自定义音效）
                try { player.playSound(player.location, soundName, 1.0f, 1.0f) } catch (_: Exception) {}
            }
        }
        levelUpParticle?.let { particleName ->
            try {
                val particle = Particle.valueOf(particleName.uppercase())
                player.world.spawnParticle(particle, player.location.add(0.0, 1.0, 0.0), 30, 0.5, 0.5, 0.5)
            } catch (_: Exception) {}
        }
        levelUpTitleMain?.let { main ->
            val sub = levelUpTitleSub ?: ""
            player.sendTitle(
                main.replace("{level}", newLevel.toString()).replace("{old_level}", oldLevel.toString()).replace("{new_level}", newLevel.toString()),
                sub.replace("{level}", newLevel.toString()).replace("{old_level}", oldLevel.toString()).replace("{new_level}", newLevel.toString()),
                levelUpTitleFadeIn, levelUpTitleStay, levelUpTitleFadeOut
            )
        }
    }
}
