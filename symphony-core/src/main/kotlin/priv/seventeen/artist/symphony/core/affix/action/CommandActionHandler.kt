package priv.seventeen.artist.symphony.core.affix.action

import org.bukkit.Bukkit
import org.bukkit.entity.Player
import priv.seventeen.artist.blink.BlinkLog
import priv.seventeen.artist.symphony.api.affix.AffixInstance
import priv.seventeen.artist.symphony.api.trigger.ITriggerContext

class CommandActionHandler : ActionHandler {
    private val blockedCommands = setOf("op", "deop", "stop", "restart", "ban", "pardon", "whitelist")

    override fun execute(params: Map<String, Any>, context: ITriggerContext, affix: AffixInstance) {
        val command = resolveParam(params["command"], affix.parameters)
            .replace("%player%", context.entity.name)
        // 安全检查：阻止危险命令
        val firstWord = command.trim().split(" ").firstOrNull()?.lowercase() ?: return
        if (firstWord in blockedCommands) {
            BlinkLog.warn("词条 ${affix.affixId} 尝试执行被阻止的命令: $command")
            return
        }
        val asType = params["as"]?.toString() ?: "console"
        when (asType) {
            "player" -> (context.entity as? Player)?.performCommand(command)
            else -> Bukkit.dispatchCommand(Bukkit.getConsoleSender(), command)
        }
    }
}
