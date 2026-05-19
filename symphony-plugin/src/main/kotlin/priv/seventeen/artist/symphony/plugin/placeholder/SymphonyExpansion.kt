package priv.seventeen.artist.symphony.plugin.placeholder

import me.clip.placeholderapi.expansion.PlaceholderExpansion
import org.bukkit.entity.Player
import priv.seventeen.artist.blink.bukkitPlugin

class SymphonyExpansion : PlaceholderExpansion() {

    override fun getIdentifier(): String = "symphony"

    override fun getAuthor(): String = "Symphony Team"

    override fun getVersion(): String = bukkitPlugin.description.version

    override fun persist(): Boolean = true

    override fun canRegister(): Boolean = true

    override fun onPlaceholderRequest(player: Player?, params: String): String? {
        if (player == null) return null
        return SymphonyPlaceholder.onRequest(player, params)
    }
}
