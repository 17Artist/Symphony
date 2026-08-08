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

import priv.seventeen.artist.symphony.engine.definition.ArmorFormulaDefinition
import kotlin.math.max

data class ArmorCalculation(
    val rawDamage: Double,
    val effectiveArmor: Double,
    val afterArmor: Double
)

object ArmorFormula {
    fun calculate(
        damage: Double,
        defense: Double,
        percentPenetration: Double,
        flatPenetration: Double,
        definition: ArmorFormulaDefinition
    ): ArmorCalculation {
        require(damage.isFinite() && damage >= 0.0) { "伤害必须是非负有限数" }
        require(defense.isFinite()) { "防御值必须是有限数" }
        require(percentPenetration.isFinite()) { "百分比穿透必须是有限数" }
        require(flatPenetration.isFinite()) { "固定穿透必须是有限数" }
        require(definition.constant.isFinite() && definition.constant > 0.0) {
            "护甲公式常数必须是大于零的有限数"
        }
        val boundedDefense = defense.coerceAtLeast(0.0)
        val boundedPercentPenetration = percentPenetration.coerceIn(0.0, 1.0)
        val boundedFlatPenetration = flatPenetration.coerceAtLeast(0.0)
        val effective = max(
            0.0,
            boundedDefense * (1.0 - boundedPercentPenetration) - boundedFlatPenetration
        )
        val after = if (damage == 0.0) 0.0 else damage * definition.constant / (definition.constant + effective)
        return ArmorCalculation(damage, effective, after)
    }
}
