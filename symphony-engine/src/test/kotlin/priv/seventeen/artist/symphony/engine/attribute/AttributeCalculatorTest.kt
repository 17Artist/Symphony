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
import kotlin.test.assertFailsWith
import priv.seventeen.artist.symphony.api.attribute.AttributeBounds
import priv.seventeen.artist.symphony.api.attribute.AttributeDefinition
import priv.seventeen.artist.symphony.api.attribute.AttributeFormat
import priv.seventeen.artist.symphony.api.attribute.AttributeKey
import priv.seventeen.artist.symphony.api.attribute.AttributeModifier
import priv.seventeen.artist.symphony.api.attribute.AttributeOperation
import priv.seventeen.artist.symphony.api.attribute.AttributeSourceKey

class AttributeCalculatorTest {
    private val key = AttributeKey.symphony("test")
    private val definition = AttributeDefinition(
        key, "Test", "", "test", 100.0,
        AttributeBounds(0.0, 500.0), AttributeFormat.NUMBER, 2
    )

    @Test
    fun `uses documented operation order`() {
        val source = AttributeSourceKey("test", "one")
        val modifiers = listOf(
            source to modifier("total", AttributeOperation.MULTIPLY_TOTAL, 0.5),
            source to modifier("base", AttributeOperation.MULTIPLY_BASE, 0.2),
            source to modifier("add", AttributeOperation.ADD, 10.0)
        )
        val result = AttributeCalculator().calculate(UUID.randomUUID(), definition, modifiers, emptyMap(), 1, setOf("test"))
        assertEquals(195.0, result.value)
        assertEquals(listOf("add", "base", "total"), result.explain.contributions.map { it.modifier.id })
    }

    @Test
    fun `rejects non finite callback output`() {
        val calculator = AttributeCalculator(CalculateHook { _, _, _, _ -> Double.NaN })
        assertFailsWith<IllegalArgumentException> {
            calculator.calculate(UUID.randomUUID(), definition, emptyList(), emptyMap(), 1, emptySet())
        }
    }

    private fun modifier(id: String, operation: AttributeOperation, value: Double) =
        AttributeModifier(id, key, operation, value)
}
