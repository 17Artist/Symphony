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

package priv.seventeen.artist.symphony.engine.equipment

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertSame
import priv.seventeen.artist.symphony.api.attribute.AttributeKey
import priv.seventeen.artist.symphony.api.attribute.AttributeModifier
import priv.seventeen.artist.symphony.api.attribute.AttributeOperation
import priv.seventeen.artist.symphony.api.attribute.AttributeSourceKey
import priv.seventeen.artist.symphony.api.source.ItemSourceSnapshot

class OffhandSourcePolicyTest {
    private val source = ItemSourceSnapshot(
        source = AttributeSourceKey("equipment", "off_hand"),
        overtureItemId = "test_item",
        instanceId = null,
        modifiers = listOf(
            AttributeModifier("damage", AttributeKey.symphony("physical_damage"), AttributeOperation.ADD, 20.0),
            AttributeModifier("speed", AttributeKey.symphony("attack_speed"), AttributeOperation.MULTIPLY_TOTAL, 0.4)
        ),
        setPieces = emptyList()
    )

    @Test
    fun `disabled mode removes the complete offhand source`() {
        assertNull(OffhandSourcePolicy.apply(source, OffhandSettings(OffhandMode.DISABLED, 0.5), null))
    }

    @Test
    fun `full mode keeps all modifiers but an explicit item denial wins`() {
        assertSame(source, OffhandSourcePolicy.apply(source, OffhandSettings(OffhandMode.FULL, 0.25), null))
        assertNull(OffhandSourcePolicy.apply(
            source,
            OffhandSettings(OffhandMode.FULL, 0.25),
            OffhandItemSettings(enabled = false, attributeScale = 1.0)
        ))
    }

    @Test
    fun `scaled mode applies the global ratio to additive and multiplicative deltas`() {
        val scaled = requireNotNull(OffhandSourcePolicy.apply(
            source,
            OffhandSettings(OffhandMode.SCALED, 0.25),
            OffhandItemSettings(enabled = true, attributeScale = 0.75)
        ))

        assertEquals(listOf(5.0, 0.1), scaled.modifiers.map { it.value })
        assertEquals(source.overtureItemId, scaled.overtureItemId)
    }

    @Test
    fun `item controlled mode requires item opt in and uses the item ratio`() {
        val settings = OffhandSettings(OffhandMode.ITEM_CONTROLLED, 0.25)
        assertNull(OffhandSourcePolicy.apply(source, settings, null))
        val scaled = requireNotNull(OffhandSourcePolicy.apply(
            source,
            settings,
            OffhandItemSettings(enabled = true, attributeScale = 0.6)
        ))
        assertEquals(listOf(12.0, 0.24), scaled.modifiers.map { it.value })
    }
}
