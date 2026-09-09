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

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue

class BukkitEffectTypesTest {
    @Test
    fun `legacy particle vocabulary resolves on the compile floor`() {
        assertEquals("EXPLOSION_NORMAL", BukkitEffectTypes.particle("explosion_normal").name)
        assertTrue(BukkitEffectTypes.particle("totem").name in setOf("TOTEM", "TOTEM_OF_UNDYING"))
    }

    @Test
    fun `modern particle vocabulary falls back to the compile floor alias`() {
        assertEquals("EXPLOSION_NORMAL", BukkitEffectTypes.particle("minecraft:poof").name)
        assertEquals("SPELL_MOB", BukkitEffectTypes.particle("entity_effect").name)
        assertEquals("FIREWORKS_SPARK", BukkitEffectTypes.particle("firework").name)
        assertEquals("WATER_WAKE", BukkitEffectTypes.particle("fishing").name)
        assertEquals("DRIP_WATER", BukkitEffectTypes.particle("dripping_water").name)
        assertEquals("VILLAGER_HAPPY", BukkitEffectTypes.particle("happy_villager").name)
        assertEquals("ENCHANTMENT_TABLE", BukkitEffectTypes.particle("enchant").name)
        assertEquals("ITEM_CRACK", BukkitEffectTypes.particle("item").name)
        assertEquals("BLOCK_CRACK", BukkitEffectTypes.particle("block").name)
        assertEquals("MOB_APPEARANCE", BukkitEffectTypes.particle("elder_guardian").name)
    }

    @Test
    fun `sound accepts resource key and legacy enum vocabulary`() {
        assertEquals("ENTITY_GENERIC_EXPLODE", BukkitEffectTypes.sound("entity.generic.explode").name)
        assertEquals("ENTITY_LIGHTNING_BOLT_IMPACT", BukkitEffectTypes.sound("entity.lightning_bolt.impact").name)
        assertEquals("ENTITY_LIGHTNING_BOLT_IMPACT", BukkitEffectTypes.sound("minecraft:entity.lightning_bolt.impact").name)
        assertEquals("ENTITY_LIGHTNING_BOLT_IMPACT", BukkitEffectTypes.sound("ENTITY_LIGHTNING_BOLT_IMPACT").name)
    }

    @Test
    fun `unknown effects fail with a useful validation message`() {
        assertFailsWith<IllegalArgumentException> { BukkitEffectTypes.particle("not_a_real_particle") }
        assertFailsWith<IllegalArgumentException> { BukkitEffectTypes.sound("not.a.real.sound") }
    }
}
