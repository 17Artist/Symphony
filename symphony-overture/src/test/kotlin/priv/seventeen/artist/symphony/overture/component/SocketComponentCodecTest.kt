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

package priv.seventeen.artist.symphony.overture.component

import java.nio.file.Path
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertTrue
import org.bukkit.NamespacedKey
import priv.seventeen.artist.overture.api.component.ComponentDecodeContext
import priv.seventeen.artist.overture.api.component.ComponentDecodeResult
import priv.seventeen.artist.overture.api.data.ItemDataNode
import priv.seventeen.artist.symphony.engine.config.LanguageBundle

class SocketComponentCodecTest {
    private val language = LanguageBundle.load(
        Path.of("..", "symphony-bukkit", "src", "main", "resources", "assets", "language.yml")
            .toAbsolutePath().normalize()
    )
    private val codec = SocketComponentCodec { language }

    @Test
    fun `accepts specific multi category and universal slots`() {
        val result = codec.decode(context(
            compound(
                "max-extra-slots" to ItemDataNode.Integer(2L),
                "slots" to ItemDataNode.ListNode(listOf(
                    compound(
                        "accepts" to ItemDataNode.ListNode(listOf(ItemDataNode.Text("fire"), ItemDataNode.Text("offense"))),
                        "unlock-at-enhancement" to ItemDataNode.Integer(0L)
                    ),
                    compound(
                        "accepts" to ItemDataNode.ListNode(listOf(ItemDataNode.Text("*"))),
                        "unlock-at-enhancement" to ItemDataNode.Integer(5L)
                    )
                ))
            )
        ))

        val success = assertIs<ComponentDecodeResult.Success>(result)
        val slots = assertIs<ItemDataNode.ListNode>(success.definition.values.getValue("slots"))
        assertEquals(2, slots.values.size)
        val universal = assertIs<ItemDataNode.Compound>(slots.values[1])
        val accepts = assertIs<ItemDataNode.ListNode>(universal.values.getValue("accepts"))
        assertEquals(listOf("*"), accepts.values.map { (it as ItemDataNode.Text).value })
    }

    @Test
    fun `rejects invalid categories with localized diagnostic`() {
        val result = codec.decode(context(
            compound(
                "slots" to ItemDataNode.ListNode(listOf(
                    compound("accepts" to ItemDataNode.ListNode(listOf(ItemDataNode.Text("Fire Gem"))))
                ))
            )
        ))

        val failure = assertIs<ComponentDecodeResult.Failure>(result)
        assertTrue(failure.issues.any { it.message == language.text("component.socket.category") })
    }

    private fun context(source: ItemDataNode.Compound) = ComponentDecodeContext(
        NamespacedKey("symphony", "sockets"),
        "test:item",
        source,
        "items.test.components.symphony:sockets"
    )

    private fun compound(vararg values: Pair<String, ItemDataNode>) = ItemDataNode.Compound(linkedMapOf(*values))
}
