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

package priv.seventeen.artist.symphony.api.attribute

import org.bukkit.NamespacedKey
import org.bukkit.entity.LivingEntity
import org.bukkit.plugin.Plugin
import priv.seventeen.artist.symphony.api.registration.RegistrationHandle

data class AttributeProviderContext(
    val nowMillis: Long,
    val definitionRevision: Long
)

interface AttributeProvider {
    val id: NamespacedKey

    /** 必须返回新列表或不可变列表；访问 Bukkit API 时必须处于合法的实体线程。 */
    fun modifiers(entity: LivingEntity, context: AttributeProviderContext): List<AttributeModifier>
}

interface AttributeService {
    fun value(entity: LivingEntity, key: AttributeKey): Double
    fun snapshot(entity: LivingEntity): AttributeSnapshot
    fun explain(entity: LivingEntity, key: AttributeKey): AttributeExplain?
    fun invalidate(entity: LivingEntity, reason: String, affected: Set<AttributeKey> = emptySet())
    fun recalculate(entity: LivingEntity): AttributeSnapshot

    fun registerProvider(
        owner: Plugin,
        provider: AttributeProvider,
        priority: Int = 0
    ): RegistrationHandle
}
