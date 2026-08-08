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

package priv.seventeen.artist.symphony.integrations.mythic

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNull
import priv.seventeen.artist.symphony.api.attribute.AttributeOperation

class MythicAttributeConfigurationTest {
    private val configuration = MythicAttributeConfiguration()

    @Test
    fun `structured attributes support namespaces percentages and level scaling`() {
        val modifiers = configuration.compile(
            enabled = true,
            structured = listOf(
                MythicAttributeEntry(
                    attribute = "physical_damage",
                    operation = "add",
                    value = "25",
                    perLevel = "2",
                    priority = "10",
                    description = "首领物理伤害"
                ),
                MythicAttributeEntry(
                    attribute = "critical_chance",
                    operation = "add",
                    value = "10%",
                    perLevel = "2%"
                ),
                MythicAttributeEntry(
                    attribute = "my_addon:spell_power",
                    operation = "multiply_total",
                    value = "15%"
                )
            ),
            legacyLines = emptyList(),
            mobLevel = 3.0
        )

        assertEquals(3, modifiers.size)
        val physical = modifiers.single { it.attribute.value == "symphony:physical_damage" }
        assertEquals(29.0, physical.value, 1.0e-9)
        assertEquals(AttributeOperation.ADD, physical.operation)
        assertEquals(10, physical.priority)
        assertEquals("首领物理伤害", physical.description)

        val critical = modifiers.single { it.attribute.value == "symphony:critical_chance" }
        assertEquals(0.14, critical.value, 1.0e-9)
        assertNull(critical.description)

        val external = modifiers.single { it.attribute.value == "my_addon:spell_power" }
        assertEquals(0.15, external.value, 1.0e-9)
        assertEquals(AttributeOperation.MULTIPLY_TOTAL, external.operation)
    }

    @Test
    fun `legacy lines remain available without structured entries`() {
        val modifiers = configuration.compile(
            enabled = true,
            structured = emptyList(),
            legacyLines = listOf(
                "symphony:max_health ADD 80",
                "symphony:fire_resistance ADD 25%"
            ),
            mobLevel = 20.0
        )

        assertEquals(80.0, modifiers[0].value, 1.0e-9)
        assertEquals(0.25, modifiers[1].value, 1.0e-9)
    }

    @Test
    fun `disabled configuration produces no attributes`() {
        val modifiers = configuration.compile(
            enabled = false,
            structured = listOf(MythicAttributeEntry("physical_damage", "add", "20")),
            legacyLines = emptyList(),
            mobLevel = 1.0
        )
        assertEquals(emptyList(), modifiers)
    }

    @Test
    fun `mixed formats duplicates and invalid values are rejected`() {
        assertFailsWith<IllegalArgumentException> {
            configuration.compile(
                true,
                listOf(MythicAttributeEntry("physical_damage", "add", "20")),
                listOf("symphony:physical_damage ADD 20"),
                1.0
            )
        }
        assertFailsWith<IllegalArgumentException> {
            configuration.compile(
                true,
                listOf(
                    MythicAttributeEntry("physical_damage", "add", "20"),
                    MythicAttributeEntry("symphony:physical_damage", "add", "30")
                ),
                emptyList(),
                1.0
            )
        }
        assertFailsWith<IllegalArgumentException> {
            configuration.compile(
                true,
                listOf(MythicAttributeEntry("physical_damage", "add", "twenty")),
                emptyList(),
                1.0
            )
        }
        assertFailsWith<IllegalArgumentException> {
            configuration.compile(true, emptyList(), emptyList(), Double.NaN)
        }
    }
}
