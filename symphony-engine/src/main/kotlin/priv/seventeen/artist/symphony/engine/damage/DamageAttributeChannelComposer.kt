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

package priv.seventeen.artist.symphony.engine.damage

import priv.seventeen.artist.symphony.api.attribute.AttributeKey
import priv.seventeen.artist.symphony.engine.definition.DamageChannelDefinition

data class DamageAttributeChannelInput(
    val channel: String,
    val attribute: AttributeKey,
    val amount: Double
)

/** 根据所有已配置的伤害属性，生成结果稳定的普通攻击输入。 */
fun composeDamageAttributeChannels(
    channels: Collection<DamageChannelDefinition>,
    damageMultiplier: Double = 1.0,
    attributeValue: (AttributeKey) -> Double
): List<DamageAttributeChannelInput> {
    require(damageMultiplier.isFinite() && damageMultiplier > 0.0) {
        "伤害倍率必须是大于零的有限数"
    }
    return channels.asSequence()
        .sortedBy(DamageChannelDefinition::id)
        .mapNotNull { channel ->
            val attribute = channel.damageAttribute ?: return@mapNotNull null
            val raw = attributeValue(attribute)
            require(raw.isFinite()) { "伤害属性 $attribute 必须是有限数" }
            if (raw <= 0.0) return@mapNotNull null
            val amount = raw * damageMultiplier
            require(amount.isFinite()) { "伤害属性 $attribute 应用倍率后发生数值溢出" }
            DamageAttributeChannelInput(channel.id, attribute, amount)
        }
        .toList()
}
