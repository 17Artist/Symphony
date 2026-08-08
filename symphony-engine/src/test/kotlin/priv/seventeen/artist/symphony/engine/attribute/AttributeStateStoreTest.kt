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

import java.util.UUID
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue
import priv.seventeen.artist.symphony.api.attribute.AttributeBounds
import priv.seventeen.artist.symphony.api.attribute.AttributeDefinition
import priv.seventeen.artist.symphony.api.attribute.AttributeKey
import priv.seventeen.artist.symphony.api.attribute.AttributeModifier
import priv.seventeen.artist.symphony.api.attribute.AttributeOperation
import priv.seventeen.artist.symphony.api.attribute.AttributeSourceKey
import priv.seventeen.artist.symphony.engine.definition.CompiledAttributeDefinition
import priv.seventeen.artist.symphony.engine.definition.DefinitionRepository
import priv.seventeen.artist.symphony.engine.definition.DefinitionSnapshot

class AttributeStateStoreTest {
    @Test
    fun `cancelled prepare is distinguishable from unchanged and commits nothing`() {
        val key = AttributeKey.symphony("health")
        val definition = AttributeDefinition(key, "Health", "", "test", 10.0, AttributeBounds(0.0, 100.0))
        val repository = DefinitionRepository(DefinitionSnapshot.empty().copy(
            attributes = mapOf(key to CompiledAttributeDefinition(definition, emptyList()))
        ))
        var cancel = true
        var committed = 0
        val observer = object : AttributeStateObserver {
            override fun prepare(entityId: UUID, before: EntityAttributeState, candidate: EntityAttributeState) = !cancel
            override fun committed(entityId: UUID, before: EntityAttributeState, committedState: EntityAttributeState) { committed++ }
        }
        val store = AttributeStateStore(repository, observer = observer)
        val entity = UUID.randomUUID()
        val source = AttributeSourceKey("test", "one")
        val modifier = AttributeModifier("bonus", key, AttributeOperation.ADD, 5.0)
        val rejected = store.replace(entity, source, listOf(modifier))
        assertTrue(rejected.cancelled)
        assertFalse(rejected.changed)
        assertEquals(0, store.state(entity).sources.size)
        cancel = false
        val applied = store.replace(entity, source, listOf(modifier))
        assertTrue(applied.changed)
        assertFalse(applied.cancelled)
        assertEquals(15.0, applied.state.snapshot.values[key])
        assertEquals(1, committed)
        val unchanged = store.replace(entity, source, listOf(modifier))
        assertFalse(unchanged.changed)
        assertFalse(unchanged.cancelled)
    }

    @Test
    fun `idle eviction preserves retained and recently touched entities`() {
        var now = 1_000L
        val repository = DefinitionRepository(DefinitionSnapshot.empty())
        val store = AttributeStateStore(repository, clock = { now })
        val stale = UUID.randomUUID()
        val retained = UUID.randomUUID()
        val recent = UUID.randomUUID()
        store.state(stale)
        store.state(retained)
        now = 2_000L
        store.state(recent)

        val evicted = store.evictIdle(1_500L) { it == retained }

        assertEquals(listOf(stale), evicted)
        assertEquals(2, store.size())
        assertTrue(store.evictIdle(2_000L).containsAll(listOf(retained, recent)))
        assertEquals(0, store.size())
    }

    @Test
    fun `no-op remove and empty batch do not materialize entity state`() {
        val repository = DefinitionRepository(DefinitionSnapshot.empty())
        val store = AttributeStateStore(repository)
        val entity = UUID.randomUUID()

        val removed = store.remove(entity, AttributeSourceKey("equipment", "head"))
        val reconciled = store.replaceSources(entity, { it.namespace == "equipment" }, emptyMap(), "equipment.batch")

        assertFalse(removed.changed)
        assertFalse(reconciled.changed)
        assertEquals(0, store.size())
        assertEquals(null, store.stateIfPresent(entity))
    }
}
