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

package priv.seventeen.artist.symphony.level.command

import org.bukkit.Bukkit
import org.bukkit.command.Command
import org.bukkit.command.CommandMap
import org.bukkit.command.CommandSender
import org.bukkit.entity.Player
import org.bukkit.plugin.java.JavaPlugin
import priv.seventeen.artist.blink.command.CommandContext
import java.util.concurrent.atomic.AtomicBoolean

class LocalizedCommand(
    name: String,
    aliases: List<String>,
    private val text: (String, Array<out Pair<String, Any?>>) -> String,
    private val reportError: (Throwable) -> Unit
) : Command(name, "", "/$name", aliases) {
    private data class Entry(
        val name: String,
        val description: String,
        val permission: String,
        val arguments: Array<String>,
        val playerOnly: Boolean,
        val handler: (CommandContext) -> Unit
    ) {
        val requiredArguments = arguments.count { !it.startsWith('?') }
    }

    private val entries = linkedMapOf<String, Entry>()
    private val canonicalNames = hashMapOf<String, String>()
    private val completions = hashMapOf<String, () -> Collection<String>>()

    fun command(
        name: String,
        description: String,
        permission: String = "",
        args: Array<String> = emptyArray(),
        playerOnly: Boolean = false,
        handler: (CommandContext) -> Unit
    ): LocalizedCommand {
        require(name.isNotBlank())
        require(args.dropWhile { !it.startsWith('?') }.all { it.startsWith('?') }) {
            "命令的可选参数必须放在最后"
        }
        val entry = Entry(name, description, permission, args, playerOnly, handler)
        entries[name] = entry
        canonicalNames[name.lowercase()] = name
        return this
    }

    fun tabComplete(argument: String, provider: () -> Collection<String>): LocalizedCommand {
        completions[argument.lowercase()] = provider
        return this
    }

    override fun execute(sender: CommandSender, label: String, args: Array<String>): Boolean {
        if (args.isEmpty()) return sendHelp(sender, label)
        val entry = entries[canonicalNames[args[0].lowercase()]]
            ?: return reply(sender, "command-system.unknown", "label" to label, "command" to args[0])
        if (entry.playerOnly && sender !is Player) return reply(sender, "command-system.player-only")
        if (entry.permission.isNotEmpty() && !sender.hasPermission(entry.permission)) {
            return reply(sender, "command-system.permission-denied")
        }
        val supplied = args.size - 1
        if (supplied < entry.requiredArguments) {
            return reply(sender, "command-system.missing-arguments", "usage" to usage(label, entry))
        }
        return try {
            entry.handler(CommandContext(sender, label, args.copyOfRange(1, args.size)))
            true
        } catch (error: Throwable) {
            reportError(error)
            reply(sender, "command-system.execution-error")
        }
    }

    override fun tabComplete(sender: CommandSender, alias: String, args: Array<String>): List<String> {
        if (args.isEmpty()) return visibleEntries(sender).map(Entry::name).sorted()
        if (args.size == 1) return filter(visibleEntries(sender).map(Entry::name), args[0])
        val entry = entries[canonicalNames[args[0].lowercase()]] ?: return emptyList()
        if (!visible(sender, entry)) return emptyList()
        val index = args.size - 2
        if (index !in entry.arguments.indices) return emptyList()
        val argument = entry.arguments[index].removePrefix("?").lowercase()
        val candidates = if (argument == "player") Bukkit.getOnlinePlayers().map(Player::getName)
        else completions[argument]?.invoke().orEmpty()
        return filter(candidates, args.last())
    }

    private fun sendHelp(sender: CommandSender, label: String): Boolean {
        sender.sendMessage(t("command-system.help-header", "label" to label))
        visibleEntries(sender).forEach { entry ->
            sender.sendMessage(t(
                "command-system.help-entry",
                "usage" to usage(label, entry),
                "description" to entry.description
            ))
        }
        return true
    }

    private fun usage(label: String, entry: Entry): String = buildString {
        append('/').append(label).append(' ').append(entry.name)
        entry.arguments.forEach { raw ->
            val optional = raw.startsWith('?')
            val name = raw.removePrefix("?")
            val display = t("arguments.$name")
            append(' ').append(if (optional) "[$display]" else "<$display>")
        }
    }

    private fun visibleEntries(sender: CommandSender): List<Entry> = entries.values.filter { visible(sender, it) }
    private fun visible(sender: CommandSender, entry: Entry): Boolean =
        (!entry.playerOnly || sender is Player) && (entry.permission.isEmpty() || sender.hasPermission(entry.permission))

    private fun filter(values: Collection<String>, prefix: String): List<String> {
        val lower = prefix.lowercase()
        return values.asSequence().distinct().filter { it.lowercase().startsWith(lower) }.sorted().toList()
    }

    private fun reply(sender: CommandSender, key: String, vararg variables: Pair<String, Any?>): Boolean {
        sender.sendMessage(t(key, *variables))
        return true
    }

    private fun t(key: String, vararg variables: Pair<String, Any?>): String = text(key, variables)
}

object LocalizedCommandRegistrar {
    fun register(plugin: JavaPlugin, command: LocalizedCommand): AutoCloseable {
        val map = commandMap()
        map.register(plugin.name.lowercase(), command)
        return Registration(map, command)
    }

    private fun commandMap(): CommandMap {
        val server = Bukkit.getServer()
        val method = generateSequence<Class<*>>(server.javaClass) { it.superclass }
            .flatMap { it.declaredMethods.asSequence() }
            .first { it.name == "getCommandMap" && it.parameterCount == 0 }
            .apply { isAccessible = true }
        return method.invoke(server) as CommandMap
    }

    private class Registration(
        private val map: CommandMap,
        private val command: LocalizedCommand
    ) : AutoCloseable {
        private val open = AtomicBoolean(true)
        override fun close() {
            if (!open.compareAndSet(true, false)) return
            command.unregister(map)
            val field = generateSequence<Class<*>>(map.javaClass) { it.superclass }
                .flatMap { it.declaredFields.asSequence() }
                .firstOrNull { it.name == "knownCommands" }
                ?.apply { isAccessible = true }
                ?: return
            @Suppress("UNCHECKED_CAST")
            val known = field.get(map) as? MutableMap<String, Command> ?: return
            known.entries.asSequence()
                .filter { it.value === command }
                .map { it.key }
                .toList()
                .forEach { key -> known.remove(key, command) }
        }
    }
}
