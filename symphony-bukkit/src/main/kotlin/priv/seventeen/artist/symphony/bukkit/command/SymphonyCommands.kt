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
import org.bukkit.command.CommandSender
import org.bukkit.entity.LivingEntity
import org.bukkit.entity.Player
import priv.seventeen.artist.blink.BlinkLog
import priv.seventeen.artist.blink.bukkitPlugin
import priv.seventeen.artist.blink.command.CommandContext
import priv.seventeen.artist.blink.command.SenderType
import priv.seventeen.artist.blink.lifecycle.Awake
import priv.seventeen.artist.blink.lifecycle.LifeCycle
import priv.seventeen.artist.symphony.api.attribute.AttributeKey
import priv.seventeen.artist.symphony.api.attribute.AttributeModifier
import priv.seventeen.artist.symphony.api.attribute.AttributeOperation
import priv.seventeen.artist.symphony.api.attribute.AttributeSourceKey
import priv.seventeen.artist.symphony.api.damage.DamageChannelAmount
import priv.seventeen.artist.symphony.api.damage.DamageRequest
import priv.seventeen.artist.symphony.api.source.SourceUpdateResult
import priv.seventeen.artist.symphony.bukkit.gui.GuiScreenId
import priv.seventeen.artist.symphony.bukkit.runtime.SymphonyRuntime

object SymphonyCommands {
    @JvmStatic
    @Awake(LifeCycle.ENABLE, priority = 50)
    fun register() {
        val command = SymphonyCommand("sym", "symphony")
            .command("validate", t("commands.help.validate"), sender = SenderType.ALL, handler = ::validate)
            .command("reload", t("commands.help.reload"), sender = SenderType.ALL, handler = ::reload)
            .command(
                "attr", t("commands.help.attribute"),
                args = arrayOf("attr_action", "player", "attribute", "?value", "?operation", "?source"),
                sender = SenderType.ALL, handler = ::attribute
            )
            .command(
                "player", t("commands.help.player"),
                args = arrayOf("player_action", "player"),
                sender = SenderType.ALL, handler = ::player
            )
            .command(
                "item", t("commands.help.item"),
                args = arrayOf("item_action", "?player"),
                sender = SenderType.ALL, handler = ::item
            )
            .command(
                "callback", t("commands.help.callback"),
                args = arrayOf("callback_action", "?callback"),
                sender = SenderType.ALL, handler = ::callback
            )
            .command(
                "damage", t("commands.help.damage"),
                args = arrayOf("damage_action", "player", "amount", "?channel"),
                sender = SenderType.ALL, handler = ::damage
            )
            .command(
                "debug", t("commands.help.debug"),
                args = arrayOf("debug_action", "?player"),
                sender = SenderType.ALL, handler = ::debug
            )
            .command(
                "menu", t("commands.help.menu"),
                args = arrayOf("?screen", "?menu_value"),
                sender = SenderType.PLAYER, handler = ::menu
            )
            .tabComplete("attr_action") { listOf("get", "set", "remove", "explain") }
            .tabComplete("attribute") { SymphonyRuntime.apiOrNull()?.definitions?.attributes()?.keys?.map { it.value }.orEmpty() }
            .tabComplete("operation") { listOf("add", "multiply_base", "multiply_total") }
            .tabComplete("player_action") { listOf("level", "refresh") }
            .tabComplete("item_action") { listOf("inspect") }
            .tabComplete("callback_action") { listOf("list", "enable", "disable", "failures") }
            .tabComplete("damage_action") { listOf("test") }
            .tabComplete("debug_action") { listOf("entity", "transaction", "cache") }
            .tabComplete("screen") { GuiScreenId.values().map { it.alias } }
            .tabComplete("menu_value") {
                val attributes = SymphonyRuntime.apiOrNull()?.definitions?.attributes()?.keys?.map { it.value }.orEmpty()
                (attributes + Bukkit.getOnlinePlayers().map { it.name }).distinct().sorted()
            }
        SymphonyCommandRegistrar.register(bukkitPlugin, command)
    }

