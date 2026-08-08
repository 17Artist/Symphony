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

package priv.seventeen.artist.symphony.engine.attribute

import priv.seventeen.artist.symphony.api.attribute.AttributeKey
import priv.seventeen.artist.symphony.engine.definition.DefinitionRepository
import priv.seventeen.artist.symphony.engine.definition.InteractionType
import kotlin.math.max

class InteractionCalculator(
    private val definitions: DefinitionRepository,
    private val enabled: Boolean = true
) {
    fun apply(target: AttributeKey, standardValue: Double, resolved: Map<String, Double>): Double {
        if (!enabled) return standardValue
        var result = standardValue
        definitions.current().interactionsByTarget[target].orEmpty().forEach { interaction ->
                val source = resolved[interaction.source.value] ?: 0.0
                result = when (interaction.type) {
                    InteractionType.CONVERSION -> result + source * interaction.ratio
                    InteractionType.OVERFLOW -> result + max(0.0, source - interaction.threshold) * interaction.ratio
                    InteractionType.THRESHOLD -> if (source >= interaction.threshold) result + interaction.ratio else result
                    InteractionType.SYNERGY -> result * (1.0 + source * interaction.ratio)
                    InteractionType.CONFLICT -> result * max(0.0, 1.0 - source * interaction.ratio)
                    InteractionType.AMPLIFY -> result * (1.0 + max(0.0, source - interaction.threshold) * interaction.ratio)
                    InteractionType.DIMINISH -> result / (1.0 + max(0.0, source - interaction.threshold) * interaction.ratio)
                }
                require(result.isFinite()) { "属性联动 ${interaction.id} 产生了非有限数" }
        }
        return result
    }
}
