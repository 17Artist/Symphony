package priv.seventeen.artist.symphony.core.growth.level

import org.bukkit.Bukkit
import org.bukkit.entity.Player
import priv.seventeen.artist.blink.BlinkLog
import priv.seventeen.artist.symphony.api.event.LevelChangeEvent
import priv.seventeen.artist.symphony.api.trigger.TriggerType
import priv.seventeen.artist.symphony.core.attribute.AttributeCalculator
import priv.seventeen.artist.symphony.core.storage.PlayerDataManager
import priv.seventeen.artist.symphony.core.trigger.TriggerDispatcher

class LevelManager {
    var maxLevel = 100

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
        data.persistent.exp += amount

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
    }
}