    private fun validate(ctx: CommandContext) {
        if (!permission(ctx.sender, "symphony.command.validate")) return
        val result = SymphonyRuntime.validateDefinitions()
        val snapshot = result.snapshot
        if (result.report.valid && snapshot != null) ctx.reply(t("commands.validate.success", "count" to snapshot.attributes.size))
        else {
            ctx.reply(t("commands.validate.failed", "count" to result.report.errors.size))
            result.report.errors.take(20).forEach {
                ctx.reply(t("commands.validate.issue", "source" to (it.source ?: t("common.none")), "path" to it.path, "message" to it.message))
            }
        }
    }

    private fun reload(ctx: CommandContext) {
        if (!permission(ctx.sender, "symphony.command.reload")) return
        val result = SymphonyRuntime.reload()
        if (result.report.valid && result.snapshot != null) ctx.reply(t("commands.reload.success"))
        else ctx.reply(t("commands.reload.failed"))
    }

    private fun attribute(ctx: CommandContext) {
        val action = ctx.arg(0).lowercase()
        if (!permission(ctx.sender, "symphony.command.attr.$action")) return
        val target = ctx.argPlayer(1) ?: return ctx.reply(t("commands.player-offline"))
        val key = runCatching { AttributeKey(normalizeId(ctx.arg(2))) }.getOrElse {
            return ctx.reply(t("commands.attribute.missing"))
        }
        val api = SymphonyRuntime.api()
        when (action) {
            "get" -> {
                val name = api.definitions.attribute(key)?.name ?: return ctx.reply(t("commands.attribute.missing"))
                ctx.reply(t("commands.attribute.value", "player" to target.name, "name" to name, "value" to api.attributes.value(target, key)))
            }
            "explain" -> {
                val explain = api.attributes.explain(target, key) ?: return ctx.reply(t("commands.attribute.missing"))
                val name = api.definitions.attribute(key)?.name ?: t("common.unknown-name")
                ctx.reply(t("commands.attribute.explain", "name" to name, "base" to explain.base, "aggregated" to explain.standardValue, "calculated" to explain.calculatedValue, "final" to explain.formatted))
                explain.contributions.forEach {
                    ctx.reply(t(
                        "commands.attribute.contribution",
                        "source" to sourceName(it.source),
                        "operation" to t("operations.${it.modifier.operation.name.lowercase()}"),
                        "value" to it.modifier.value,
                        "before" to it.valueBefore,
                        "after" to it.valueAfter
                    ))
                }
            }
            "set" -> {
                val value = ctx.arg(3).toDoubleOrNull()?.takeIf(Double::isFinite) ?: return ctx.reply(t("commands.number-invalid"))
                val operation = runCatching { AttributeOperation.parse(ctx.arg(4).ifBlank { "add" }) }.getOrElse {
                    return ctx.reply(t("commands.action-invalid"))
                }
                val source = commandSource(ctx, target, key)
                val result = api.sources.replaceSource(
                    target, source,
                    listOf(AttributeModifier("command", key, operation, value, description = "由 ${ctx.sender.name} 通过命令写入"))
                )
                replySourceResult(ctx, result)
            }
            "remove" -> replySourceResult(ctx, api.sources.removeSource(target, commandSource(ctx, target, key, 3)))
            else -> ctx.reply(t("commands.action-invalid"))
        }
    }

    private fun player(ctx: CommandContext) {
        val action = ctx.arg(0).lowercase()
        if (!permission(ctx.sender, "symphony.command.player.$action")) return
        val target = ctx.argPlayer(1) ?: return ctx.reply(t("commands.player-offline"))
        val levels = SymphonyRuntime.api().levels
        when (action) {
            "level", "refresh" -> {
                val snapshot = (if (action == "refresh") levels.refresh(target, "command:${ctx.sender.name}") else levels.snapshot(target))
                    ?: return ctx.reply(t("commands.level.unavailable", "player" to target.name))
                ctx.reply(t(
                    "commands.level.snapshot",
                    "player" to target.name,
                    "provider" to snapshot.providerName,
                    "level" to snapshot.level,
                    "experience" to (snapshot.experience ?: t("common.unavailable")),
                    "character" to (snapshot.characterName ?: t("common.none"))
                ))
            }
            else -> ctx.reply(t("commands.action-invalid"))
        }
    }

