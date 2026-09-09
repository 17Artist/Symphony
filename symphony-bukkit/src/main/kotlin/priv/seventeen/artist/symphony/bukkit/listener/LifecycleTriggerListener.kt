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

package priv.seventeen.artist.symphony.bukkit.listener

import org.bukkit.event.EventPriority
import org.bukkit.event.player.PlayerJoinEvent
import org.bukkit.event.player.PlayerKickEvent
import org.bukkit.event.player.PlayerQuitEvent
import org.bukkit.event.player.PlayerRespawnEvent
import org.bukkit.event.entity.EntityDeathEvent
import org.bukkit.event.server.PluginDisableEvent
import org.bukkit.entity.Player
import priv.seventeen.artist.blink.event.AutoListener
import priv.seventeen.artist.symphony.api.event.LevelChangeEvent
import priv.seventeen.artist.symphony.bukkit.runtime.SymphonyRuntime
import priv.seventeen.artist.symphony.engine.trigger.*
import java.util.UUID

object LifecycleTriggerListener {
    @JvmStatic
    @AutoListener(priority = EventPriority.MONITOR)
    fun onJoin(event: PlayerJoinEvent) {
        SymphonyRuntime.epicFightOrNull()?.onJoin(event.player)
        SymphonyRuntime.equipmentOrNull()?.mark(event.player)
        SymphonyRuntime.environmentOrNull()?.reconcileNow(event.player)
        SymphonyRuntime.levelsOrNull()?.refresh(event.player, "player.join")
        SymphonyRuntime.attributesOrNull()?.recalculate(event.player)
        SymphonyRuntime.healthPersistenceOrNull()?.onJoin(event.player)
        dispatch(PlayerJoinTrigger, event.player)
    }

    @JvmStatic
    @AutoListener(priority = EventPriority.MONITOR)
    fun onQuit(event: PlayerQuitEvent) {
        dispatch(PlayerQuitTrigger, event.player)
        SymphonyRuntime.healthPersistenceOrNull()?.onQuit(event.player)
        cleanup(event.player)
    }

    @JvmStatic
    @AutoListener(priority = EventPriority.MONITOR, ignoreCancelled = true)
    fun onKick(event: PlayerKickEvent) {
        SymphonyRuntime.healthPersistenceOrNull()?.onQuit(event.player)
        cleanup(event.player)
    }

    @JvmStatic
    @AutoListener(priority = EventPriority.MONITOR)
    fun onRespawnLifecycle(event: PlayerRespawnEvent) {
        SymphonyRuntime.epicFightOrNull()?.onJoin(event.player)
        SymphonyRuntime.equipmentOrNull()?.mark(event.player)
        SymphonyRuntime.environmentOrNull()?.reconcileNow(event.player)
        SymphonyRuntime.healthPersistenceOrNull()?.onRespawn(event.player)
        dispatch(PlayerRespawnTrigger, event.player)
    }

    @JvmStatic
    @AutoListener(priority = EventPriority.MONITOR)
    fun onEntityDeath(event: EntityDeathEvent) {
        val player = event.entity as? Player
        if (player == null) SymphonyRuntime.scheduleForgetEntity(event.entity.uniqueId)
        else SymphonyRuntime.healthPersistenceOrNull()?.onDeath(player)
    }

    @JvmStatic
    @AutoListener(priority = EventPriority.MONITOR)
    fun onLevelChange(event: LevelChangeEvent) {
        val player = event.entity as? Player ?: return
        SymphonyRuntime.healthPersistenceOrNull()?.onCharacterChanged(player, event.current)
    }

    @JvmStatic
    @AutoListener(priority = EventPriority.MONITOR)
    fun onPluginDisable(event: PluginDisableEvent) {
        SymphonyRuntime.removeExternalOwner(event.plugin)
    }

    private fun dispatch(trigger: EntityTrigger, player: Player) {
        SymphonyRuntime.triggerOrNull()?.dispatch(
            trigger,
            EntityTriggerContext(UUID.randomUUID(), player, null, System.currentTimeMillis())
        )
    }

    private fun cleanup(player: Player) {
        SymphonyRuntime.forgetEntity(player)
    }
}
