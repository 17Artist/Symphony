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

package priv.seventeen.artist.symphony.bukkit.service

import org.bukkit.Bukkit
import org.bukkit.plugin.Plugin
import org.bukkit.scheduler.BukkitTask
import priv.seventeen.artist.symphony.engine.attribute.AttributeStateStore
import java.util.UUID

class AttributeCacheEvictionRuntime(
    private val plugin: Plugin,
    private val store: AttributeStateStore,
    idleSeconds: Long,
    private val clock: () -> Long = System::currentTimeMillis,
    private val onMaintenance: (List<UUID>, Long) -> Unit = { _, _ -> }
) : AutoCloseable {
    private val idleMillis = Math.multiplyExact(idleSeconds, 1000L)
    private val intervalTicks = (idleSeconds * 20L).coerceIn(20L, 20L * 60L)
    private var task: BukkitTask? = null

    fun start() {
        if (task != null) return
        task = Bukkit.getScheduler().runTaskTimer(plugin, Runnable(::evict), intervalTicks, intervalTicks)
    }

    private fun evict() {
        val now = clock()
        val cutoff = now - idleMillis
        store.pruneExpired(now) { entityId -> Bukkit.getEntity(entityId)?.isValid == true }
        val evicted = store.evictIdle(cutoff) { entityId ->
            store.hasSources(entityId) && Bukkit.getEntity(entityId)?.isValid == true
        }
        onMaintenance(evicted, now)
    }

    override fun close() {
        task?.cancel()
        task = null
    }
}
