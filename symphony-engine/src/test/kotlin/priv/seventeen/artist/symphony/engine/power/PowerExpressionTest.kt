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

package priv.seventeen.artist.symphony.engine.power

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue

class PowerExpressionTest {
    @Test
    fun `formula combines attributes sets equipment and level deterministically`() {
        val expression = PowerExpressionCompiler.compile(
            """
            max(
              0,
              attribute("physical_damage") * 10
                + attribute('critical_chance') * 400
                + set_pieces("frost_guardian") * 25
                + set_tiers("frost_guardian") * 100
                + level * 15
                + skill_count * 30
                + enhancement_total * 12
            )
            """.trimIndent()
        )

        assertEquals(
            setOf(
                "attribute:physical_damage",
                "attribute:critical_chance",
                "set_pieces:frost_guardian",
                "set_tiers:frost_guardian",
                "level",
                "skill_count",
                "enhancement_total"
            ),
            expression.variables
        )
        assertEquals(
            1_115.0,
            expression.evaluate(
                mapOf(
                    "attribute:physical_damage" to 40.0,
                    "attribute:critical_chance" to 0.25,
                    "set_pieces:frost_guardian" to 4.0,
                    "set_tiers:frost_guardian" to 2.0,
                    "level" to 10.0,
                    "skill_count" to 3.0,
                    "enhancement_total" to 6.25
                )
            ),
            1.0e-9
        )
    }

    @Test
    fun `conditions and boolean operators short circuit unsafe branches`() {
        val expression = PowerExpressionCompiler.compile(
            "if(level >= 10 && set_tier_count > 0, sqrt(81) + pow(2, 3), 1 / 0)"
        )

        assertEquals(17.0, expression.evaluate(mapOf("level" to 10.0, "set_tier_count" to 1.0)), 1.0e-9)
    }

    @Test
    fun `missing known variables resolve to zero`() {
        val expression = PowerExpressionCompiler.compile("attribute(\"arcane_damage\") + gem_count")
        assertEquals(0.0, expression.evaluate(emptyMap()), 1.0e-9)
    }

    @Test
    fun `unknown symbols and unsafe arithmetic are rejected`() {
        val unknown = assertFailsWith<IllegalArgumentException> {
            PowerExpressionCompiler.compile("server.command(\"op\")")
        }
        assertTrue(unknown.message.orEmpty().contains("未知的战力"))

        assertFailsWith<IllegalArgumentException> {
            PowerExpressionCompiler.compile("10 / attribute(\"physical_defense\")").evaluate(emptyMap())
        }
        assertFailsWith<IllegalArgumentException> {
            PowerExpressionCompiler.compile("sqrt(-1)").evaluate(emptyMap())
        }
    }
}
