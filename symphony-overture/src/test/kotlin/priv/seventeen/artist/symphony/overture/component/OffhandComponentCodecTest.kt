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

class OffhandComponentCodecTest {
    private val language = LanguageBundle.load(
        Path.of("..", "symphony-bukkit", "src", "main", "resources", "assets", "language.yml")
            .toAbsolutePath().normalize()
    )
    private val codec = OffhandComponentCodec { language }

    @Test
    fun `normalizes item eligibility and percentage`() {
        val result = codec.decode(context(ItemDataNode.Compound(linkedMapOf(
            "enabled" to ItemDataNode.Bool(true),
            "attribute-scale" to ItemDataNode.Text("40%")
        ))))

        val success = assertIs<ComponentDecodeResult.Success>(result)
        assertEquals(true, (success.definition.values.getValue("enabled") as ItemDataNode.Bool).value)
        assertEquals(0.4, (success.definition.values.getValue("attribute_scale") as ItemDataNode.Decimal).value)
    }

    @Test
    fun `rejects an out of range item ratio`() {
        val result = codec.decode(context(ItemDataNode.Compound(linkedMapOf(
            "enabled" to ItemDataNode.Bool(true),
            "attribute-scale" to ItemDataNode.Text("125%")
        ))))

        val failure = assertIs<ComponentDecodeResult.Failure>(result)
        assertTrue(failure.issues.any { it.message == language.text("component.offhand.attribute-scale") })
    }

    @Test
    fun `rejects a non boolean eligibility switch`() {
        val result = codec.decode(context(ItemDataNode.Compound(linkedMapOf(
            "enabled" to ItemDataNode.Text("yes")
        ))))

        val failure = assertIs<ComponentDecodeResult.Failure>(result)
        assertTrue(failure.issues.any { it.message == language.text("component.offhand.enabled") })
    }

    private fun context(source: ItemDataNode.Compound) = ComponentDecodeContext(
        NamespacedKey("symphony", "offhand"),
        "test:item",
        source,
        "items.test.components.symphony:offhand"
    )
}
