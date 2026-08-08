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

import java.time.Duration
import java.util.UUID
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import org.junit.jupiter.api.assertTimeout
import priv.seventeen.artist.symphony.api.attribute.AttributeBounds
import priv.seventeen.artist.symphony.api.attribute.AttributeDefinition
import priv.seventeen.artist.symphony.api.attribute.AttributeKey
import priv.seventeen.artist.symphony.api.attribute.AttributeModifier
import priv.seventeen.artist.symphony.api.attribute.AttributeOperation
import priv.seventeen.artist.symphony.api.attribute.AttributeSourceKey
import priv.seventeen.artist.symphony.api.source.ItemSourceSnapshot
import priv.seventeen.artist.symphony.engine.definition.CompiledAttributeDefinition
import priv.seventeen.artist.symphony.engine.definition.DefinitionRepository
import priv.seventeen.artist.symphony.engine.definition.DefinitionSnapshot

class AttributeStateStorePressureTest {
    @Test
    fun `high frequency source replacement remains bounded and releases state`() {
        val definitions = (0 until 96).associate { index ->
            val key = AttributeKey.symphony("pressure_$index")
            key to CompiledAttributeDefinition(
                AttributeDefinition(key, "Pressure $index", "", "pressure", 0.0, AttributeBounds(-1_000_000.0, 1_000_000.0)),
                emptyList()
            )
        }
        val store = AttributeStateStore(DefinitionRepository(DefinitionSnapshot.empty().copy(attributes = definitions)))
        val entityId = UUID.randomUUID()

        assertTimeout(Duration.ofSeconds(10)) {
            repeat(3_000) { index ->
                val key = definitions.keys.elementAt(index % definitions.size)
                val source = AttributeSourceKey("pressure", "source_${index % 128}")
                val result = store.replace(
                    entityId,
                    source,
                    listOf(AttributeModifier("value", key, AttributeOperation.ADD, (index % 31).toDouble()))
                )
                assertTrue(result.changed)
            }
        }

        assertEquals(3_000L, store.state(entityId).revision)
        assertEquals(1, store.size())
        store.removeEntity(entityId)
        assertEquals(0, store.size())
    }

    @Test
    fun `idle eviction clears a high cardinality cache within a bounded pass`() {
        var now = 1_000L
        val store = AttributeStateStore(DefinitionRepository(), clock = { now })
        repeat(25_000) { store.state(UUID.nameUUIDFromBytes("pressure-$it".toByteArray())) }
        assertEquals(25_000, store.size())

        now = 10_000L
        val evicted = assertTimeout(Duration.ofSeconds(5)) { store.evictIdle(5_000L) }
        assertEquals(25_000, evicted.size)
        assertEquals(0, store.size())
    }

    @Test
    fun `expired modifiers are removed from sources and snapshots`() {
        var now = 1_000L
        val key = AttributeKey.symphony("temporary")
        val definition = AttributeDefinition(key, "Temporary", "", "pressure", 10.0, AttributeBounds(0.0, 100.0))
        val store = AttributeStateStore(
            DefinitionRepository(DefinitionSnapshot.empty().copy(
                attributes = mapOf(key to CompiledAttributeDefinition(definition, emptyList()))
            )),
            clock = { now }
        )
        val entityId = UUID.randomUUID()
        store.replace(
            entityId,
            AttributeSourceKey("buff", "temporary"),
            listOf(AttributeModifier("temporary", key, AttributeOperation.ADD, 5.0, expiresAtMillis = 1_500L))
        )
        assertEquals(15.0, store.state(entityId).snapshot.values[key])

        now = 1_501L
        assertEquals(listOf(entityId), store.pruneExpired(now))
        assertEquals(10.0, store.state(entityId).snapshot.values[key])
        assertTrue(store.state(entityId).sources.isEmpty())
        assertTrue(store.pruneExpired(now).isEmpty())
    }

    @Test
    fun `item identity alone does not retain an expired temporary source`() {
        var now = 1_000L
        val key = AttributeKey.symphony("temporary_item")
        val source = AttributeSourceKey("external", "temporary-item")
        val definition = AttributeDefinition(key, "Temporary item", "", "pressure", 0.0, AttributeBounds(0.0, 100.0))
        val store = AttributeStateStore(
            DefinitionRepository(DefinitionSnapshot.empty().copy(
                attributes = mapOf(key to CompiledAttributeDefinition(definition, emptyList()))
            )),
            clock = { now }
        )
        val entityId = UUID.randomUUID()
        val modifier = AttributeModifier("temporary", key, AttributeOperation.ADD, 5.0, expiresAtMillis = 1_500L)
        store.replace(
            entityId,
            source,
            listOf(modifier),
            ItemSourceSnapshot(
                source = source,
                overtureItemId = "temporary_item",
                instanceId = UUID.randomUUID().toString(),
                modifiers = listOf(modifier),
                setPieces = emptyList()
            )
        )

        now = 1_501L
        assertEquals(listOf(entityId), store.pruneExpired(now))
        assertTrue(store.state(entityId).sources.isEmpty())
    }
}
