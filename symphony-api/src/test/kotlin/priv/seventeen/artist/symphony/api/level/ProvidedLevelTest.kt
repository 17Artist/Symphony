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

package priv.seventeen.artist.symphony.api.level

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith

class ProvidedLevelTest {
    @Test
    fun `supports provider owned character and experience metadata`() {
        val level = ProvidedLevel(
            level = 42,
            experience = 1_200,
            experienceForNextLevel = 2_000,
            characterId = "warrior-1",
            characterName = "先锋",
            metadata = mapOf("rank" to "A")
        )
        assertEquals("warrior-1", level.characterId)
        assertEquals("A", level.metadata["rank"])
    }

    @Test
    fun `rejects invalid external snapshots at the api boundary`() {
        assertFailsWith<IllegalArgumentException> { ProvidedLevel(-1) }
        assertFailsWith<IllegalArgumentException> { ProvidedLevel(1, experience = -1) }
        assertFailsWith<IllegalArgumentException> { ProvidedLevel(1, experienceForNextLevel = 0) }
        assertFailsWith<IllegalArgumentException> { ProvidedLevel(1, characterId = "") }
    }
}
