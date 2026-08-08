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

package priv.seventeen.artist.symphony.engine.damage

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue
import priv.seventeen.artist.symphony.api.attribute.AttributeKey
import priv.seventeen.artist.symphony.engine.definition.DamageChannelDefinition

class DamageAttributeChannelComposerTest {
    @Test
    fun `ordinary attacks include every positive configured damage attribute in stable order`() {
        val values = mapOf(
            AttributeKey.symphony("physical_damage") to 32.0,
            AttributeKey.symphony("fire_damage") to 12.0,
            AttributeKey.symphony("ice_damage") to 12.0,
            AttributeKey.symphony("lightning_damage") to 12.0,
            AttributeKey.symphony("arcane_damage") to 0.0
        )
        val inputs = composeDamageAttributeChannels(
            listOf(
                channel("lightning"),
                channel("physical"),
                channel("arcane"),
                channel("ice"),
                channel("fire"),
                channel("true", null)
            )
        ) { values.getValue(it) }

        assertEquals(listOf("fire", "ice", "lightning", "physical"), inputs.map { it.channel })
        assertEquals(listOf(12.0, 12.0, 12.0, 32.0), inputs.map { it.amount })
    }

    @Test
    fun `invalid attribute values fail before a Bukkit damage event is mutated`() {
        assertFailsWith<IllegalArgumentException> {
            composeDamageAttributeChannels(listOf(channel("fire"))) { Double.NaN }
        }
    }

    @Test
    fun `ordinary attack multiplier scales every positive channel before combat formulas`() {
        val values = mapOf(
            AttributeKey.symphony("physical_damage") to 20.0,
            AttributeKey.symphony("fire_damage") to 7.5
        )

        val inputs = composeDamageAttributeChannels(
            listOf(channel("physical"), channel("fire")),
            damageMultiplier = 1.6
        ) { values.getValue(it) }

        assertEquals(listOf("fire", "physical"), inputs.map { it.channel })
        assertEquals(listOf(12.0, 32.0), inputs.map { it.amount })
    }

    @Test
    fun `ordinary attack multiplier rejects zero negative non finite and overflowing results`() {
        listOf(0.0, -1.0, Double.NaN, Double.POSITIVE_INFINITY).forEach { multiplier ->
            assertFailsWith<IllegalArgumentException> {
                composeDamageAttributeChannels(listOf(channel("physical")), multiplier) { 10.0 }
            }
        }

        val overflow = assertFailsWith<IllegalArgumentException> {
            composeDamageAttributeChannels(listOf(channel("physical")), 2.0) { Double.MAX_VALUE }
        }
        assertTrue(overflow.message.orEmpty().contains("数值溢出"))
    }

    private fun channel(id: String, attribute: AttributeKey? = AttributeKey.symphony("${id}_damage")) =
        DamageChannelDefinition(id, id, attribute, null, null, null, true, id != "physical", null)
}
