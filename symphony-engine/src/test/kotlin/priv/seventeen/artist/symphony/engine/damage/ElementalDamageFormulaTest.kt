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

class ElementalDamageFormulaTest {
    @Test
    fun `zero modifiers preserve channel damage`() {
        assertEquals(100.0, ElementalDamageFormula.calculate(100.0, 0.0, 0.0).output)
    }

    @Test
    fun `resistance and amplification multiply in a deterministic order`() {
        val result = ElementalDamageFormula.calculate(100.0, resistance = 0.25, amplification = 0.20)
        assertEquals(0.75, result.resistanceMultiplier)
        assertEquals(1.20, result.amplificationMultiplier)
        assertEquals(90.0, result.output)
    }

    @Test
    fun `full resistance and minus one amplification independently grant immunity`() {
        assertEquals(0.0, ElementalDamageFormula.calculate(100.0, 1.0, 10.0).output)
        assertEquals(0.0, ElementalDamageFormula.calculate(100.0, 0.0, -1.0).output)
    }

    @Test
    fun `out of range defensive values are safely bounded`() {
        val result = ElementalDamageFormula.calculate(80.0, resistance = 4.0, amplification = -5.0)
        assertEquals(1.0, result.resistance)
        assertEquals(-1.0, result.amplification)
        assertEquals(0.0, result.output)
    }

    @Test
    fun `reaction advantage is applied before resistance and amplification`() {
        val reactionAdjustedInput = 40.0 * 2.5
        assertEquals(90.0, ElementalDamageFormula.calculate(reactionAdjustedInput, 0.25, 0.20).output)
    }

    @Test
    fun `invalid external values fail closed`() {
        assertFailsWith<IllegalArgumentException> { ElementalDamageFormula.calculate(-1.0, 0.0, 0.0) }
        assertFailsWith<IllegalArgumentException> { ElementalDamageFormula.calculate(1.0, Double.NaN, 0.0) }
        assertFailsWith<IllegalArgumentException> { ElementalDamageFormula.calculate(1.0, 0.0, Double.POSITIVE_INFINITY) }
    }
}
