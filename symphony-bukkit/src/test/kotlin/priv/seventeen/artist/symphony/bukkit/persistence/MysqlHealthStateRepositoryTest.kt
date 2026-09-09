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

import org.h2.jdbcx.JdbcDataSource
import java.util.UUID
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

class MysqlHealthStateRepositoryTest {
    @Test
    fun `released health is restored by the next server session`() {
        val repository = repository()
        val key = key()
        val first = HealthSessionToken(key, UUID.randomUUID())
        assertNull(repository.claim(first, StoredHealth(20.0, 20.0), waitMillis = 0))
        assertTrue(repository.checkpoint(first, StoredHealth(36.0, 40.0)))
        assertTrue(repository.release(first, StoredHealth(35.0, 40.0)))

        val second = HealthSessionToken(key, UUID.randomUUID())
        assertEquals(StoredHealth(35.0, 40.0), repository.claim(second, StoredHealth(20.0, 20.0), waitMillis = 0))
        assertTrue(repository.release(second, StoredHealth(34.0, 40.0)))
    }

    @Test
    fun `takeover rejects a late write from the old server`() {
        val repository = repository()
        val key = key()
        val source = HealthSessionToken(key, UUID.randomUUID())
        assertNull(repository.claim(source, StoredHealth(20.0, 20.0), waitMillis = 0))
        assertTrue(repository.checkpoint(source, StoredHealth(31.0, 40.0)))

        val target = HealthSessionToken(key, UUID.randomUUID())
        assertEquals(StoredHealth(31.0, 40.0), repository.claim(target, StoredHealth(20.0, 20.0), waitMillis = 0))
        assertFalse(repository.release(source, StoredHealth(39.0, 40.0)))
        assertTrue(repository.release(target, StoredHealth(27.0, 40.0)))

        val verifier = HealthSessionToken(key, UUID.randomUUID())
        assertEquals(StoredHealth(27.0, 40.0), repository.claim(verifier, StoredHealth(20.0, 20.0), waitMillis = 0))
    }

    @Test
    fun `different character keys retain independent health`() {
        val repository = repository()
        val player = UUID.randomUUID()
        val warriorKey = HealthStateKey("network", player, "a".repeat(64))
        val mageKey = HealthStateKey("network", player, "b".repeat(64))
        val warrior = HealthSessionToken(warriorKey, UUID.randomUUID())
        val mage = HealthSessionToken(mageKey, UUID.randomUUID())

        assertNull(repository.claim(warrior, StoredHealth(20.0, 20.0), waitMillis = 0))
        assertNull(repository.claim(mage, StoredHealth(20.0, 20.0), waitMillis = 0))
        assertTrue(repository.release(warrior, StoredHealth(50.0, 60.0)))
        assertTrue(repository.release(mage, StoredHealth(18.0, 30.0)))

        assertEquals(
            StoredHealth(50.0, 60.0),
            repository.claim(HealthSessionToken(warriorKey, UUID.randomUUID()), StoredHealth(20.0, 20.0), 0)
        )
        assertEquals(
            StoredHealth(18.0, 30.0),
            repository.claim(HealthSessionToken(mageKey, UUID.randomUUID()), StoredHealth(20.0, 20.0), 0)
        )
    }

    private fun repository(): MysqlHealthStateRepository {
        val dataSource = JdbcDataSource().apply {
            setURL("jdbc:h2:mem:${UUID.randomUUID()};MODE=MySQL;DB_CLOSE_DELAY=-1")
        }
        return MysqlHealthStateRepository(dataSource).also(MysqlHealthStateRepository::initialize)
    }

    private fun key(): HealthStateKey =
        HealthStateKey("network", UUID.randomUUID(), "c".repeat(64))
}
