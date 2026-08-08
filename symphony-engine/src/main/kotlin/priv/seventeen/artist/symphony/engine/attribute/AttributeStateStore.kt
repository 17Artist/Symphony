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

package priv.seventeen.artist.symphony.engine.attribute

import priv.seventeen.artist.symphony.api.attribute.AttributeExplain
import priv.seventeen.artist.symphony.api.attribute.AttributeKey
import priv.seventeen.artist.symphony.api.attribute.AttributeModifier
import priv.seventeen.artist.symphony.api.attribute.AttributeSnapshot
import priv.seventeen.artist.symphony.api.attribute.AttributeSourceKey
import priv.seventeen.artist.symphony.api.source.ItemSourceSnapshot
import priv.seventeen.artist.symphony.api.source.SetThresholdChange
import priv.seventeen.artist.symphony.engine.definition.DefinitionRepository
import priv.seventeen.artist.symphony.engine.set.SetResolution
import priv.seventeen.artist.symphony.engine.set.SetResolver
import java.util.Collections
import java.util.UUID
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.atomic.AtomicReference

data class SourceBatch(
    val modifiers: List<AttributeModifier>,
    val item: ItemSourceSnapshot? = null
)

data class EntityAttributeState(
    val revision: Long,
    val sources: Map<AttributeSourceKey, SourceBatch>,
    val setResolution: SetResolution,
    val snapshot: AttributeSnapshot,
    val explanations: Map<AttributeKey, AttributeExplain>
)

data class StateMutationResult(
    val changed: Boolean,
    val cancelled: Boolean,
    val beforeRevision: Long,
    val state: EntityAttributeState,
    val changedAttributes: Set<AttributeKey>,
    val setThresholdChanges: List<SetThresholdChange>
)

interface AttributeStateObserver {
    fun prepare(entityId: UUID, before: EntityAttributeState, candidate: EntityAttributeState): Boolean
    fun committed(entityId: UUID, before: EntityAttributeState, committed: EntityAttributeState)

    object None : AttributeStateObserver {
        override fun prepare(entityId: UUID, before: EntityAttributeState, candidate: EntityAttributeState) = true
        override fun committed(entityId: UUID, before: EntityAttributeState, committed: EntityAttributeState) = Unit
    }
}

