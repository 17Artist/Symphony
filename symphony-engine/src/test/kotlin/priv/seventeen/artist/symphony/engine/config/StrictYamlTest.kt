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

package priv.seventeen.artist.symphony.engine.config

import java.nio.file.Files
import java.nio.file.Path
import kotlin.test.Test
import kotlin.test.assertFailsWith
import org.junit.jupiter.api.io.TempDir

class StrictYamlTest {
    @TempDir lateinit var directory: Path

    @Test
    fun `duplicate keys are rejected`() {
        val file = directory.resolve("duplicate.yml")
        Files.writeString(file, "schema: 1\nschema: 1\n")
        assertFailsWith<RuntimeException> { StrictYaml().load(file) }
    }

    @Test
    fun `oversized source is rejected before parse`() {
        val file = directory.resolve("large.yml")
        Files.writeString(file, "x".repeat(2 * 1024 * 1024 + 1))
        assertFailsWith<IllegalArgumentException> { StrictYaml().load(file) }
    }
}
