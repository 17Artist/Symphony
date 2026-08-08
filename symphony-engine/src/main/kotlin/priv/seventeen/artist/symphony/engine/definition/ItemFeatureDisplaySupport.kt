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

package priv.seventeen.artist.symphony.engine.definition

import java.math.BigDecimal
import java.math.RoundingMode

enum class SkillActivationInput(val id: String) {
    RIGHT_CLICK("right_click"),
    SNEAK_RIGHT_CLICK("sneak_right_click");

    companion object {
        fun parse(raw: String): SkillActivationInput = values().firstOrNull { it.id == raw }
            ?: throw IllegalArgumentException("不支持的技能激活输入方式：$raw")
    }
}

enum class SkillActivationSource(val id: String) {
    MAIN_HAND("main_hand"),
    OFF_HAND("off_hand"),
    ANY("any");

    companion object {
        fun parse(raw: String): SkillActivationSource = values().firstOrNull { it.id == raw }
            ?: throw IllegalArgumentException("不支持的技能激活来源：$raw")
    }
}

enum class SkillCancelPolicy(val id: String) {
    NEVER("never"),
    ON_SUCCESS("on_success"),
    ALWAYS("always");

    companion object {
        fun parse(raw: String): SkillCancelPolicy = values().firstOrNull { it.id == raw }
            ?: throw IllegalArgumentException("不支持的技能取消策略：$raw")
    }
}

data class SkillActivationDefinition(
    val input: SkillActivationInput,
    val source: SkillActivationSource,
    val cancelPolicy: SkillCancelPolicy,
    val priority: Int
)

fun GenericDefinition.skillActivation(): SkillActivationDefinition? {
    val raw = values["activation"] as? Map<*, *> ?: return null
    if (raw.isEmpty()) return null
    return SkillActivationDefinition(
        input = SkillActivationInput.parse(raw["input"]?.toString() ?: "right_click"),
        source = SkillActivationSource.parse(raw["source"]?.toString() ?: "main_hand"),
        cancelPolicy = SkillCancelPolicy.parse(raw["cancel-event"]?.toString() ?: "on_success"),
        priority = (raw["priority"] as? Number)?.toInt() ?: 0
    )
}

/** 根据词条等级生成用户配置的词条实例说明。 */
fun GenericDefinition.affixDescription(level: Int, parameters: Map<String, Double>): List<String> {
    val display = values["display"] as? Map<*, *> ?: return emptyList()
    val description = display["description"] as? List<*> ?: return emptyList()
    val variables = parameters + ("level" to level.toDouble())
    return description.mapNotNull { it?.toString() }
        .map { template ->
            AFFIX_PLACEHOLDER.replace(template) { match ->
                val value = variables[match.groupValues[1]] ?: return@replace match.value
                formatFeatureNumber(value, match.groupValues[2].ifBlank { "number" })
            }
        }
}

fun EnhancementDefinition.multiplierAt(level: Int): Double =
    levels.filterKeys { it <= level }.maxByOrNull { it.key }?.value?.multiplier ?: 1.0

private fun formatFeatureNumber(value: Double, format: String): String = when (format) {
    "percent" -> decimal(value * 100.0, 4) + "%"
    "integer" -> BigDecimal.valueOf(value).setScale(0, RoundingMode.HALF_UP).toPlainString()
    else -> decimal(value, 6)
}

private fun decimal(value: Double, scale: Int): String = BigDecimal.valueOf(value)
    .setScale(scale, RoundingMode.HALF_UP)
    .stripTrailingZeros()
    .toPlainString()

internal val AFFIX_PLACEHOLDER = Regex("\\{([a-zA-Z0-9_.-]+)(?:\\|(number|percent|integer))?}")
