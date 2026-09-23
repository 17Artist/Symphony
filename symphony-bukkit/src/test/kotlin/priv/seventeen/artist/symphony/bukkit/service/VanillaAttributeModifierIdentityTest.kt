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

package priv.seventeen.artist.symphony.bukkit.service

import org.bukkit.attribute.AttributeModifier
import priv.seventeen.artist.symphony.api.attribute.AttributeKey
import java.util.UUID
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class VanillaAttributeModifierIdentityTest {
    private val attackSpeed = AttributeKey.symphony("attack_speed")

    @Test
    fun `recognizes the legacy Bukkit modifier name`() {
        val modifier = modifier(UUID.randomUUID(), VanillaAttributeModifierIdentity.LEGACY_NAME)

        assertEquals(listOf(modifier), VanillaAttributeModifierIdentity.owned(listOf(modifier), attackSpeed))
    }

    @Test
    fun `recognizes the deterministic UUID after Paper rewrites the name`() {
        val id = VanillaAttributeModifierIdentity.id(attackSpeed)
        val modifier = modifier(id, id.toString())

        assertEquals(listOf(modifier), VanillaAttributeModifierIdentity.owned(listOf(modifier), attackSpeed))
    }

    @Test
    fun `rejects modifiers owned by other systems or attributes`() {
        val unrelated = modifier(UUID.randomUUID(), "other.plugin")
        val movementSpeedId = VanillaAttributeModifierIdentity.id(AttributeKey.symphony("movement_speed"))
        val otherSymphonyAttribute = modifier(movementSpeedId, movementSpeedId.toString())

        assertTrue(
            VanillaAttributeModifierIdentity.owned(
                listOf(unrelated, otherSymphonyAttribute),
                attackSpeed
            ).isEmpty()
        )
    }

    @Test
    fun `returns every legacy and modern residue so cleanup is complete`() {
        val id = VanillaAttributeModifierIdentity.id(attackSpeed)
        val legacy = modifier(UUID.randomUUID(), VanillaAttributeModifierIdentity.LEGACY_NAME)
        val modern = modifier(id, id.toString())

        assertEquals(
            listOf(legacy, modern),
            VanillaAttributeModifierIdentity.owned(listOf(legacy, modern), attackSpeed)
        )
    }

    private fun modifier(id: UUID, name: String): AttributeModifier = AttributeModifier(
        id,
        name,
        0.25,
        AttributeModifier.Operation.MULTIPLY_SCALAR_1
    )
}
