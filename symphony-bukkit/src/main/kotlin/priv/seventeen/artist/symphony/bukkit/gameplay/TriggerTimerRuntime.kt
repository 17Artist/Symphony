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

package priv.seventeen.artist.symphony.bukkit.gameplay

import org.bukkit.Bukkit
import org.bukkit.plugin.Plugin
import org.bukkit.scheduler.BukkitTask
import priv.seventeen.artist.symphony.bukkit.service.BukkitTriggerService
import priv.seventeen.artist.symphony.engine.trigger.EntityTimerTrigger
import priv.seventeen.artist.symphony.engine.trigger.EntityTriggerContext
import java.util.UUID

class TriggerTimerRuntime(
    private val plugin: Plugin,
    private val triggers: BukkitTriggerService,
    private val bucketTicks: Long
) : AutoCloseable {
    private var task: BukkitTask? = null
    private var tick: Long = 0

    fun start() {
        if (task != null) return
        task = Bukkit.getScheduler().runTaskTimer(plugin, Runnable {
            if (!triggers.hasCallbacks(EntityTimerTrigger)) return@Runnable
            tick += bucketTicks
            val now = System.currentTimeMillis()
            Bukkit.getWorlds().forEach { world ->
                world.livingEntities.forEach { entity ->
                    triggers.dispatch(
                        EntityTimerTrigger,
                        EntityTriggerContext(UUID.randomUUID(), entity, null, now, mapOf("tick" to tick))
                    )
                }
            }
        }, bucketTicks, bucketTicks)
    }

    override fun close() {
        task?.cancel()
        task = null
    }
}
