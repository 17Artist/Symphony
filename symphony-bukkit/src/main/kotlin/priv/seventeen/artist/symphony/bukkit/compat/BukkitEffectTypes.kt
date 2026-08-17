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

import org.bukkit.Particle
import org.bukkit.Sound
import java.util.Locale

object BukkitEffectTypes {
    private val particleAliases = mapOf(
        "EXPLOSION_NORMAL" to listOf("EXPLOSION_NORMAL", "POOF"),
        "EXPLOSION_LARGE" to listOf("EXPLOSION_LARGE", "EXPLOSION"),
        "EXPLOSION_HUGE" to listOf("EXPLOSION_HUGE", "EXPLOSION_EMITTER"),
        "SPELL_MOB" to listOf("SPELL_MOB", "ENTITY_EFFECT"),
        "TOTEM" to listOf("TOTEM", "TOTEM_OF_UNDYING"),
        "REDSTONE" to listOf("REDSTONE", "DUST")
    )

    fun particle(raw: String): Particle {
        val normalized = normalize(raw)
        return resolve(Particle::class.java, particleAliases[normalized] ?: listOf(normalized))
            ?: throw IllegalArgumentException("当前版本不存在粒子效果 $raw")
    }

    fun sound(raw: String): Sound {
        val normalized = normalize(raw)
        return resolve(Sound::class.java, listOf(normalized))
            ?: throw IllegalArgumentException("当前版本不存在声音 $raw")
    }

    private fun normalize(raw: String): String = raw.uppercase(Locale.ROOT).replace('.', '_')

    private fun <T : Enum<T>> resolve(type: Class<T>, candidates: List<String>): T? =
        candidates.firstNotNullOfOrNull { candidate -> runCatching { java.lang.Enum.valueOf(type, candidate) }.getOrNull() }
}
