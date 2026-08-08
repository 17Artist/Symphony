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

package priv.seventeen.artist.symphony.bukkit.service

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs

class VanillaAttributeSyncPolicyTest {
    @Test
    fun `neutral definition value never claims vanilla authority`() {
        assertIs<VanillaSyncDirective.Clear>(
            VanillaAttributeSyncPolicy.directive(20.0, 20.0, VanillaSyncMode.ABSOLUTE)
        )
        assertIs<VanillaSyncDirective.Clear>(
            VanillaAttributeSyncPolicy.directive(1.0, 1.0, VanillaSyncMode.MULTIPLY_TOTAL)
        )
    }

    @Test
    fun `absolute attributes target the calculated Symphony value`() {
        assertEquals(
            VanillaSyncDirective.Absolute(32.0),
            VanillaAttributeSyncPolicy.directive(20.0, 32.0, VanillaSyncMode.ABSOLUTE)
        )
    }

    @Test
    fun `attack speed is a multiplier over the current vanilla result`() {
        val faster = assertIs<VanillaSyncDirective.MultiplyTotal>(
            VanillaAttributeSyncPolicy.directive(1.0, 1.25, VanillaSyncMode.MULTIPLY_TOTAL)
        )
        val slower = assertIs<VanillaSyncDirective.MultiplyTotal>(
            VanillaAttributeSyncPolicy.directive(1.0, 0.8, VanillaSyncMode.MULTIPLY_TOTAL)
        )
        assertEquals(0.25, faster.amount, 1.0e-9)
        assertEquals(-0.2, slower.amount, 1.0e-9)
    }
}
