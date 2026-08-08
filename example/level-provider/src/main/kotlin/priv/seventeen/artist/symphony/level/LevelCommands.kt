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

package priv.seventeen.artist.symphony.level

import org.bukkit.entity.Player
import priv.seventeen.artist.blink.BlinkLog
import priv.seventeen.artist.blink.bukkitPlugin
import priv.seventeen.artist.blink.command.CommandContext
import priv.seventeen.artist.blink.lifecycle.Awake
import priv.seventeen.artist.blink.lifecycle.LifeCycle
import priv.seventeen.artist.symphony.level.command.LocalizedCommand
import priv.seventeen.artist.symphony.level.command.LocalizedCommandRegistrar

object LevelCommands {
    private var registration: AutoCloseable? = null

    @JvmStatic
    @Awake(LifeCycle.ENABLE, priority = 50)
    fun register() {
        val command = LocalizedCommand(
            "symlevel",
            listOf("slevel"),
            { key, variables -> LevelRuntime.message(key, *variables) },
            { error -> BlinkLog.error(t("console.command-failed"), error) }
        )
            .command("show", t("commands.description.show"), args = arrayOf("?player"), handler = ::show)
            .command("addexp", t("commands.description.addexp"), permission = "symlevel.admin.addexp", args = arrayOf("player", "amount"), handler = ::addExperience)
            .command("set", t("commands.description.set"), permission = "symlevel.admin.set", args = arrayOf("player", "level", "?experience"), handler = ::setLevel)
            .command("reload", t("commands.description.reload"), permission = "symlevel.admin.reload", handler = ::reload)
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
        val progress = LevelRuntime.progress(target.uniqueId)
        ctx.reply(t(
            "commands.show",
            "player" to target.name,
            "level" to progress.level,
            "experience" to progress.experience,
            "next" to (LevelRuntime.nextExperience(progress.level) ?: t("common.maximum-level"))
        ))
    }

    private fun addExperience(ctx: CommandContext) {
        val target = target(ctx, 0) ?: return
        val amount = ctx.arg(1).toLongOrNull()
        if (amount == null || amount < 0L) return ctx.reply(t("errors.experience-invalid"))
        val change = LevelRuntime.addExperience(target, amount)
        ctx.reply(t(
            "commands.addexp.success",
            "player" to target.name,
            "amount" to amount,
            "level" to change.progress.level,
            "experience" to change.progress.experience,
            "gained" to change.levelsGained,
            "discarded" to change.discardedExperience
        ))
    }

    private fun setLevel(ctx: CommandContext) {
        val target = target(ctx, 0) ?: return
        val level = ctx.arg(1).toIntOrNull()
        val experience = ctx.arg(2).ifBlank { "0" }.toLongOrNull()
        if (level == null || level !in 1..LevelRuntime.maximumLevel()) {
            return ctx.reply(t("errors.level-invalid", "maximum" to LevelRuntime.maximumLevel()))
        }
        if (experience == null || experience < 0L) return ctx.reply(t("errors.experience-invalid"))
        val progress = LevelRuntime.setLevel(target, level, experience)
        ctx.reply(t("commands.set.success", "player" to target.name, "level" to progress.level, "experience" to progress.experience))
    }

    private fun reload(ctx: CommandContext) {
        LevelRuntime.reload().onSuccess { ctx.reply(t("commands.reload.success")) }.onFailure { error ->
            BlinkLog.error(t("console.reload-failed"), error)
            ctx.reply(t("commands.reload.failed"))
        }
    }

    private fun target(ctx: CommandContext, index: Int): Player? {
        if (ctx.arg(index).isBlank()) return ctx.player ?: run { ctx.reply(t("errors.player-required")); null }
        return ctx.argPlayer(index) ?: run { ctx.reply(t("errors.player-offline")); null }
    }

    private fun t(key: String, vararg variables: Pair<String, Any?>): String = LevelRuntime.message(key, *variables)
}
