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
import priv.seventeen.artist.symphony.api.attribute.AttributeKey
import priv.seventeen.artist.symphony.api.attribute.AttributeModifier
import priv.seventeen.artist.symphony.api.attribute.AttributeService
import priv.seventeen.artist.symphony.api.attribute.AttributeSourceKey
import priv.seventeen.artist.symphony.api.source.SourceUpdateResult
import priv.seventeen.artist.symphony.bukkit.runtime.SymphonyRuntime
import priv.seventeen.artist.symphony.bukkit.script.CallbackOwner
import priv.seventeen.artist.symphony.bukkit.script.CallbackOwnerKind
import priv.seventeen.artist.symphony.bukkit.service.BukkitAttributeSourceService
import priv.seventeen.artist.symphony.engine.attribute.AttributeStateStore
import priv.seventeen.artist.symphony.engine.definition.DefinitionRepository
import priv.seventeen.artist.symphony.engine.trigger.EntityTriggerContext
import java.util.UUID
import java.util.concurrent.ConcurrentHashMap

class PassiveRuleRuntime(
    private val plugin: Plugin,
    private val definitions: DefinitionRepository,
    private val store: AttributeStateStore,
    private val attributes: AttributeService,
    private val sources: BukkitAttributeSourceService,
    private val resonancesEnabled: Boolean = true,
    private val talentsEnabled: Boolean = true
) : AutoCloseable {
    private val pending = ConcurrentHashMap.newKeySet<UUID>()
    private val active = ConcurrentHashMap<UUID, Set<String>>()
    private var task: BukkitTask? = null

    fun mark(entity: LivingEntity) {
        if (!resonancesEnabled && !talentsEnabled) return
        pending += entity.uniqueId
        if (task == null) task = Bukkit.getScheduler().runTask(plugin, Runnable(::flush))
    }

    fun active(entity: LivingEntity): Set<String> =
        if (!resonancesEnabled && !talentsEnabled) emptySet() else active[entity.uniqueId].orEmpty()

    fun callbackVariables(owner: CallbackOwner, context: EntityTriggerContext): Map<String, Any?>? =
        if (owner.id in active[context.self.uniqueId].orEmpty()) {
            mapOf("passiveId" to owner.id, "passiveType" to owner.kind.name.lowercase())
        } else null

    fun forget(entity: LivingEntity) {
        forget(entity.uniqueId)
        sources.replaceManagedSources(entity, setOf("resonance", "talent"), emptyMap(), "passive.forget")
    }

    fun forget(entityId: UUID) {
        pending.remove(entityId)
        active.remove(entityId)
    }

    private fun flush() {
        task = null
        val ids = pending.toList()
        pending.removeAll(ids.toSet())
        ids.forEach { id ->
            val entity = Bukkit.getEntity(id) as? LivingEntity ?: return@forEach
            if (entity.isValid) reconcile(entity)
        }
        if (pending.isNotEmpty() && task == null) task = Bukkit.getScheduler().runTask(plugin, Runnable(::flush))
    }

    private fun reconcile(entity: LivingEntity) {
        val matchedResonances = if (!resonancesEnabled) emptyMap() else definitions.current().snapshot.resonances.toSortedMap().filterValues {
            matchesResonance(entity, it.values["condition"])
        }
        val matchedTalents = if (!talentsEnabled) emptyMap() else definitions.current().snapshot.talents.toSortedMap().filterValues {
            matchesGate(entity, it.values["gate"])
        }
        val nextActive = (matchedResonances.keys + matchedTalents.keys).toSet()
        val replacements = linkedMapOf<AttributeSourceKey, List<AttributeModifier>>()
        matchedResonances.forEach { (id, definition) ->
            replacements[AttributeSourceKey("resonance", id)] = compileModifiers(definition.values["modifiers"], "resonance:$id")
        }
        matchedTalents.forEach { (id, definition) ->
            replacements[AttributeSourceKey("talent", id)] = compileModifiers(definition.values["modifiers"], "talent:$id")
        }
        val previous = active[entity.uniqueId]
        val result = sources.replaceManagedSources(
            entity,
            setOf("resonance", "talent"),
            replacements,
            "passive.reconcile"
        )
        if (result is SourceUpdateResult.Rejected) {
            val language = SymphonyRuntime.language()
            BlinkLog.error(language.text("console.managed-source-failed", "entity" to entity.uniqueId, "type" to language.text("console.managed-source-types.passive"), "reason" to result.reason), result.cause ?: IllegalStateException(result.reason))
            return
        }
        if (nextActive.isEmpty()) active.remove(entity.uniqueId) else active[entity.uniqueId] = nextActive
    }

    private fun matchesResonance(entity: LivingEntity, raw: Any?): Boolean {
        val condition = raw as? Map<*, *> ?: return false
        return when (condition["type"]?.toString()) {
            "affix_tag_count" -> {
                val tag = condition["tag"]?.toString() ?: return false
                val required = (condition["count"] as? Number)?.toInt() ?: return false
                val count = store.stateIfPresent(entity.uniqueId)?.sources?.values.orEmpty().asSequence()
                    .mapNotNull { it.item }
                    .flatMap { it.affixes.asSequence() }
                    .count { tag in it.tags }
                count >= required
            }
            "set_count" -> {
                val setId = namespaced(condition["set"]?.toString() ?: return false)
                val required = (condition["count"] as? Number)?.toInt() ?: return false
                (store.stateIfPresent(entity.uniqueId)?.setResolution?.counts?.get(setId) ?: 0) >= required
            }
            "attribute" -> compareAttribute(entity, condition)
            else -> false
        }
    }

    private fun matchesGate(entity: LivingEntity, raw: Any?): Boolean {
        val gate = raw as? Map<*, *> ?: return false
        val all = gate["all"] as? List<*>
        if (all != null && !all.all { compareAttribute(entity, it as? Map<*, *> ?: return@all false) }) return false
        val any = gate["any"] as? List<*>
        if (any != null && !any.any { compareAttribute(entity, it as? Map<*, *> ?: return@any false) }) return false
        val none = gate["none"] as? List<*>
        if (none != null && none.any { compareAttribute(entity, it as? Map<*, *> ?: return@any false) }) return false
        return all != null || any != null || none != null
    }

    private fun compareAttribute(entity: LivingEntity, condition: Map<*, *>): Boolean {
        val rawKey = condition["attribute"]?.toString() ?: return false
        val expected = (condition["value"] as? Number)?.toDouble() ?: return false
        val key = AttributeKey(namespaced(rawKey))
        val actual = store.stateIfPresent(entity.uniqueId)?.snapshot?.values?.get(key)
            ?: definitions.current().snapshot.attributes[key]?.definition?.base
            ?: attributes.value(entity, key)
        return when (condition["operator"]?.toString() ?: ">=") {
            ">" -> actual > expected
            ">=" -> actual >= expected
            "<" -> actual < expected
            "<=" -> actual <= expected
            "==", "=" -> actual == expected
            "!=", "<>" -> actual != expected
            else -> false
        }
    }

    override fun close() {
        task?.cancel()
        task = null
        active.keys.toList().forEach { id ->
            (Bukkit.getEntity(id) as? LivingEntity)?.let {
                sources.replaceManagedSources(it, setOf("resonance", "talent"), emptyMap(), "passive.disable")
            }
        }
        pending.clear()
        active.clear()
    }
}