    private fun item(ctx: CommandContext) {
        val action = ctx.arg(0).lowercase()
        if (!permission(ctx.sender, "symphony.command.item.$action")) return
        val target = (if (ctx.arg(1).isBlank()) ctx.player else ctx.argPlayer(1))
            ?: return ctx.reply(t("commands.player-required"))
        if (action != "inspect") {
            ctx.reply(t("commands.item.use-gui"))
            return
        }
        val inspection = SymphonyRuntime.api().items.inspect(target.inventory.itemInMainHand)
        if (!inspection.isSymphonyItem || inspection.diagnostics.isNotEmpty()) return ctx.reply(t("item.invalid"))
        ctx.reply(t(
            "commands.item.summary",
            "attributes" to inspection.attributes.size,
            "sets" to inspection.setIds.size,
            "affixes" to inspection.affixes.size,
            "gems" to inspection.gems.size,
            "skills" to inspection.skills.size,
            "enhancement" to inspection.enhancementLevel
        ))
    }

    private fun callback(ctx: CommandContext) {
        val action = ctx.arg(0).lowercase()
        if (!permission(ctx.sender, "symphony.command.callback.$action")) return
        val triggers = SymphonyRuntime.triggerOrNull() ?: return ctx.reply(t("commands.service-unavailable"))
        when (action) {
            "list" -> triggers.registeredTriggers().forEach {
                ctx.reply(t("commands.callback.entry", "id" to it.id, "phase" to enumText("trigger-phases", it.phase.name), "policy" to enumText("result-policies", it.resultPolicy.name)))
            }
            "failures" -> triggers.failures().forEach {
                ctx.reply(t("commands.callback.failure", "id" to it.callbackId, "enabled" to booleanText(it.enabled), "failures" to it.recentFailures, "last" to (it.lastMessage ?: t("common.none"))))
            }
            "enable" -> ctx.reply(t(if (triggers.enableCallback(ctx.arg(1))) "commands.callback.enabled" else "commands.callback.already-enabled"))
            "disable" -> ctx.reply(t(if (triggers.disableCallback(ctx.arg(1))) "commands.callback.disabled" else "commands.callback.already-disabled"))
            else -> ctx.reply(t("commands.action-invalid"))
        }
    }

    private fun damage(ctx: CommandContext) {
        if (ctx.arg(0).lowercase() != "test") return ctx.reply(t("commands.action-invalid"))
        if (!permission(ctx.sender, "symphony.command.damage.test")) return
        val target = ctx.argPlayer(1) ?: return ctx.reply(t("commands.player-offline"))
        val amount = ctx.argDouble(2, -1.0)
        if (!amount.isFinite() || amount < 0.0) return ctx.reply(t("commands.number-invalid"))
        val channel = ctx.arg(3).ifBlank { "physical" }
        val result = SymphonyRuntime.api().damage.damage(
            DamageRequest(ctx.sender as? LivingEntity, target, listOf(DamageChannelAmount(channel, amount)), "command:test")
        )
        ctx.reply(t("commands.damage.result", "state" to enumText("damage-states", result.state.name), "damage" to result.finalDamage))
    }

    private fun debug(ctx: CommandContext) {
        val action = ctx.arg(0).lowercase()
        if (!permission(ctx.sender, "symphony.command.debug.$action")) return
        val target = if (ctx.arg(1).isBlank()) ctx.player else ctx.argPlayer(1)
        when (action) {
            "entity" -> {
                target ?: return ctx.reply(t("commands.player-required"))
                val state = SymphonyRuntime.storeOrNull()?.state(target.uniqueId) ?: return ctx.reply(t("commands.service-unavailable"))
                ctx.reply(t("commands.debug.entity", "uuid" to target.uniqueId, "revision" to state.revision, "sources" to state.sources.size))
                ctx.reply(t("commands.debug.sets", "sets" to state.setResolution.counts, "active" to state.setResolution.activeThresholds))
            }
            "transaction" -> {
                target ?: return ctx.reply(t("commands.player-required"))
                SymphonyRuntime.api().damage.recentTransactions(target).forEach {
                    ctx.reply(t("commands.debug.transaction", "id" to it.transactionId, "state" to enumText("damage-states", it.state.name), "damage" to it.finalDamage))
                }
            }
            "cache" -> ctx.reply(t("commands.debug.cache", "entities" to (SymphonyRuntime.storeOrNull()?.size() ?: 0), "sessions" to (SymphonyRuntime.guiOrNull()?.sessionCount() ?: 0)))
            else -> ctx.reply(t("commands.action-invalid"))
        }
    }

