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

import org.bukkit.attribute.Attribute

internal object BukkitAttributeTypes {
    val maxHealth: Attribute by lazy {
        resolve(
            "最大生命值",
            listOf("max_health", "generic.max_health"),
            listOf("MAX_HEALTH", "GENERIC_MAX_HEALTH")
        )
    }
    val movementSpeed: Attribute by lazy {
        resolve(
            "移动速度",
            listOf("movement_speed", "generic.movement_speed"),
            listOf("MOVEMENT_SPEED", "GENERIC_MOVEMENT_SPEED")
        )
    }
    val attackSpeed: Attribute by lazy {
        resolve(
            "攻击速度",
            listOf("attack_speed", "generic.attack_speed"),
            listOf("ATTACK_SPEED", "GENERIC_ATTACK_SPEED")
        )
    }
    val knockbackResistance: Attribute by lazy {
        resolve(
            "击退抗性",
            listOf("knockback_resistance", "generic.knockback_resistance"),
            listOf("KNOCKBACK_RESISTANCE", "GENERIC_KNOCKBACK_RESISTANCE")
        )
    }

    private fun resolve(displayName: String, keys: List<String>, fields: List<String>): Attribute =
        BukkitRegistryTypes.resolve(Attribute::class.java, keys, listOf("ATTRIBUTE"), fields)
            ?: throw IllegalStateException("当前服务端不存在原版属性：$displayName")
}
