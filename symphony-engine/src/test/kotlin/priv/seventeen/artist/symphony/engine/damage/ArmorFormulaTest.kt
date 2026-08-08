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

package priv.seventeen.artist.symphony.engine.damage

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue
import priv.seventeen.artist.symphony.api.attribute.AttributeKey
import priv.seventeen.artist.symphony.engine.definition.ArmorFormulaDefinition
import priv.seventeen.artist.symphony.engine.definition.ArmorFormulaType

class ArmorFormulaTest {
    private val formula = ArmorFormulaDefinition(
        ArmorFormulaType.DIMINISHING,
        100.0,
        AttributeKey.symphony("physical_defense"),
        AttributeKey.symphony("penetration"),
        AttributeKey.symphony("flat_penetration")
    )

    @Test
    fun `one hundred armor with one hundred constant halves damage`() {
        assertEquals(50.0, ArmorFormula.calculate(100.0, 100.0, 0.0, 0.0, formula).afterArmor)
    }

    @Test
    fun `defense and penetration remain monotonic`() {
        val lowDefense = ArmorFormula.calculate(100.0, 10.0, 0.0, 0.0, formula).afterArmor
        val highDefense = ArmorFormula.calculate(100.0, 100.0, 0.0, 0.0, formula).afterArmor
        val penetrated = ArmorFormula.calculate(100.0, 100.0, 0.5, 0.0, formula).afterArmor
        assertTrue(highDefense <= lowDefense)
        assertTrue(penetrated >= highDefense)
    }

    @Test
    fun `percent and flat penetration are applied in documented order`() {
        val result = ArmorFormula.calculate(100.0, 200.0, 0.25, 25.0, formula)
        assertEquals(125.0, result.effectiveArmor)
        assertEquals(100.0 * 100.0 / 225.0, result.afterArmor)
    }

    @Test
    fun `negative defense and penetration cannot invert mitigation`() {
        assertEquals(100.0, ArmorFormula.calculate(100.0, -50.0, 0.0, 0.0, formula).afterArmor)
        assertEquals(50.0, ArmorFormula.calculate(100.0, 100.0, -2.0, -20.0, formula).afterArmor)
    }

    @Test
    fun `penetration over one hundred percent is bounded`() {
        val result = ArmorFormula.calculate(100.0, 1_000.0, 8.0, 0.0, formula)
        assertEquals(0.0, result.effectiveArmor)
        assertEquals(100.0, result.afterArmor)
    }

    @Test
    fun `invalid inputs fail instead of introducing non finite damage`() {
        assertFailsWith<IllegalArgumentException> {
            ArmorFormula.calculate(Double.NaN, 100.0, 0.0, 0.0, formula)
        }
        assertFailsWith<IllegalArgumentException> {
            ArmorFormula.calculate(10.0, 100.0, 0.0, 0.0, formula.copy(constant = 0.0))
        }
    }
}
