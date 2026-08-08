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

package priv.seventeen.artist.symphony.bukkit

import java.nio.file.Files
import java.nio.file.Path
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertTrue
import org.junit.jupiter.api.io.TempDir
import priv.seventeen.artist.symphony.bukkit.lifecycle.BundledShowcaseInstaller

class BundledShowcaseInstallerTest {
    @TempDir
    lateinit var directory: Path

    private val resources = Path.of("src", "main", "resources").toAbsolutePath().normalize()

    @Test
    fun `fresh install writes Symphony and Overture showcase once`() {
        val symphony = directory.resolve("plugins/Symphony")
        val overture = directory.resolve("plugins/Overture")

        val first = BundledShowcaseInstaller.install(symphony, overture, true, ::openResource)
        assertTrue(first.attempted)
        assertTrue(first.installed)
        assertEquals(34, first.copiedSymphonyFiles)
        assertEquals(17, first.copiedOvertureFiles)
        assertEquals(0, first.verifiedExistingFiles)
        assertTrue(Files.isRegularFile(symphony.resolve("combat-power.yml")))
        assertTrue(Files.isRegularFile(symphony.resolve("items/socket-removal.yml")))
        assertTrue(Files.isRegularFile(overture.resolve("displays/prismatic-arsenal.yml")))
        assertTrue(Files.isRegularFile(overture.resolve("items/prismatic-arsenal/__group__.yml")))
        assertTrue(Files.isRegularFile(symphony.resolve(BundledShowcaseInstaller.INSTALLED_MARKER)))
        assertFalse(Files.exists(symphony.resolve(BundledShowcaseInstaller.INSTALLING_MARKER)))

        val second = BundledShowcaseInstaller.install(symphony, overture, true, ::openResource)
        assertFalse(second.attempted)
        assertFalse(second.installed)
        assertEquals(0, second.copiedSymphonyFiles + second.copiedOvertureFiles)
    }

    @Test
    fun `existing installation does not receive showcase during an upgrade`() {
        val symphony = directory.resolve("plugins/Symphony")
        val overture = directory.resolve("plugins/Overture")
        Files.createDirectories(symphony)
        Files.writeString(symphony.resolve("config.yml"), "schema: 2\n")

        val result = BundledShowcaseInstaller.install(symphony, overture, false, ::openResource)
        assertFalse(result.attempted)
        assertFalse(Files.exists(overture.resolve("items/prismatic-arsenal")))
    }

    @Test
    fun `interrupted install resumes but refuses conflicting files`() {
        val symphony = directory.resolve("plugins/Symphony")
        val overture = directory.resolve("plugins/Overture")
        Files.createDirectories(symphony.resolve("advanced/environments"))
        Files.writeString(symphony.resolve(BundledShowcaseInstaller.INSTALLING_MARKER), "showcase=prismatic-arsenal\n")
        val target = symphony.resolve("advanced/environments/stormfront.yml")
        Files.writeString(target, "changed-by-owner: true\n")

        val error = assertFailsWith<IllegalArgumentException> {
            BundledShowcaseInstaller.install(symphony, overture, false, ::openResource)
        }
        assertTrue(error.message.orEmpty().contains("未覆盖"))
        assertEquals("changed-by-owner: true\n", Files.readString(target))
        assertTrue(Files.isRegularFile(symphony.resolve(BundledShowcaseInstaller.INSTALLING_MARKER)))
    }

    private fun openResource(name: String) =
        resources.resolve(name).takeIf(Files::isRegularFile)?.let(Files::newInputStream)
}
