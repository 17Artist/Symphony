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

import org.bukkit.potion.PotionEffectType
import kotlin.test.Test
import kotlin.test.assertFailsWith
import kotlin.test.assertSame

class BukkitPotionEffectTypesTest {
    @Test
    fun `modern effect names fall back to legacy compile floor names`() {
        assertSame(PotionEffectType.SLOW, BukkitPotionEffectTypes.effect("slowness"))
        assertSame(PotionEffectType.FAST_DIGGING, BukkitPotionEffectTypes.effect("minecraft:haste"))
        assertSame(PotionEffectType.DAMAGE_RESISTANCE, BukkitPotionEffectTypes.effect("resistance"))
    }

    @Test
    fun `legacy effect names remain available`() {
        assertSame(PotionEffectType.SLOW, BukkitPotionEffectTypes.effect("SLOW"))
        assertSame(PotionEffectType.HEAL, BukkitPotionEffectTypes.effect("heal"))
    }

    @Test
    fun `unknown effect fails with a useful validation message`() {
        assertFailsWith<IllegalArgumentException> { BukkitPotionEffectTypes.effect("not_a_real_effect") }
    }
}
