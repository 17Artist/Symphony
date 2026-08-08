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

import org.bukkit.event.EventPriority
import org.bukkit.event.player.PlayerJoinEvent
import org.bukkit.event.player.PlayerQuitEvent
import priv.seventeen.artist.blink.event.AutoListener

object LevelListeners {
    @JvmStatic
    @AutoListener(priority = EventPriority.LOWEST)
    fun onJoin(event: PlayerJoinEvent) = LevelRuntime.loadPlayer(event.player)

    @JvmStatic
    @AutoListener(priority = EventPriority.MONITOR)
    fun onQuit(event: PlayerQuitEvent) = LevelRuntime.unloadPlayer(event.player)
}
