package priv.seventeen.artist.symphony.core.growth.rune

import org.bukkit.Bukkit
import org.bukkit.entity.Player
import priv.seventeen.artist.symphony.api.event.RuneActivateEvent
import priv.seventeen.artist.symphony.core.attribute.AttributeCalculator
import priv.seventeen.artist.symphony.core.data.RuneData
import priv.seventeen.artist.symphony.core.storage.PlayerDataManager

class RuneManager {

    fun activateRune(player: Player, runeId: String, level: Int): Boolean {
        val data = PlayerDataManager.getData(player.uniqueId) ?: return false
        val event = RuneActivateEvent(player, runeId, level)
        Bukkit.getPluginManager().callEvent(event)
        if (event.isCancelled) return false
        data.persistent.runes[runeId] = RuneData(runeId, level, true)
        data.dirty = true
        AttributeCalculator.markDirty(player)
        return true
    }

    fun deactivateRune(player: Player, runeId: String) {
        val data = PlayerDataManager.getData(player.uniqueId) ?: return
        data.persistent.runes[runeId]?.active = false
        data.dirty = true
        AttributeCalculator.markDirty(player)
    }

    fun addFragments(player: Player, runeId: String, amount: Int) {
        val data = PlayerDataManager.getData(player.uniqueId) ?: return
        data.persistent.runeFragments[runeId] = (data.persistent.runeFragments[runeId] ?: 0) + amount
        data.dirty = true
    }

    fun getFragments(player: Player, runeId: String): Int {
        return PlayerDataManager.getData(player.uniqueId)?.persistent?.runeFragments?.get(runeId) ?: 0
    }

    fun getRunes(player: Player): Map<String, RuneData> {
        return PlayerDataManager.getData(player.uniqueId)?.persistent?.runes ?: emptyMap()
    }
}
