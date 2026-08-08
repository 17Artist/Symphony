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
import org.bukkit.attribute.Attribute
import org.bukkit.plugin.Plugin
import org.bukkit.scheduler.BukkitTask
import priv.seventeen.artist.symphony.api.attribute.AttributeKey
import priv.seventeen.artist.symphony.api.attribute.AttributeService
import kotlin.math.min

class HealthRegenerationRuntime(
    private val plugin: Plugin,
    private val attributes: AttributeService
) : AutoCloseable {
    private var task: BukkitTask? = null

    fun start() {
        if (task != null) return
        task = Bukkit.getScheduler().runTaskTimer(plugin, Runnable(::tick), 20L, 20L)
    }

    private fun tick() {
        Bukkit.getOnlinePlayers().forEach { player ->
            val regeneration = attributes.value(player, HEALTH_REGEN).coerceAtLeast(0.0)
            if (regeneration <= 0.0 || player.health <= 0.0) return@forEach
            val maximumHealth = player.getAttribute(Attribute.GENERIC_MAX_HEALTH)?.value ?: player.health
            player.health = min(maximumHealth, player.health + regeneration)
        }
    }

    override fun close() {
        task?.cancel()
        task = null
    }

    companion object {
        private val HEALTH_REGEN = AttributeKey.symphony("health_regen")
    }
}
