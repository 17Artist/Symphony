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

package priv.seventeen.artist.symphony.integrations.mythic

import priv.seventeen.artist.symphony.api.attribute.AttributeKey
import priv.seventeen.artist.symphony.api.attribute.AttributeModifier
import priv.seventeen.artist.symphony.api.attribute.AttributeOperation
import priv.seventeen.artist.symphony.engine.attribute.SourceLineParser

internal data class MythicAttributeEntry(
    val attribute: String,
    val operation: String,
    val value: String,
    val perLevel: String = "0",
    val priority: String = "0",
    val description: String? = null
)

internal class MythicAttributeConfiguration(
    private val legacyParser: SourceLineParser = SourceLineParser()
) {
    fun compile(
        enabled: Boolean,
        structured: List<MythicAttributeEntry>,
        legacyLines: List<String>,
        mobLevel: Double
    ): List<AttributeModifier> {
        require(mobLevel.isFinite() && mobLevel >= 0.0) { "MythicMobs 生物等级必须是非负有限数" }
        if (!enabled) return emptyList()
        require(structured.size <= MAX_ATTRIBUTES) {
            "Symphony.Attributes 的属性数量超过上限 $MAX_ATTRIBUTES"
        }
        require(structured.isEmpty() || legacyLines.isEmpty()) {
            "Symphony.Attributes 不能与旧版 SymphonyAttributes 同时使用"
        }
        if (structured.isEmpty()) return legacyParser.parse(legacyLines)

        val duplicateAttributes = structured.groupingBy { normalizeAttribute(it.attribute).value }
            .eachCount()
            .filterValues { it > 1 }
            .keys
        require(duplicateAttributes.isEmpty()) {
            "Symphony.Attributes 包含重复属性：${duplicateAttributes.sorted().joinToString()}"
        }

        val levelOffset = (mobLevel - 1.0).coerceAtLeast(0.0)
        return structured.sortedBy { normalizeAttribute(it.attribute).value }.map { entry ->
            val key = normalizeAttribute(entry.attribute)
            val base = parseNumber(entry.value, "${entry.attribute}.Value")
            val perLevel = parseNumber(entry.perLevel, "${entry.attribute}.PerLevel")
            val value = base + perLevel * levelOffset
            require(value.isFinite()) { "${entry.attribute} 在生物等级 $mobLevel 下产生了非有限数" }
            val priority = entry.priority.toIntOrNull()
                ?: throw IllegalArgumentException("${entry.attribute}.Priority 必须是整数")
            AttributeModifier(
                id = "mythic:${key.value}",
                attribute = key,
                operation = AttributeOperation.parse(entry.operation),
                value = value,
                priority = priority,
                description = entry.description?.takeIf(String::isNotBlank)
            )
        }
    }

    private fun normalizeAttribute(raw: String): AttributeKey {
        val value = raw.trim()
        require(value.isNotEmpty()) { "Symphony 属性 ID 不能为空" }
        return AttributeKey(if (':' in value) value else "symphony:$value")
    }

    private fun parseNumber(raw: String, path: String): Double {
        val value = raw.trim()
        val percent = value.endsWith('%')
        val numeric = (if (percent) value.dropLast(1) else value).toDoubleOrNull()
            ?: throw IllegalArgumentException("$path 必须是数字或百分比")
        require(numeric.isFinite()) { "$path 必须是有限数" }
        return if (percent) numeric / 100.0 else numeric
    }

    companion object {
        const val MAX_ATTRIBUTES: Int = 512
    }
}
