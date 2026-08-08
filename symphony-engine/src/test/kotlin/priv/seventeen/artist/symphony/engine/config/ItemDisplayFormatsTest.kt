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

import java.io.ByteArrayInputStream
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue

class ItemDisplayFormatsTest {
    @Test
    fun `display id overrides selected lines and falls back to defaults`() {
        val formats = load(
            """
            schema: 1
            default:
              attribute:
                positive: '&a{name}: +{value}'
              set:
                title: '&6{name}'
            displays:
              compact:
                attribute:
                  positive: '&f+{value} {name}'
            """.trimIndent()
        )

        assertEquals("§f+12 火焰伤害", formats.render("compact", "attribute.positive", "name" to "火焰伤害", "value" to 12))
        assertEquals("§6元素先锋", formats.render("compact", "set.title", "name" to "元素先锋"))
        assertTrue(formats.has("compact", "set.title"))
    }

    @Test
    fun `missing format is rejected instead of leaking an internal key`() {
        val formats = load("schema: 1\ndefault:\n  item:\n    line: '{value}'")
        assertFailsWith<IllegalArgumentException> { formats.render(null, "socket.slot") }
    }

    private fun load(text: String): ItemDisplayFormats = ItemDisplayFormats.load(
        ByteArrayInputStream(text.toByteArray(Charsets.UTF_8)),
        "display-test.yml"
    )
}
