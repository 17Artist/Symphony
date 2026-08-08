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

data class ElementalDamageResult(
    val input: Double,
    val resistance: Double,
    val amplification: Double,
    val resistanceMultiplier: Double,
    val amplificationMultiplier: Double,
    val output: Double
)

/**
 * 用于元素伤害和其它非护甲通道的纯伤害减免计算。
 *
 * 元素克制完全由数据驱动：元素反应先改变 [input]，随后在此应用防守方抗性与攻击方增幅。
 */
object ElementalDamageFormula {
    fun calculate(input: Double, resistance: Double, amplification: Double): ElementalDamageResult {
        require(input.isFinite() && input >= 0.0) { "元素伤害输入必须是非负有限数" }
        require(resistance.isFinite()) { "元素抗性必须是有限数" }
        require(amplification.isFinite()) { "元素伤害增幅必须是有限数" }

        val boundedResistance = resistance.coerceIn(0.0, 1.0)
        val boundedAmplification = amplification.coerceAtLeast(-1.0)
        val resistanceMultiplier = 1.0 - boundedResistance
        val amplificationMultiplier = 1.0 + boundedAmplification
        val output = input * resistanceMultiplier * amplificationMultiplier
        require(output.isFinite() && output >= 0.0) { "元素伤害输出必须是非负有限数" }
        return ElementalDamageResult(
            input,
            boundedResistance,
            boundedAmplification,
            resistanceMultiplier,
            amplificationMultiplier,
            output
        )
    }
}
