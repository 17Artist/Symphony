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
import kotlin.test.assertFalse
import kotlin.test.assertTrue
import org.junit.jupiter.api.io.TempDir
import priv.seventeen.artist.symphony.bukkit.lifecycle.BundledShowcaseInstaller
import priv.seventeen.artist.symphony.bukkit.lifecycle.DefaultResources

class BundledShowcaseInstallerTest {
    @TempDir
    lateinit var directory: Path

    private val resources = Path.of("src", "main", "resources").toAbsolutePath().normalize()

    @Test
    fun `fresh install writes Symphony and Overture showcase once`() {
        val symphony = directory.resolve("plugins/Symphony")
        val overture = directory.resolve("plugins/Overture")

        val first = BundledShowcaseInstaller.install(
            symphony,
            overture,
            DefaultResources.isFirstInstall(symphony),
            ::openResource
        )
        assertTrue(first.attempted)
        assertTrue(first.installed)
        assertEquals(34, first.copiedSymphonyFiles)
        assertEquals(17, first.copiedOvertureFiles)
        assertEquals(0, first.preservedExistingFiles)
        assertTrue(Files.isRegularFile(symphony.resolve("combat-power.yml")))
        assertTrue(Files.isRegularFile(symphony.resolve("items/socket-removal.yml")))
        assertTrue(Files.isRegularFile(overture.resolve("displays/prismatic-arsenal.yml")))
        assertTrue(Files.isRegularFile(overture.resolve("items/prismatic-arsenal/__group__.yml")))
        assertTrue(Files.isRegularFile(symphony.resolve(BundledShowcaseInstaller.INSTALLED_MARKER)))
        assertFalse(Files.exists(symphony.resolve(BundledShowcaseInstaller.INSTALLING_MARKER)))

        val second = BundledShowcaseInstaller.install(
            symphony,
            overture,
            DefaultResources.isFirstInstall(symphony),
            ::openResource
        )
        assertFalse(second.attempted)
        assertFalse(second.installed)
        assertEquals(0, second.copiedSymphonyFiles + second.copiedOvertureFiles)
    }

    @Test
    fun `existing data directory never receives showcase`() {
        val symphony = directory.resolve("plugins/Symphony")
        val overture = directory.resolve("plugins/Overture")
        Files.createDirectories(symphony)
        Files.writeString(symphony.resolve(BundledShowcaseInstaller.INSTALLING_MARKER), "showcase=prismatic-arsenal\n")

        val result = BundledShowcaseInstaller.install(
            symphony,
            overture,
            DefaultResources.isFirstInstall(symphony),
            ::openResource
        )
        assertFalse(result.attempted)
        assertFalse(Files.exists(overture.resolve("items/prismatic-arsenal")))
    }

    @Test
    fun `Blink bootstrap entries do not hide a fresh Symphony install`() {
        val symphony = directory.resolve("plugins/Symphony")
        Files.createDirectories(symphony.resolve("libs"))
        Files.writeString(symphony.resolve("blink.yml"), "repositories: []\n")

        assertTrue(DefaultResources.isFirstInstall(symphony))
        Files.writeString(symphony.resolve("config.yml"), "schema: 2\n")
        assertFalse(DefaultResources.isFirstInstall(symphony))
    }

    @Test
    fun `fresh install preserves existing Overture files`() {
        val symphony = directory.resolve("plugins/Symphony")
        val overture = directory.resolve("plugins/Overture")
        val target = overture.resolve("items/prismatic-arsenal/01-equipment/weapons.yml")
        Files.createDirectories(requireNotNull(target.parent))
        Files.writeString(target, "changed-by-owner: true\n")

        val result = BundledShowcaseInstaller.install(
            symphony,
            overture,
            DefaultResources.isFirstInstall(symphony),
            ::openResource
        )

        assertTrue(result.installed)
        assertEquals(1, result.preservedExistingFiles)
        assertEquals("changed-by-owner: true\n", Files.readString(target))
        assertTrue(Files.isRegularFile(symphony.resolve(BundledShowcaseInstaller.INSTALLED_MARKER)))
        assertFalse(Files.exists(symphony.resolve(BundledShowcaseInstaller.INSTALLING_MARKER)))
    }

    private fun openResource(name: String) =
        resources.resolve(name).takeIf(Files::isRegularFile)?.let(Files::newInputStream)
}
