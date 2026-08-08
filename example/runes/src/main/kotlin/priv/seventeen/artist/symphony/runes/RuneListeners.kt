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

import org.bukkit.event.EventPriority
import org.bukkit.event.player.PlayerJoinEvent
import org.bukkit.event.player.PlayerQuitEvent
import priv.seventeen.artist.blink.event.AutoListener
import priv.seventeen.artist.symphony.api.event.LevelChangeEvent

object RuneListeners {
    @JvmStatic
    @AutoListener(priority = EventPriority.LOW)
    fun onJoin(event: PlayerJoinEvent) = RuneRuntime.loadPlayer(event.player)

    @JvmStatic
    @AutoListener(priority = EventPriority.MONITOR)
    fun onQuit(event: PlayerQuitEvent) = RuneRuntime.unloadPlayer(event.player)

    @JvmStatic
    @AutoListener(priority = EventPriority.MONITOR)
    fun onLevelChange(event: LevelChangeEvent) = RuneRuntime.onLevelChange(event)
}
