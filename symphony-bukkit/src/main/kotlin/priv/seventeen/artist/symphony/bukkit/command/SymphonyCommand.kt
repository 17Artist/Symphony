/*
 * Copyright 2026 17Artist
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package priv.seventeen.artist.symphony.bukkit.command

import org.bukkit.Bukkit
import org.bukkit.command.Command
import org.bukkit.command.CommandMap
import org.bukkit.command.CommandSender
import org.bukkit.command.ConsoleCommandSender
import org.bukkit.entity.Player
import org.bukkit.plugin.java.JavaPlugin
import priv.seventeen.artist.blink.BlinkLog
import priv.seventeen.artist.blink.command.CommandContext
import priv.seventeen.artist.blink.command.SenderType
import priv.seventeen.artist.symphony.bukkit.runtime.SymphonyRuntime

/**
 * Symphony 的轻量命令路由器。
 *
 * Blink 1.3.14 的命令实现在补全时会直接读取 args[0]，即使 Bukkit 传入的是空参数数组。
 * 将边界保留在这里，还能确保框架校验消息使用 Symphony 语言文件，避免向玩家暴露
 * 实现名称或硬编码文本。
 */
internal class SymphonyCommand(name: String, vararg aliases: String) :
    Command(name, "", "/$name", aliases.toList()) {

    private data class Spec(
        val name: String,
        val description: String,
        val args: Array<String>,
        val sender: SenderType,
        val handler: (CommandContext) -> Unit
    ) {
        val requiredArgs: Int = args.count { !it.startsWith("?") }
    }

    private val commands = linkedMapOf<String, Spec>()
    private val normalizedNames = hashMapOf<String, String>()
    private val tabProviders = hashMapOf<String, () -> Collection<String>>()

    fun command(
        name: String,
        description: String = "",
        args: Array<String> = emptyArray(),
        sender: SenderType = SenderType.ALL,
        handler: (CommandContext) -> Unit
    ): SymphonyCommand {
        val spec = Spec(name, description, args.copyOf(), sender, handler)
        commands[name] = spec
        normalizedNames[name.lowercase()] = name
        return this
    }

    fun tabComplete(argName: String, provider: () -> Collection<String>): SymphonyCommand {
        tabProviders[argName.lowercase()] = provider
        return this
    }

    override fun execute(sender: CommandSender, label: String, args: Array<String>): Boolean {
        val subName = args.firstOrNull()
        if (subName.isNullOrBlank()) {
            sendHelp(sender, label)
            return true
        }
        val spec = commands[normalizedNames[subName.lowercase()]]
        if (spec == null) {
            sender.sendMessage(text("commands.framework.unknown", "label" to label))
            return true
        }
        if (!checkSender(sender, spec.sender)) return true
        val commandArgs = args.copyOfRange(1, args.size)
        if (commandArgs.size < spec.requiredArgs) {
            sender.sendMessage(text("commands.framework.missing-arguments", "label" to label))
            return true
        }
        return try {
            spec.handler(CommandContext(sender, label, commandArgs))
            true
        } catch (error: Throwable) {
            sender.sendMessage(text("commands.framework.execution-failed"))
            BlinkLog.error(text("console.command-failed", "label" to label, "command" to spec.name), error)
            true
        }
    }

    override fun tabComplete(sender: CommandSender, alias: String, args: Array<String>): List<String> =
        runCatching {
            val input = if (args.isEmpty()) arrayOf("") else args
            if (input.size == 1) return@runCatching filter(commands.keys, input[0])

            val spec = commands[normalizedNames[input[0].lowercase()]] ?: return@runCatching emptyList()
            val argumentIndex = input.size - 2
            if (argumentIndex !in spec.args.indices) return@runCatching emptyList()

            val argumentName = spec.args[argumentIndex].removePrefix("?").lowercase()
            val candidates = when (argumentName) {
                "player" -> Bukkit.getOnlinePlayers().map { it.name }
                else -> tabProviders[argumentName]?.invoke().orEmpty()
            }
            filter(candidates, input.last())
        }.getOrElse { error ->
            BlinkLog.error(text("console.completion-failed", "alias" to alias), error)
            emptyList()
        }

    private fun checkSender(sender: CommandSender, required: SenderType): Boolean = when (required) {
        SenderType.ALL -> true
        SenderType.PLAYER -> if (sender is Player) true else deny(sender, "commands.framework.only-player")
        SenderType.OP -> if (sender.isOp) true else deny(sender, "commands.framework.only-op")
        SenderType.CONSOLE -> if (sender is ConsoleCommandSender) true else deny(sender, "commands.framework.only-console")
    }

    private fun deny(sender: CommandSender, key: String): Boolean {
        sender.sendMessage(text(key))
        return false
    }

    private fun sendHelp(sender: CommandSender, label: String) {
        sender.sendMessage(text("commands.framework.help-header", "label" to label))
        commands.values.forEach { spec ->
            sender.sendMessage(text(
                "commands.framework.help-entry",
                "label" to label,
                "command" to spec.name,
                "description" to spec.description
            ))
        }
    }

    private fun filter(candidates: Collection<String>, prefix: String): List<String> {
        val normalizedPrefix = prefix.lowercase()
        return candidates.asSequence()
            .distinct()
            .filter { it.lowercase().startsWith(normalizedPrefix) }
            .sorted()
            .toList()
    }

    private fun text(key: String, vararg variables: Pair<String, Any?>): String =
        SymphonyRuntime.language().text(key, *variables)
}

internal object SymphonyCommandRegistrar {
    private val commandMap: CommandMap by lazy {
        val server = Bukkit.getServer()
        val method = generateSequence<Class<*>>(server.javaClass) { it.superclass }
            .flatMap { it.declaredMethods.asSequence() }
            .first { it.name == "getCommandMap" && it.parameterCount == 0 }
            .apply { isAccessible = true }
        method.invoke(server) as CommandMap
    }

    fun register(plugin: JavaPlugin, command: Command) {
        commandMap.register(plugin.description.name, command)
    }
}
