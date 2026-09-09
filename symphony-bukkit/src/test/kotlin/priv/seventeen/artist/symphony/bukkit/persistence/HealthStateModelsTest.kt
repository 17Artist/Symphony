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

package priv.seventeen.artist.symphony.bukkit.persistence

import org.bukkit.NamespacedKey
import priv.seventeen.artist.symphony.api.level.LevelSnapshot
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

class HealthStateModelsTest {
    @Test
    fun `pdc codec round trips finite health values and rejects corrupt payloads`() {
        val value = StoredHealth(37.5, 80.0)
        assertEquals(value, StoredHealthCodec.decode(StoredHealthCodec.encode(value)))
        assertNull(StoredHealthCodec.decode(byteArrayOf(1, 2, 3)))
        assertNull(StoredHealthCodec.decode(StoredHealthCodec.encode(value).also { it[0] = 9 }))
    }

    @Test
    fun `character key separates providers and characters without exposing raw ids`() {
        val first = level("role:a", "warrior")
        val same = level("role:a", "warrior")
        val otherProvider = level("role:b", "warrior")
        val otherCharacter = level("role:a", "mage")

        assertEquals(HealthCharacterKey.from(first), HealthCharacterKey.from(same))
        assertNotEquals(HealthCharacterKey.from(first), HealthCharacterKey.from(otherProvider))
        assertNotEquals(HealthCharacterKey.from(first), HealthCharacterKey.from(otherCharacter))
        assertEquals(64, HealthCharacterKey.from(first).length)
        assertEquals(HealthCharacterKey.from(null), HealthCharacterKey.from(level("role:a", null)))
    }

    @Test
    fun `restore is cancelled when health changes while loading and clamps to current maximum`() {
        val stored = StoredHealth(48.0, 60.0)
        assertTrue(HealthRestorePolicy.mayRestore(20.0, 20.0, stored))
        assertFalse(HealthRestorePolicy.mayRestore(20.0, 19.0, stored))
        assertFalse(HealthRestorePolicy.healthChangedSinceJoin(20.0, 20.0))
        assertTrue(HealthRestorePolicy.healthChangedSinceJoin(20.0, 19.0))
        assertEquals(40.0, HealthRestorePolicy.restoredHealth(stored, 40.0))
        assertEquals(48.0, HealthRestorePolicy.restoredHealth(stored, 80.0))
        assertTrue(HealthRestorePolicy.hasChanged(stored, StoredHealth(47.0, 60.0)))
        assertFalse(HealthRestorePolicy.hasChanged(stored, stored.copy()))
    }

    private fun level(provider: String, character: String?): LevelSnapshot = LevelSnapshot(
        NamespacedKey.fromString(provider)!!,
        provider,
        1,
        null,
        null,
        character,
        null,
        emptyMap()
    )
}
