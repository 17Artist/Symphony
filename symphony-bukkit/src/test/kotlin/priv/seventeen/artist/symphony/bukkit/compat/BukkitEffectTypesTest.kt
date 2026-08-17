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
    fun `namespaced style sound name normalizes to Bukkit enum`() {
        assertEquals("ENTITY_GENERIC_EXPLODE", BukkitEffectTypes.sound("entity.generic.explode").name)
    }

    @Test
    fun `unknown effects fail with a useful validation message`() {
        assertFailsWith<IllegalArgumentException> { BukkitEffectTypes.particle("not_a_real_particle") }
    }
}