    private fun menu(ctx: CommandContext) {
        val viewer = ctx.player ?: return
        val rawScreen = ctx.arg(0)
        if (rawScreen.isBlank()) {
            ctx.reply(t("commands.menu.header"))
            GuiScreenId.values().forEach { screen ->
                ctx.reply(t(
                    "commands.menu.entry",
                    "command" to menuUsage(screen),
                    "description" to t("commands.menu.screens.${screen.alias}")
                ))
            }
            return
        }
        val screen = GuiScreenId.fromAlias(rawScreen) ?: return ctx.reply(t("commands.menu.unknown"))
        if (!permission(ctx.sender, "symphony.command.menu.${screen.alias}")) return
        val value = ctx.arg(1)
        val target = when (screen) {
            GuiScreenId.ATTRIBUTE_BROWSER, GuiScreenId.ADMIN_DIAGNOSTICS -> {
                if (value.isBlank()) viewer else ctx.argPlayer(1) ?: return ctx.reply(t("commands.player-offline"))
            }
            else -> viewer
        }
        if (target != viewer && !viewer.hasPermission("symphony.gui.others")) return ctx.reply(t("commands.menu.others-denied"))
        val filter = if (screen == GuiScreenId.ATTRIBUTE_EXPLAIN) {
            if (value.isBlank()) return ctx.reply(t("commands.menu.detail-required"))
            normalizeId(value)
        } else {
            if (value.isNotBlank() && screen !in setOf(GuiScreenId.ATTRIBUTE_BROWSER, GuiScreenId.ADMIN_DIAGNOSTICS)) {
                return ctx.reply(t("commands.menu.no-argument", "command" to menuUsage(screen)))
            }
            ""
        }
        val gui = SymphonyRuntime.guiOrNull() ?: return ctx.reply(t("commands.service-unavailable"))
        gui.open(viewer, screen, target, filter)
    }

    private fun menuUsage(screen: GuiScreenId): String = when (screen) {
        GuiScreenId.ATTRIBUTE_BROWSER -> t("commands.menu.usages.attributes")
        GuiScreenId.ATTRIBUTE_EXPLAIN -> t("commands.menu.usages.detail")
        GuiScreenId.ADMIN_DIAGNOSTICS -> t("commands.menu.usages.admin")
        else -> t("commands.menu.usages.default", "screen" to screen.alias)
    }

    private fun commandSource(ctx: CommandContext, target: Player, key: AttributeKey, argumentIndex: Int = 5): AttributeSourceKey {
        val value = ctx.arg(argumentIndex).ifBlank { "${ctx.sender.name}:${target.uniqueId}:${key.value}" }
        return AttributeSourceKey("command", value)
    }

    private fun replySourceResult(ctx: CommandContext, result: SourceUpdateResult) {
        when (result) {
            is SourceUpdateResult.Applied -> ctx.reply(t("commands.attribute.updated"))
            is SourceUpdateResult.Unchanged -> ctx.reply(t("commands.attribute.unchanged"))
            is SourceUpdateResult.Rejected -> {
                BlinkLog.error(t("console.source-update-rejected", "source" to result.source, "reason" to result.reason), result.cause ?: IllegalArgumentException(result.reason))
                ctx.reply(t("commands.attribute.update-failed"))
            }
        }
    }

    private fun permission(sender: CommandSender, permission: String): Boolean {
        if (sender.hasPermission(permission)) return true
        sender.sendMessage(t("permission.denied"))
        return false
    }

    private fun normalizeId(value: String): String = if (':' in value) value else "symphony:$value"

    private fun sourceName(source: AttributeSourceKey): String = when (source.namespace) {
        "equipment" -> SymphonyRuntime.language().optional("sources.equipment.${source.value}", t("sources.external"))
        "set" -> t("sources.set")
        "status" -> t("sources.status")
        "environment" -> t("sources.environment")
        "provider" -> t("sources.provider")
        else -> t("sources.external")
    }

    private fun t(key: String, vararg variables: Pair<String, Any?>): String =
        SymphonyRuntime.language().text(key, *variables)

    private fun enumText(section: String, value: String): String =
        SymphonyRuntime.language().optional("$section.${value.lowercase()}", t("common.unknown-effect"))

    private fun booleanText(value: Boolean): String = t("booleans.${if (value) "yes" else "no"}")
}
