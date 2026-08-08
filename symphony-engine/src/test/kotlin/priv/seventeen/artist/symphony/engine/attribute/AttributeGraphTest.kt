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

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import priv.seventeen.artist.symphony.api.attribute.AttributeDefinition
import priv.seventeen.artist.symphony.api.attribute.AttributeKey
import priv.seventeen.artist.symphony.engine.definition.CompiledAttributeDefinition

class AttributeGraphTest {
    @Test
    fun `orders dependencies and expands affected closure`() {
        val a = AttributeKey.symphony("a")
        val b = AttributeKey.symphony("b")
        val c = AttributeKey.symphony("c")
        val graph = AttributeGraph.build(
            mapOf(
                c to compiled(c, setOf(b)),
                a to compiled(a),
                b to compiled(b, setOf(a))
            )
        )
        assertEquals(listOf(a, b, c), graph.topologicalOrder)
        assertEquals(setOf(a, b, c), graph.affectedClosure(setOf(a)))
    }

    @Test
    fun `rejects cycles`() {
        val a = AttributeKey.symphony("a")
        val b = AttributeKey.symphony("b")
        assertFailsWith<IllegalArgumentException> {
            AttributeGraph.build(mapOf(a to compiled(a, setOf(b)), b to compiled(b, setOf(a))))
        }
    }

    private fun compiled(key: AttributeKey, dependencies: Set<AttributeKey> = emptySet()) =
        CompiledAttributeDefinition(AttributeDefinition(key, key.value, "", "test", 0.0, dependsOn = dependencies), emptyList())
}

