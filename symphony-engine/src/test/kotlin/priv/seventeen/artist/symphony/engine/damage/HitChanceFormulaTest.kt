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

class HitChanceFormulaTest {
    @Test
    fun `neutral accuracy offsets its baseline before comparing dodge`() {
        assertEquals(0.0, HitChanceFormula.dodgeChance(1.0, 0.0))
        assertEquals(0.25, HitChanceFormula.dodgeChance(1.0, 0.25))
        assertEquals(0.05, HitChanceFormula.dodgeChance(1.2, 0.25), 1.0e-12)
        assertEquals(0.45, HitChanceFormula.dodgeChance(0.8, 0.25), 1.0e-12)
    }

    @Test
    fun `dodge chance is bounded and rejects invalid inputs`() {
        assertEquals(0.0, HitChanceFormula.dodgeChance(2.0, 0.1))
        assertEquals(0.9, HitChanceFormula.dodgeChance(1.0, 4.0))
        assertFailsWith<IllegalArgumentException> { HitChanceFormula.dodgeChance(Double.NaN, 0.0) }
    }
}
