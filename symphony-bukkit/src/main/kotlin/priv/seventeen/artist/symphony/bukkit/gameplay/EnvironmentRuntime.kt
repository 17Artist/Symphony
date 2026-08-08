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
import org.bukkit.entity.LivingEntity
import org.bukkit.entity.Player
import org.bukkit.plugin.Plugin
import org.bukkit.scheduler.BukkitTask
import priv.seventeen.artist.blink.BlinkLog
import priv.seventeen.artist.symphony.api.attribute.AttributeSourceKey
import priv.seventeen.artist.symphony.api.source.SourceUpdateResult
import priv.seventeen.artist.symphony.bukkit.runtime.SymphonyRuntime
import priv.seventeen.artist.symphony.bukkit.script.CallbackOwner
import priv.seventeen.artist.symphony.bukkit.service.BukkitAttributeSourceService
import priv.seventeen.artist.symphony.engine.definition.DefinitionRepository
import priv.seventeen.artist.symphony.engine.trigger.EntityTriggerContext
import java.util.UUID
import java.util.concurrent.ConcurrentHashMap
import kotlin.math.floor

class EnvironmentRuntime(
    private val plugin: Plugin,
    private val definitions: DefinitionRepository,
    private val sources: BukkitAttributeSourceService,
    private val bucketTicks: Long,
    private val enabled: Boolean = true
) : AutoCloseable {
    private val active = ConcurrentHashMap<UUID, Set<String>>()
    private val pending = ConcurrentHashMap.newKeySet<UUID>()
    private var task: BukkitTask? = null

    fun start() {
        if (!enabled) return
        if (task != null) return
        task = Bukkit.getScheduler().runTaskTimer(plugin, Runnable {
            Bukkit.getOnlinePlayers().forEach(::mark)
            flush()
        }, bucketTicks, bucketTicks)
    }

    fun mark(entity: LivingEntity) {
        if (!enabled) return
        pending += entity.uniqueId
    }

    fun callbackVariables(owner: CallbackOwner, context: EntityTriggerContext): Map<String, Any?>? =
        if (owner.id in active[context.self.uniqueId].orEmpty()) mapOf("environmentId" to owner.id) else null

    fun active(entity: LivingEntity): Set<String> = if (enabled) active[entity.uniqueId].orEmpty() else emptySet()

    fun forget(entity: LivingEntity) {
        forget(entity.uniqueId)
        sources.replaceManagedSources(entity, setOf("environment"), emptyMap(), "environment.forget")
    }

    fun forget(entityId: UUID) {
        pending.remove(entityId)
        active.remove(entityId)
    }

    private fun flush() {
        val ids = pending.toList()
        pending.removeAll(ids.toSet())
        ids.forEach { id ->
            val entity = Bukkit.getEntity(id) as? LivingEntity ?: return@forEach
            if (!entity.isValid) return@forEach
            reconcile(entity)
        }
    }

    private fun reconcile(entity: LivingEntity) {
        val matched = definitions.current().snapshot.environments.toSortedMap().filterValues { definition ->
            matches(entity, definition.values["when"])
        }
        val ids = matched.keys.toSet()
        val previous = active[entity.uniqueId].orEmpty()
        if (previous == ids) return
        if (ids.isEmpty()) active.remove(entity.uniqueId) else active[entity.uniqueId] = ids
        val replacements = matched.map { (id, definition) ->
            AttributeSourceKey("environment", id) to compileModifiers(definition.values["modifiers"], "environment:$id")
        }.toMap()
        val result = sources.replaceManagedSources(entity, setOf("environment"), replacements, "environment.reconcile")
        if (result is SourceUpdateResult.Rejected) {
            if (previous.isEmpty()) active.remove(entity.uniqueId) else active[entity.uniqueId] = previous
            val language = SymphonyRuntime.language()
            BlinkLog.error(language.text("console.managed-source-failed", "entity" to entity.uniqueId, "type" to language.text("console.managed-source-types.environment"), "reason" to result.reason), result.cause ?: IllegalStateException(result.reason))
        }
    }

    private fun matches(entity: LivingEntity, raw: Any?): Boolean {
        val whenMap = raw as? Map<*, *> ?: return true
        val location = entity.location
        val worlds = stringSet(whenMap["worlds"])
        if (worlds.isNotEmpty() && location.world?.name !in worlds) return false
        val biomes = stringSet(whenMap["biomes"]).map(String::uppercase)
        if (biomes.isNotEmpty() && location.block.biome.name !in biomes) return false
        val outdoor = whenMap["outdoor"] as? Boolean
        if (outdoor != null) {
            val world = location.world ?: return false
            val isOutdoor = world.getHighestBlockYAt(location.blockX, location.blockZ) <= floor(location.y).toInt() + 1
            if (isOutdoor != outdoor) return false
        }
        val time = whenMap["time"] as? Map<*, *>
        if (time != null) {
            val from = (time["from"] as? Number)?.toLong() ?: 0L
            val to = (time["to"] as? Number)?.toLong() ?: 23_999L
            val current = entity.world.time
            val within = if (from <= to) current in from..to else current >= from || current <= to
            if (!within) return false
        }
        when (whenMap["weather"]?.toString()?.lowercase()) {
            "clear" -> if (entity.world.hasStorm()) return false
            "rain" -> if (!entity.world.hasStorm() || entity.world.isThundering) return false
            "thunder" -> if (!entity.world.isThundering) return false
        }
        return true
    }

    private fun stringSet(raw: Any?): Set<String> = when (raw) {
        is Collection<*> -> raw.mapTo(linkedSetOf()) { it.toString() }
        is String -> setOf(raw)
        else -> emptySet()
    }

    override fun close() {
        task?.cancel()
        task = null
        active.keys.toList().forEach { id ->
            (Bukkit.getEntity(id) as? LivingEntity)?.let {
                sources.replaceManagedSources(it, setOf("environment"), emptyMap(), "environment.disable")
            }
        }
        active.clear()
        pending.clear()
    }
}
