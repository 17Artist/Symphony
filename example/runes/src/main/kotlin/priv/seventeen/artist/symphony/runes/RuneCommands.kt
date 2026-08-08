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

package priv.seventeen.artist.symphony.runes

import org.bukkit.entity.Player
import priv.seventeen.artist.blink.BlinkLog
import priv.seventeen.artist.blink.bukkitPlugin
import priv.seventeen.artist.blink.command.CommandContext
import priv.seventeen.artist.blink.lifecycle.Awake
import priv.seventeen.artist.blink.lifecycle.LifeCycle
import priv.seventeen.artist.symphony.runes.command.LocalizedCommand
import priv.seventeen.artist.symphony.runes.command.LocalizedCommandRegistrar
import priv.seventeen.artist.symphony.runes.model.RuneActivationState
import priv.seventeen.artist.symphony.runes.model.RuneMutationFailure
import priv.seventeen.artist.symphony.runes.model.RuneMutationResult

object RuneCommands {
    private var registration: AutoCloseable? = null

    @JvmStatic
    @Awake(LifeCycle.ENABLE, priority = 50)
    fun register() {
        val command = LocalizedCommand(
            "symrune",
            listOf("srune"),
            { key, variables -> RuneRuntime.message(key, *variables) },
            { error -> BlinkLog.error(t("console.command-failed"), error) }
        )
            .command("show", t("commands.description.show"), args = arrayOf("?player"), handler = ::show)
            .command("inspect", t("commands.description.inspect"), args = arrayOf("rune"), handler = ::inspect)
            .command("equip", t("commands.description.equip"), args = arrayOf("slot", "rune"), playerOnly = true, handler = ::equip)
            .command("unequip", t("commands.description.unequip"), args = arrayOf("slot"), playerOnly = true, handler = ::unequip)
            .command("grant", t("commands.description.grant"), permission = "symrune.admin.grant", args = arrayOf("player", "rune", "?rank"), handler = ::grant)
            .command("revoke", t("commands.description.revoke"), permission = "symrune.admin.revoke", args = arrayOf("player", "rune"), handler = ::revoke)
            .command("equipfor", t("commands.description.equipfor"), permission = "symrune.admin.equip", args = arrayOf("player", "slot", "rune"), handler = ::equipFor)
            .command("reload", t("commands.description.reload"), permission = "symrune.admin.reload", handler = ::reload)
            .tabComplete("slot") { RuneRuntime.slotIds().sorted() }
            .tabComplete("rune") { RuneRuntime.runeIds().sorted() }
        registration = LocalizedCommandRegistrar.register(bukkitPlugin, command)
    }

    @JvmStatic
    @Awake(LifeCycle.DISABLE, priority = 50)
    fun unregister() {
        registration?.close()
        registration = null
    }

    private fun show(ctx: CommandContext) {
        val target = target(ctx, 0) ?: return
        val statuses = RuneRuntime.statuses(target)
        ctx.reply(t("commands.show.header", "player" to target.name, "count" to statuses.count { it.state == RuneActivationState.ACTIVE }))
        statuses.forEach { status ->
            if (status.state == RuneActivationState.EMPTY) {
                ctx.reply(t("commands.show.empty", "slot" to status.slot.displayName))
            } else {
                ctx.reply(t(
                    "commands.show.entry",
                    "slot" to status.slot.displayName,
                    "rune" to (status.rune?.displayName ?: t("common.unknown-rune")),
                    "rank" to (status.rank ?: 0),
                    "state" to t("states.${status.state.name.lowercase()}"),
                    "level" to (status.requiredLevel ?: 0)
                ))
            }
        }
    }

    private fun inspect(ctx: CommandContext) {
        val rune = RuneRuntime.rune(ctx.arg(0).lowercase()) ?: return ctx.reply(t("errors.rune-not-found"))
        ctx.reply(t(
            "commands.inspect.header",
            "name" to rune.displayName,
            "category" to RuneRuntime.categoryName(rune.category),
            "maximum" to rune.maximumRank
        ))
        rune.description.forEach { ctx.reply(t("commands.inspect.description", "text" to it)) }
        rune.modifiers.forEach { modifier ->
            ctx.reply(t(
                "commands.inspect.modifier",
                "attribute" to RuneRuntime.attributeName(modifier.attribute),
                "operation" to RuneRuntime.operationName(modifier.operation.name.lowercase()),
                "base" to modifier.value.base,
                "per" to modifier.value.perRank
            ))
        }
    }

