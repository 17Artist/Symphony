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
import java.util.Locale
import java.util.concurrent.ConcurrentHashMap

internal object BukkitPotionEffectTypes {
    private val aliases = listOf(
        listOf("SLOW", "SLOWNESS"),
        listOf("FAST_DIGGING", "HASTE"),
        listOf("SLOW_DIGGING", "MINING_FATIGUE"),
        listOf("INCREASE_DAMAGE", "STRENGTH"),
        listOf("HEAL", "INSTANT_HEALTH"),
        listOf("HARM", "INSTANT_DAMAGE"),
        listOf("JUMP", "JUMP_BOOST"),
        listOf("CONFUSION", "NAUSEA"),
        listOf("DAMAGE_RESISTANCE", "RESISTANCE")
    ).flatMap { names -> names.map { name -> name to names.prioritize(name) } }.toMap()
    private val effects = ConcurrentHashMap<String, PotionEffectType>()

    fun effect(raw: String): PotionEffectType = effects.computeIfAbsent(cacheKey(raw)) {
        val normalized = BukkitRegistryTypes.enumName(raw)
        val candidates = aliases[normalized] ?: listOf(normalized)
        BukkitRegistryTypes.resolve(
            PotionEffectType::class.java,
            listOf(raw) + candidates,
            listOf("MOB_EFFECT"),
            candidates
        ) ?: throw IllegalArgumentException("当前版本不存在药水效果 $raw")
    }

    private fun cacheKey(raw: String): String = raw.trim().lowercase(Locale.ROOT)

    private fun List<String>.prioritize(value: String): List<String> = listOf(value) + filterNot(value::equals)
}
