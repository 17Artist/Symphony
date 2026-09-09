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
import java.util.concurrent.ConcurrentHashMap

object BukkitEffectTypes {
    private val particleAliases = listOf(
        listOf("EXPLOSION_NORMAL", "POOF"),
        listOf("EXPLOSION_LARGE", "EXPLOSION"),
        listOf("EXPLOSION_HUGE", "EXPLOSION_EMITTER"),
        listOf("FIREWORKS_SPARK", "FIREWORK"),
        listOf("WATER_BUBBLE", "BUBBLE"),
        listOf("WATER_SPLASH", "SPLASH"),
        listOf("WATER_WAKE", "FISHING"),
        listOf("SUSPENDED", "SUSPENDED_DEPTH", "UNDERWATER"),
        listOf("CRIT_MAGIC", "ENCHANTED_HIT"),
        listOf("SMOKE_NORMAL", "SMOKE"),
        listOf("SMOKE_LARGE", "LARGE_SMOKE"),
        listOf("SPELL", "EFFECT"),
        listOf("SPELL_INSTANT", "INSTANT_EFFECT"),
        listOf("SPELL_MOB", "ENTITY_EFFECT"),
        listOf("SPELL_MOB_AMBIENT", "AMBIENT_ENTITY_EFFECT"),
        listOf("SPELL_WITCH", "WITCH"),
        listOf("DRIP_WATER", "DRIPPING_WATER"),
        listOf("DRIP_LAVA", "DRIPPING_LAVA"),
        listOf("VILLAGER_ANGRY", "ANGRY_VILLAGER"),
        listOf("VILLAGER_HAPPY", "HAPPY_VILLAGER"),
        listOf("TOWN_AURA", "MYCELIUM"),
        listOf("ENCHANTMENT_TABLE", "ENCHANT"),
        listOf("TOTEM", "TOTEM_OF_UNDYING"),
        listOf("REDSTONE", "DUST"),
        listOf("SNOWBALL", "SNOW_SHOVEL", "ITEM_SNOWBALL"),
        listOf("SLIME", "ITEM_SLIME"),
        listOf("ITEM_CRACK", "ITEM"),
        listOf("BLOCK_CRACK", "BLOCK_DUST", "LEGACY_BLOCK_CRACK", "LEGACY_BLOCK_DUST", "BLOCK"),
        listOf("WATER_DROP", "RAIN"),
        listOf("MOB_APPEARANCE", "ELDER_GUARDIAN"),
        listOf("LEGACY_FALLING_DUST", "FALLING_DUST")
    ).flatMap { aliases -> aliases.map { alias -> alias to aliases.prioritize(alias) } }.toMap()
    private val particles = ConcurrentHashMap<String, Particle>()
    private val sounds = ConcurrentHashMap<String, Sound>()

    fun particle(raw: String): Particle = particles.computeIfAbsent(cacheKey(raw)) {
        val normalized = BukkitRegistryTypes.enumName(raw)
        val candidates = particleAliases[normalized] ?: listOf(normalized)
        BukkitRegistryTypes.resolve(
            Particle::class.java,
            listOf(raw) + candidates,
            listOf("PARTICLE_TYPE"),
            candidates
        )
            ?: throw IllegalArgumentException("当前版本不存在粒子效果 $raw")
    }

    fun sound(raw: String): Sound = sounds.computeIfAbsent(cacheKey(raw)) {
        val normalized = BukkitRegistryTypes.enumName(raw)
        BukkitRegistryTypes.resolve(
            Sound::class.java,
            listOf(raw),
            listOf("SOUND_EVENT", "SOUNDS"),
            listOf(normalized)
        )
            ?: throw IllegalArgumentException("当前版本不存在声音 $raw")
    }

    private fun cacheKey(raw: String): String = raw.trim().lowercase(Locale.ROOT)

    private fun List<String>.prioritize(value: String): List<String> = listOf(value) + filterNot(value::equals)
}
