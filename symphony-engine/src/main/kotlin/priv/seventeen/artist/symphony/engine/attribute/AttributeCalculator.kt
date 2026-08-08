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

import priv.seventeen.artist.symphony.api.attribute.*
import java.math.BigDecimal
import java.math.RoundingMode
import java.util.UUID

fun interface CalculateHook {
    fun calculate(
        entityId: UUID,
        definition: AttributeDefinition,
        standardValue: Double,
        resolved: Map<String, Double>
    ): Double
}

data class CalculationResult(
    val value: Double,
    val explain: AttributeExplain
)

class AttributeCalculator(
    private val calculateHook: CalculateHook = CalculateHook { _, _, standard, _ -> standard }
) {
    fun calculate(
        entityId: UUID,
        definition: AttributeDefinition,
        modifiers: List<Pair<AttributeSourceKey, AttributeModifier>>,
        resolved: Map<String, Double>,
        snapshotRevision: Long,
        dirtyReasons: Set<String>
    ): CalculationResult {
        val active = modifiers.sortedWith(
                compareBy(
                    { operationOrder(it.second.operation) },
                    { it.second.priority },
                    { it.first.namespace },
                    { it.first.value },
                    { it.second.id }
                )
            )

        var running = definition.base
        val contributions = ArrayList<ModifierContribution>(active.size)

        active.forEach { (source, modifier) ->
            val before = running
            running = when (modifier.operation) {
                AttributeOperation.ADD -> running + modifier.value
                AttributeOperation.MULTIPLY_BASE -> running + definition.base * modifier.value
                AttributeOperation.MULTIPLY_TOTAL -> running * (1.0 + modifier.value)
            }
            contributions += ModifierContribution(source, modifier, before, running)
        }

        require(running.isFinite()) { "属性 ${definition.key} 的标准值不是有限数" }
        val calculated = calculateHook.calculate(entityId, definition, running, resolved)
        require(calculated.isFinite()) { "属性 ${definition.key} 的计算回调返回了非有限数" }
        val bounded = definition.bounds.clamp(calculated)
        val finalValue = round(bounded, definition.roundingScale)
        val formatted = format(finalValue, definition)

        return CalculationResult(
            finalValue,
            AttributeExplain(
                key = definition.key,
                base = definition.base,
                contributions = contributions,
                standardValue = running,
                calculatedValue = calculated,
                boundedValue = bounded,
                finalValue = finalValue,
                formatted = formatted,
                snapshotRevision = snapshotRevision,
                dirtyReasons = dirtyReasons.toSet()
            )
        )
    }

    private fun operationOrder(operation: AttributeOperation): Int = when (operation) {
        AttributeOperation.ADD -> 0
        AttributeOperation.MULTIPLY_BASE -> 1
        AttributeOperation.MULTIPLY_TOTAL -> 2
    }

    private fun round(value: Double, scale: Int): Double =
        BigDecimal.valueOf(value).setScale(scale, RoundingMode.HALF_UP).toDouble()

    private fun format(value: Double, definition: AttributeDefinition): String = when (definition.format) {
        AttributeFormat.INTEGER ->
            BigDecimal.valueOf(value).setScale(0, RoundingMode.HALF_UP).toPlainString()
        AttributeFormat.PERCENT ->
            BigDecimal.valueOf(value * 100.0).setScale(definition.roundingScale, RoundingMode.HALF_UP)
                .stripTrailingZeros().toPlainString() + "%"
        AttributeFormat.NUMBER ->
            BigDecimal.valueOf(value).setScale(definition.roundingScale, RoundingMode.HALF_UP)
                .stripTrailingZeros().toPlainString()
    }
}
