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

package priv.seventeen.artist.symphony.bukkit.script

import java.util.UUID
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class CallbackIntervalGateTest {
    @Test
    fun `interval state is released on entity cleanup and expiry maintenance`() {
        val gate = CallbackIntervalGate(1_000L)
        val first = UUID.randomUUID()
        val second = UUID.randomUUID()
        assertTrue(gate.tryAcquire(first, 1_000L))
        assertFalse(gate.tryAcquire(first, 1_999L))
        assertTrue(gate.tryAcquire(second, 1_000L))
        assertEquals(2, gate.size())

        gate.forget(first)
        assertEquals(1, gate.size())
        gate.pruneExpired(2_000L)
        assertEquals(0, gate.size())
        assertTrue(gate.tryAcquire(first, 2_000L))
    }
}
