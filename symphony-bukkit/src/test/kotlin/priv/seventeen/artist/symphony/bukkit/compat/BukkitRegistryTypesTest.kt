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

package priv.seventeen.artist.symphony.bukkit.compat

import org.bukkit.block.Biome
import kotlin.test.Test
import kotlin.test.assertEquals

class BukkitRegistryTypesTest {
    @Test
    fun `vanilla attributes resolve on the compile floor`() {
        assertEquals("GENERIC_MAX_HEALTH", BukkitAttributeTypes.maxHealth.name)
        assertEquals("GENERIC_MOVEMENT_SPEED", BukkitAttributeTypes.movementSpeed.name)
        assertEquals("GENERIC_ATTACK_SPEED", BukkitAttributeTypes.attackSpeed.name)
        assertEquals("GENERIC_KNOCKBACK_RESISTANCE", BukkitAttributeTypes.knockbackResistance.name)
    }

    @Test
    fun `registry name does not depend on enum methods`() {
        assertEquals("PLAINS", BukkitRegistryTypes.registryName(Biome.PLAINS))
    }
}
