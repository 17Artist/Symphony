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
import org.bukkit.plugin.Plugin
import org.bukkit.scheduler.BukkitTask
import priv.seventeen.artist.blink.BlinkLog
import priv.seventeen.artist.symphony.api.attribute.AttributeSourceKey
import priv.seventeen.artist.symphony.api.source.SourceUpdateResult
import priv.seventeen.artist.symphony.bukkit.runtime.SymphonyRuntime
import priv.seventeen.artist.symphony.bukkit.script.CallbackOwner
import priv.seventeen.artist.symphony.bukkit.service.BukkitAttributeSourceService
import priv.seventeen.artist.symphony.bukkit.service.BukkitTriggerService
import priv.seventeen.artist.symphony.engine.definition.DefinitionRepository
import priv.seventeen.artist.symphony.engine.trigger.EntityTriggerContext
import priv.seventeen.artist.symphony.engine.trigger.StatusTickTrigger
import java.util.UUID
import java.util.concurrent.ConcurrentHashMap

data class StatusSnapshot(
    val id: String,
    val stacks: Int,
    val nextExpiryMillis: Long,
    val nextTickMillis: Long
)

class StatusRuntime(
    private val plugin: Plugin,
    private val definitions: DefinitionRepository,
    private val sources: BukkitAttributeSourceService,
    private val triggers: BukkitTriggerService,
    private val bucketTicks: Long,
    private val enabled: Boolean = true,
    private val clock: () -> Long = System::currentTimeMillis
) : AutoCloseable {
    private data class ActiveStatus(
        val expiries: MutableList<Long>,
        var nextTickMillis: Long
    )

    private val active = ConcurrentHashMap<UUID, ConcurrentHashMap<String, ActiveStatus>>()
    private var task: BukkitTask? = null

    fun start() {
        if (!enabled) return
        if (task == null) task = Bukkit.getScheduler().runTaskTimer(plugin, Runnable(::tick), bucketTicks, bucketTicks)
    }

    fun apply(entity: LivingEntity, rawId: String, stacks: Int, durationOverrideMillis: Long?): Boolean {
        if (!enabled) return false
        check(Bukkit.isPrimaryThread()) { "状态效果修改必须在 Bukkit 主线程执行" }
        require(stacks > 0) { "状态层数必须大于零" }
        val id = namespaced(rawId)
        val definition = definitions.current().snapshot.statuses[id] ?: return false
        val maximum = (definition.values["max-stacks"] as? Number)?.toInt()?.coerceAtLeast(1) ?: 1
        val duration = durationOverrideMillis
            ?: (definition.values["duration-ms"] as? Number)?.toLong()
            ?: throw IllegalArgumentException("缺少必填项 $id.duration-ms")
        require(duration > 0) { "$id 的状态持续时间必须大于零" }
        val tickMillis = (definition.values["tick-ms"] as? Number)?.toLong()?.coerceAtLeast(50L) ?: duration
        val now = clock()
        val statuses = active.computeIfAbsent(entity.uniqueId) { ConcurrentHashMap() }
        val state = statuses.computeIfAbsent(id) { ActiveStatus(mutableListOf(), now + tickMillis) }
        synchronized(state) {
            repeat(stacks) {
                if (state.expiries.size < maximum) state.expiries += now + duration
                else {
                    val earliest = state.expiries.indices.minByOrNull { state.expiries[it] } ?: 0
                    state.expiries[earliest] = now + duration
                }
            }
            state.expiries.sort()
        }
        applySources(entity)
        return true
    }

    fun remove(entity: LivingEntity, rawId: String): Boolean {
        check(Bukkit.isPrimaryThread())
        val statuses = active[entity.uniqueId] ?: return false
        val changed = statuses.remove(namespaced(rawId)) != null
        if (statuses.isEmpty()) active.remove(entity.uniqueId)
        if (changed) applySources(entity)
        return changed
    }

    fun snapshots(entity: LivingEntity): List<StatusSnapshot> = if (!enabled) emptyList() else active[entity.uniqueId].orEmpty()
        .map { (id, state) -> synchronized(state) {
            StatusSnapshot(id, state.expiries.size, state.expiries.minOrNull() ?: 0L, state.nextTickMillis)
        } }
        .sortedBy { it.id }

    fun callbackVariables(owner: CallbackOwner, context: EntityTriggerContext): Map<String, Any?>? {
        val state = active[context.self.uniqueId]?.get(owner.id) ?: return null
        return mapOf("statusId" to owner.id, "stacks" to synchronized(state) { state.expiries.size })
    }

    fun forget(entity: LivingEntity) {
        forget(entity.uniqueId)
        sources.replaceManagedSources(entity, setOf("status"), emptyMap(), "status.forget")
    }

    fun forget(entityId: UUID) { active.remove(entityId) }

    internal fun trackedEntityCount(): Int = active.size

    private fun tick() {
        val now = clock()
        active.forEach { (entityId, statuses) ->
            val entity = Bukkit.getEntity(entityId) as? LivingEntity
            if (entity == null || !entity.isValid) {
                active.remove(entityId)
                return@forEach
            }
            var changed = false
            statuses.forEach statusLoop@{ (id, state) ->
                var fireTick = false
                var stacks = 0
                synchronized(state) {
                    val before = state.expiries.size
                    state.expiries.removeIf { it <= now }
                    changed = changed || before != state.expiries.size
                    if (state.expiries.isNotEmpty()) {
                        val definition = definitions.current().snapshot.statuses[id]
                        if (definition != null) {
                            val tickMillis = (definition.values["tick-ms"] as? Number)?.toLong()?.coerceAtLeast(50L) ?: Long.MAX_VALUE
                            if (now >= state.nextTickMillis) {
                                state.nextTickMillis = now + tickMillis
                                fireTick = true
                                stacks = state.expiries.size
                            }
                        }
                    }
                }
                if (synchronized(state) { state.expiries.isEmpty() }) {
                    statuses.remove(id, state)
                    changed = true
                    return@statusLoop
                }
                if (definitions.current().snapshot.statuses[id] == null) {
                    statuses.remove(id, state)
                    changed = true
                    return@statusLoop
                }
                if (fireTick) {
                    triggers.dispatch(
                        StatusTickTrigger,
                        EntityTriggerContext(
                            UUID.randomUUID(), entity, entity, now,
                            mapOf("statusId" to id, "stacks" to stacks)
                        )
                    )
                }
            }
            if (statuses.isEmpty()) active.remove(entityId)
            if (changed) applySources(entity)
        }
    }

    private fun applySources(entity: LivingEntity) {
        val replacements = active[entity.uniqueId].orEmpty().toSortedMap().mapNotNull { (id, state) ->
            val definition = definitions.current().snapshot.statuses[id] ?: return@mapNotNull null
            val perStack = definition.values["per-stack"] as? Map<*, *> ?: return@mapNotNull null
            val stacks = synchronized(state) { state.expiries.size }
            val modifiers = compileModifiers(perStack["modifiers"], "status:$id", stacks.toDouble())
            AttributeSourceKey("status", id) to modifiers
        }.toMap()
        val result = sources.replaceManagedSources(entity, setOf("status"), replacements, "status.reconcile")
        if (result is SourceUpdateResult.Rejected) {
            val language = SymphonyRuntime.language()
            BlinkLog.error(language.text("console.managed-source-failed", "entity" to entity.uniqueId, "type" to language.text("console.managed-source-types.status"), "reason" to result.reason), result.cause ?: IllegalStateException(result.reason))
        }
    }

    override fun close() {
        task?.cancel()
        task = null
        active.keys.toList().forEach { id ->
            (Bukkit.getEntity(id) as? LivingEntity)?.let { sources.replaceManagedSources(it, setOf("status"), emptyMap(), "status.disable") }
        }
        active.clear()
    }
}
