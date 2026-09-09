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

import org.junit.jupiter.api.io.TempDir
import java.nio.file.Files
import java.nio.file.Path
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue

class HealthDatabaseSettingsTest {
    @TempDir
    lateinit var directory: Path

    @Test
    fun `mysql settings are loaded strictly`() {
        val path = directory.resolve("database.yml")
        Files.writeString(
            path,
            """
            schema: 1
            mysql:
              host: db.internal
              port: 3307
              database: game
              username: symphony
              password: secret
              use-ssl: true
              allow-public-key-retrieval: false
              pool-size: 6
            """.trimIndent()
        )
        val settings = HealthDatabaseSettingsLoader.load(path)
        assertEquals("db.internal", settings.host)
        assertEquals(3307, settings.port)
        assertEquals("game", settings.database)
        assertTrue(settings.useSsl)
        assertEquals(6, settings.poolSize)
    }

    @Test
    fun `unknown database fields are rejected`() {
        val path = directory.resolve("database.yml")
        Files.writeString(
            path,
            """
            schema: 1
            mysql:
              host: localhost
              database: game
              username: symphony
              unexpected: true
            """.trimIndent()
        )
        assertFailsWith<IllegalArgumentException> { HealthDatabaseSettingsLoader.load(path) }
    }
}