class AttributeStateStore(
    private val definitions: DefinitionRepository,
    private val calculator: AttributeCalculator = AttributeCalculator(),
    private val setResolver: SetResolver = SetResolver(),
    private val observer: AttributeStateObserver = AttributeStateObserver.None,
    private val clock: () -> Long = System::currentTimeMillis
) {
    private val states = ConcurrentHashMap<UUID, AtomicReference<EntityAttributeState>>()
    private val lastAccess = ConcurrentHashMap<UUID, Long>()
    private val nextExpiry = ConcurrentHashMap<UUID, Long>()

    fun state(entityId: UUID): EntityAttributeState {
        touch(entityId)
        return states.computeIfAbsent(entityId) { AtomicReference(emptyState(entityId)) }.get()
    }

    /** 读取已经生成的状态，且不会额外创建空缓存项。 */
    fun stateIfPresent(entityId: UUID): EntityAttributeState? {
        val state = states[entityId]?.get() ?: return null
        touch(entityId)
        return state
    }

    fun hasSources(entityId: UUID): Boolean = states[entityId]?.get()?.sources?.isNotEmpty() == true

    fun replace(
        entityId: UUID,
        source: AttributeSourceKey,
        modifiers: List<AttributeModifier>,
        item: ItemSourceSnapshot? = null,
        reason: String = "source:$source"
    ): StateMutationResult {
        touch(entityId)
        validateSource(source, modifiers, item)
        val ref = states.computeIfAbsent(entityId) { AtomicReference(emptyState(entityId)) }
        while (true) {
            val before = ref.get()
            val normalized = SourceBatch(modifiers.toList(), item?.copy(modifiers = modifiers.toList()))
            if (before.sources[source] == normalized) {
                return StateMutationResult(false, false, before.revision, before, emptySet(), emptyList())
            }
            val nextSources = LinkedHashMap(before.sources)
            nextSources[source] = normalized
            val next = rebuild(entityId, before, nextSources, reason)
            if (!observer.prepare(entityId, before, next)) return cancelledMutation(before)
            if (ref.compareAndSet(before, next)) {
                updateNextExpiry(entityId, next)
                observer.committed(entityId, before, next)
                return mutationResult(before, next)
            }
        }
    }

    fun remove(entityId: UUID, source: AttributeSourceKey, reason: String = "remove:$source"): StateMutationResult {
        val ref = states[entityId]
            ?: return unchangedEmpty(entityId)
        touch(entityId)
        while (true) {
            val before = ref.get()
            if (!before.sources.containsKey(source)) {
                return StateMutationResult(false, false, before.revision, before, emptySet(), emptyList())
            }
            val nextSources = LinkedHashMap(before.sources).also { it.remove(source) }
            val next = rebuild(entityId, before, nextSources, reason)
            if (!observer.prepare(entityId, before, next)) return cancelledMutation(before)
            if (ref.compareAndSet(before, next)) {
                updateNextExpiry(entityId, next)
                observer.committed(entityId, before, next)
                return mutationResult(before, next)
            }
        }
    }

    fun replaceSources(
        entityId: UUID,
        removeWhen: (AttributeSourceKey) -> Boolean,
        replacements: Map<AttributeSourceKey, SourceBatch>,
        reason: String
    ): StateMutationResult {
        replacements.forEach { (source, batch) -> validateSource(source, batch.modifiers, batch.item) }
        val existing = states[entityId]
        if (existing == null && replacements.isEmpty()) return unchangedEmpty(entityId)
        touch(entityId)
        val ref = existing ?: states.computeIfAbsent(entityId) { AtomicReference(emptyState(entityId)) }
        while (true) {
            val before = ref.get()
            val nextSources = LinkedHashMap(before.sources.filterKeys { !removeWhen(it) })
            replacements.toSortedMap().forEach { (source, batch) ->
                nextSources[source] = SourceBatch(batch.modifiers.toList(), batch.item?.copy(modifiers = batch.modifiers.toList()))
            }
            if (nextSources == before.sources) {
                return StateMutationResult(false, false, before.revision, before, emptySet(), emptyList())
            }
            val next = rebuild(entityId, before, nextSources, reason)
            if (!observer.prepare(entityId, before, next)) return cancelledMutation(before)
            if (ref.compareAndSet(before, next)) {
                updateNextExpiry(entityId, next)
                observer.committed(entityId, before, next)
                return mutationResult(before, next)
            }
        }
    }

    fun recalculate(entityId: UUID, reason: String): EntityAttributeState {
        touch(entityId)
        val ref = states.computeIfAbsent(entityId) { AtomicReference(emptyState(entityId)) }
        while (true) {
            val before = ref.get()
            val next = rebuild(entityId, before, before.sources, reason)
            if (!observer.prepare(entityId, before, next)) return before
            if (ref.compareAndSet(before, next)) {
                updateNextExpiry(entityId, next)
                observer.committed(entityId, before, next)
                return next
            }
        }
    }

    fun removeEntity(entityId: UUID): EntityAttributeState? {
        lastAccess.remove(entityId)
        nextExpiry.remove(entityId)
        return states.remove(entityId)?.get()
    }
    fun size(): Int = states.size
    fun entityIds(): Set<UUID> = states.keys.toSet()
    fun evictIdle(lastAccessAtOrBefore: Long, retain: (UUID) -> Boolean = { false }): List<UUID> {
        val evicted = mutableListOf<UUID>()
        lastAccess.forEach { (entityId, accessedAt) ->
            if (accessedAt > lastAccessAtOrBefore || retain(entityId)) return@forEach
            if (!lastAccess.remove(entityId, accessedAt)) return@forEach
            states.remove(entityId)?.let {
                nextExpiry.remove(entityId)
                evicted += entityId
            }
        }
        return evicted
    }

    fun pruneExpired(now: Long, retain: (UUID) -> Boolean = { true }): List<UUID> {
        val changed = mutableListOf<UUID>()
        nextExpiry.forEach { (entityId, expiresAt) ->
            if (expiresAt > now || !retain(entityId)) return@forEach
            if (pruneExpired(entityId, now)) changed += entityId
        }
        return changed
    }

    fun clear() {
        states.clear()
        lastAccess.clear()
        nextExpiry.clear()
    }

    private fun touch(entityId: UUID) {
        val now = clock()
        val previous = lastAccess[entityId]
        if (previous == null) {
            lastAccess.putIfAbsent(entityId, now)
        } else if (now - previous >= ACCESS_TOUCH_GRANULARITY_MILLIS) {
            lastAccess.replace(entityId, previous, now)
        }
    }

    private fun rebuild(
        entityId: UUID,
        before: EntityAttributeState,
        rawSources: Map<AttributeSourceKey, SourceBatch>,
        reason: String
    ): EntityAttributeState {
        val compiled = definitions.current()
        val itemSources = rawSources.mapNotNull { (source, batch) -> batch.item?.let { source to it } }.toMap()
        val setResolution = setResolver.resolve(itemSources, compiled.snapshot.sets, before.setResolution.activeThresholds)
        val allSources = LinkedHashMap<AttributeSourceKey, List<AttributeModifier>>()
        rawSources.toSortedMap().forEach { (key, batch) -> allSources[key] = batch.modifiers }
        setResolution.modifierSources.toSortedMap().forEach { (key, modifiers) -> allSources[key] = modifiers }

        val nextRevision = before.revision + 1
        val now = clock()
        val modifiersByAttribute = hashMapOf<AttributeKey, MutableList<Pair<AttributeSourceKey, AttributeModifier>>>()
        allSources.forEach { (source, modifiers) ->
            modifiers.forEach { modifier ->
                if (!modifier.isExpired(now)) {
                    modifiersByAttribute.getOrPut(modifier.attribute, ::arrayListOf).add(source to modifier)
                }
            }
        }
        val resolved = linkedMapOf<String, Double>()
        val explanations = linkedMapOf<AttributeKey, AttributeExplain>()
        compiled.graph.topologicalOrder.forEach { key ->
            val definition = compiled.snapshot.attributes.getValue(key).definition
            val result = calculator.calculate(
                entityId,
                definition,
                modifiersByAttribute[key].orEmpty(),
                resolved,
                nextRevision,
                setOf(reason)
            )
            resolved[key.value] = result.value
            explanations[key] = result.explain
        }
        val valueMap = explanations.mapValues { it.value.finalValue }
        val snapshot = AttributeSnapshot(
            entityId = entityId,
            revision = nextRevision,
            definitionRevision = compiled.snapshot.revision,
            values = Collections.unmodifiableMap(LinkedHashMap(valueMap)),
            dirtyReasons = Collections.unmodifiableSet(linkedSetOf(reason)),
            committedAtMillis = now
        )
        return EntityAttributeState(
            revision = nextRevision,
            sources = Collections.unmodifiableMap(LinkedHashMap(rawSources)),
            setResolution = setResolution,
            snapshot = snapshot,
            explanations = Collections.unmodifiableMap(explanations)
        )
    }

    private fun emptyState(entityId: UUID): EntityAttributeState {
        val now = clock()
        return EntityAttributeState(
            revision = 0,
            sources = emptyMap(),
            setResolution = SetResolution.EMPTY,
            snapshot = AttributeSnapshot(entityId, 0, definitions.current().snapshot.revision, emptyMap(), emptySet(), now),
            explanations = emptyMap()
        )
    }

    private fun validateSource(
        source: AttributeSourceKey,
        modifiers: List<AttributeModifier>,
        item: ItemSourceSnapshot?
    ) {
        require(modifiers.size <= MAX_MODIFIERS_PER_SOURCE) {
            "属性来源 $source 的修改器数量超过上限 $MAX_MODIFIERS_PER_SOURCE"
        }
        val duplicateIds = modifiers.groupingBy { it.id }.eachCount().filterValues { it > 1 }.keys
        require(duplicateIds.isEmpty()) { "属性来源 $source 包含重复的修改器 ID：$duplicateIds" }
        val known = definitions.current().snapshot.attributes.keys
        val unknown = modifiers.map { it.attribute }.filterNot(known::contains).distinct()
        require(unknown.isEmpty()) { "属性来源 $source 引用了未知属性：$unknown" }
        require(item == null || item.source == source) { "物品来源键与来源批次键不一致" }
    }

    private fun pruneExpired(entityId: UUID, now: Long): Boolean {
        val ref = states[entityId] ?: run {
            nextExpiry.remove(entityId)
            return false
        }
        while (true) {
            val before = ref.get()
            val nextSources = linkedMapOf<AttributeSourceKey, SourceBatch>()
            before.sources.forEach { (source, batch) ->
                val modifiers = batch.modifiers.filterNot { it.isExpired(now) }
                val item = batch.item?.copy(modifiers = modifiers)
                if (modifiers.isNotEmpty() || item?.hasContributionsAfterExpiry() == true) {
                    nextSources[source] = SourceBatch(modifiers, item)
                }
            }
            if (nextSources == before.sources) {
                updateNextExpiry(entityId, before)
                return false
            }
            val next = rebuild(entityId, before, nextSources, "modifier.expired")
            if (!observer.prepare(entityId, before, next)) return false
            if (ref.compareAndSet(before, next)) {
                updateNextExpiry(entityId, next)
                observer.committed(entityId, before, next)
                return true
            }
        }
    }

    private fun updateNextExpiry(entityId: UUID, state: EntityAttributeState) {
        val expiresAt = state.sources.values.asSequence().flatMap { it.modifiers.asSequence() }
            .mapNotNull(AttributeModifier::expiresAtMillis).minOrNull()
        if (expiresAt == null) nextExpiry.remove(entityId) else nextExpiry[entityId] = expiresAt
    }

    private fun ItemSourceSnapshot.hasContributionsAfterExpiry(): Boolean =
        modifiers.isNotEmpty() || setPieces.isNotEmpty() || affixes.isNotEmpty() || gems.isNotEmpty() ||
            skills.isNotEmpty() || enhancementLevel > 0

    private fun mutationResult(before: EntityAttributeState, next: EntityAttributeState): StateMutationResult {
        val keys = before.snapshot.values.keys + next.snapshot.values.keys
        val changed = keys.filterTo(linkedSetOf()) { before.snapshot.values[it] != next.snapshot.values[it] }
        return StateMutationResult(
            changed = true,
            cancelled = false,
            beforeRevision = before.revision,
            state = next,
            changedAttributes = changed,
            setThresholdChanges = next.setResolution.thresholdChanges
        )
    }

    private fun cancelledMutation(before: EntityAttributeState) = StateMutationResult(
        changed = false,
        cancelled = true,
        beforeRevision = before.revision,
        state = before,
        changedAttributes = emptySet(),
        setThresholdChanges = emptyList()
    )

    private fun unchangedEmpty(entityId: UUID): StateMutationResult {
        val state = emptyState(entityId)
        return StateMutationResult(
            changed = false,
            cancelled = false,
            beforeRevision = 0,
            state = state,
            changedAttributes = emptySet(),
            setThresholdChanges = emptyList()
        )
    }

    companion object {
        const val MAX_MODIFIERS_PER_SOURCE: Int = 1024
        private const val ACCESS_TOUCH_GRANULARITY_MILLIS: Long = 1_000L
    }
}
