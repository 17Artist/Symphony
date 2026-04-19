package priv.seventeen.artist.symphony.core.affix.action

import org.bukkit.ChatColor
import org.bukkit.entity.Player
import priv.seventeen.artist.symphony.api.affix.AffixInstance
import priv.seventeen.artist.symphony.api.trigger.ITriggerContext

class MessageActionHandler : ActionHandler {
    override fun execute(params: Map<String, Any>, context: ITriggerContext, affix: AffixInstance) {
        val message = ChatColor.translateAlternateColorCodes('&', resolveParam(params["message"], affix.parameters))
        val player = context.entity as? Player ?: return
        val msgType = params["type"]?.toString() ?: "chat"
        when (msgType) {
            "actionbar" -> player.spigot().sendMessage(
                net.md_5.bungee.api.ChatMessageType.ACTION_BAR,
                net.md_5.bungee.api.chat.TextComponent(message)
            )
            "title" -> player.sendTitle(message, "", 10, 40, 10)
            else -> player.sendMessage(message)
        }
    }
}