    private fun equip(ctx: CommandContext) {
        val player = requireNotNull(ctx.player)
        replyMutation(ctx, RuneRuntime.equip(player, ctx.arg(0).lowercase(), ctx.arg(1).lowercase()), "commands.equip.success")
    }

    private fun unequip(ctx: CommandContext) {
        val player = requireNotNull(ctx.player)
        replyMutation(ctx, RuneRuntime.unequip(player, ctx.arg(0).lowercase()), "commands.unequip.success")
    }

    private fun grant(ctx: CommandContext) {
        val target = target(ctx, 0) ?: return
        val runeId = ctx.arg(1).lowercase()
        val rune = RuneRuntime.rune(runeId) ?: return ctx.reply(t("errors.rune-not-found"))
        val rank = ctx.arg(2).ifBlank { "1" }.toIntOrNull()
        if (rank == null || rank !in 1..rune.maximumRank) return ctx.reply(t("errors.rank-invalid", "maximum" to rune.maximumRank))
        when (val result = RuneRuntime.grant(target, runeId, rank)) {
            is RuneMutationResult.Success -> ctx.reply(t("commands.grant.success", "player" to target.name, "rune" to rune.displayName, "rank" to rank))
            is RuneMutationResult.Failure -> replyFailure(ctx, result)
        }
    }

    private fun revoke(ctx: CommandContext) {
        val target = target(ctx, 0) ?: return
        val runeId = ctx.arg(1).lowercase()
        when (val result = RuneRuntime.revoke(target, runeId)) {
            is RuneMutationResult.Success -> ctx.reply(t("commands.revoke.success", "player" to target.name, "rune" to (RuneRuntime.rune(runeId)?.displayName ?: t("common.unknown-rune"))))
            is RuneMutationResult.Failure -> replyFailure(ctx, result)
        }
    }

    private fun equipFor(ctx: CommandContext) {
        val target = target(ctx, 0) ?: return
        when (val result = RuneRuntime.equip(target, ctx.arg(1).lowercase(), ctx.arg(2).lowercase())) {
            is RuneMutationResult.Success -> ctx.reply(t("commands.equipfor.success", "player" to target.name))
            is RuneMutationResult.Failure -> replyFailure(ctx, result)
        }
    }

    private fun reload(ctx: CommandContext) {
        RuneRuntime.reload().onSuccess { ctx.reply(t("commands.reload.success")) }.onFailure { error ->
            BlinkLog.error(t("console.reload-failed"), error)
            ctx.reply(t("commands.reload.failed", "reason" to (error.message ?: t("common.unknown-error"))))
        }
    }

    private fun replyMutation(ctx: CommandContext, result: RuneMutationResult, successKey: String) {
        when (result) {
            is RuneMutationResult.Success -> ctx.reply(t(successKey))
            is RuneMutationResult.Failure -> replyFailure(ctx, result)
        }
    }

    private fun replyFailure(ctx: CommandContext, failure: RuneMutationResult.Failure) {
        val key = when (failure.reason) {
            RuneMutationFailure.SLOT_NOT_FOUND -> "errors.slot-not-found"
            RuneMutationFailure.RUNE_NOT_FOUND -> "errors.rune-not-found"
            RuneMutationFailure.NOT_UNLOCKED -> "errors.not-unlocked"
            RuneMutationFailure.CATEGORY_MISMATCH -> "errors.category-mismatch"
            RuneMutationFailure.ALREADY_EQUIPPED -> "errors.already-equipped"
            RuneMutationFailure.LEVEL_PROVIDER_MISSING -> "errors.level-provider-missing"
            RuneMutationFailure.LEVEL_TOO_LOW -> "errors.level-too-low"
            RuneMutationFailure.SOURCE_REJECTED -> "errors.source-rejected"
        }
        ctx.reply(t(key, "level" to (failure.requiredLevel ?: 0), "reason" to (failure.detail ?: t("common.unknown-error"))))
    }

    private fun target(ctx: CommandContext, index: Int): Player? {
        if (ctx.arg(index).isBlank()) return ctx.player ?: run { ctx.reply(t("errors.player-required")); null }
        return ctx.argPlayer(index) ?: run { ctx.reply(t("errors.player-offline")); null }
    }

    private fun t(key: String, vararg variables: Pair<String, Any?>): String = RuneRuntime.message(key, *variables)
}
