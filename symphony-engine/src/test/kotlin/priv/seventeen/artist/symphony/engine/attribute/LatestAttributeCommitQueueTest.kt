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
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue
import java.util.UUID

class LatestAttributeCommitQueueTest {
    @Test
    fun `multiple animation-period commits coalesce to the newest revision`() {
        val entityId = UUID.randomUUID()
        val queue = LatestAttributeCommitQueue()
        val applied = mutableListOf<Long>()

        queue.defer(entityId, 4) { applied += 4 }
        queue.defer(entityId, 5) { applied += 5 }
        queue.defer(entityId, 6) { applied += 6 }

        assertEquals(6, queue.revision(entityId))
        assertTrue(queue.flush(entityId))
        assertEquals(listOf(6L), applied)
        assertNull(queue.revision(entityId))
        assertFalse(queue.flush(entityId))
    }

    @Test
    fun `late older completion cannot replace a newer pending commit`() {
        val entityId = UUID.randomUUID()
        val queue = LatestAttributeCommitQueue()
        var applied = 0L

        queue.defer(entityId, 9) { applied = 9 }
        queue.defer(entityId, 7) { applied = 7 }

        assertEquals(9, queue.revision(entityId))
        queue.flush(entityId)
        assertEquals(9, applied)
    }

    @Test
    fun `forget and clear discard deferred side effects`() {
        val first = UUID.randomUUID()
        val second = UUID.randomUUID()
        val queue = LatestAttributeCommitQueue()
        var calls = 0

        queue.defer(first, 1) { calls++ }
        queue.defer(second, 1) { calls++ }
        queue.forget(first)
        assertFalse(queue.flush(first))
        queue.clear()
        assertFalse(queue.flush(second))
        assertEquals(0, calls)
    }
}
