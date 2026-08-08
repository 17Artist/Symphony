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
import java.io.ByteArrayInputStream
import kotlin.io.path.writeText
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith

class LanguageBundleTest {
    @Test
    fun `loads nested messages colors lists and placeholders`() {
        val file = Files.createTempFile("symphony-language", ".yml")
        file.writeText(
            """
            gui:
              close: '&c关闭'
              lore:
                - '&7当前 {current}'
                - '&7上限 {maximum}'
            """.trimIndent()
        )

        val language = LanguageBundle.load(file)
        assertEquals("§c关闭", language.text("gui.close"))
        assertEquals(
            listOf("§7当前 3", "§7上限 5"),
            language.lines("gui.lore", mapOf("current" to 3, "maximum" to 5))
        )
    }

    @Test
    fun `rejects non-string leaves`() {
        val file = Files.createTempFile("symphony-language-invalid", ".yml")
        file.writeText("gui:\n  close: 42\n")
        assertFailsWith<IllegalArgumentException> { LanguageBundle.load(file) }
    }

    @Test
    fun `user language overrides defaults and inherits newly added keys`() {
        val defaults = LanguageBundle.load(
            ByteArrayInputStream("gui:\n  close: '&c关闭'\n  refresh: '&a刷新'\n".toByteArray()),
            "bundled language"
        )
        val custom = LanguageBundle.load(
            ByteArrayInputStream("gui:\n  close: '&d返回'\n".toByteArray()),
            "custom language"
        ).withFallback(defaults)

        assertEquals("§d返回", custom.text("gui.close"))
        assertEquals("§a刷新", custom.text("gui.refresh"))
    }
}
