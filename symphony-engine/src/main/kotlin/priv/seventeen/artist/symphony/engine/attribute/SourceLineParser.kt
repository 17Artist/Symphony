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
import priv.seventeen.artist.symphony.api.attribute.AttributeModifier
import priv.seventeen.artist.symphony.api.attribute.AttributeOperation

class SourceLineParser {
    fun parse(lines: List<String>): List<AttributeModifier> {
        require(lines.size <= MAX_LINES) { "属性来源不能超过 $MAX_LINES 行" }
        val result = ArrayList<AttributeModifier>()
        lines.forEachIndexed { index, raw ->
            require(raw.length <= MAX_LINE_LENGTH) { "第 ${index + 1} 行不能超过 $MAX_LINE_LENGTH 个字符" }
            val line = raw.trim()
            if (line.isEmpty() || line.startsWith('#')) return@forEachIndexed
            val parts = line.split(Regex("\\s+"), limit = 3)
            require(parts.size == 3) {
                "第 ${index + 1} 行必须使用 '<命名空间:属性> <运算方式> <数值>' 格式"
            }
            val key = AttributeKey(parts[0])
            val operation = AttributeOperation.parse(parts[1])
            val value = parseNumber(parts[2], index + 1)
            result += AttributeModifier(
                id = "line-${index + 1}",
                attribute = key,
                operation = operation,
                value = value,
                description = "外部属性来源第 ${index + 1} 行"
            )
        }
        return result
    }

    private fun parseNumber(raw: String, lineNumber: Int): Double {
        val percent = raw.endsWith('%')
        val numeric = if (percent) raw.dropLast(1) else raw
        val value = numeric.toDoubleOrNull()
            ?: throw IllegalArgumentException("第 $lineNumber 行包含无效数字：$raw")
        require(value.isFinite()) { "第 $lineNumber 行包含非有限数" }
        return if (percent) value / 100.0 else value
    }

    companion object {
        const val MAX_LINES: Int = 512
        const val MAX_LINE_LENGTH: Int = 512
    }
}
