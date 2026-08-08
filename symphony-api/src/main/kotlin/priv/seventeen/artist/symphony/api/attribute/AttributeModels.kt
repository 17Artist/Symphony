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

import java.util.UUID

data class AttributeKey(val value: String) : Comparable<AttributeKey> {
    init {
        require(PATTERN.matches(value)) { "属性键不是有效的命名空间格式：$value" }
        require(value.length <= MAX_LENGTH) { "属性键不能超过 $MAX_LENGTH 个字符" }
    }

    override fun compareTo(other: AttributeKey): Int = value.compareTo(other.value)
    override fun toString(): String = value

    companion object {
        const val MAX_LENGTH: Int = 128
        private val PATTERN = Regex("^[a-z0-9._-]+:[a-z0-9._/-]+$")

        @JvmStatic
        fun symphony(id: String): AttributeKey = AttributeKey("symphony:$id")
    }
}

data class AttributeSourceKey(
    val namespace: String,
    val value: String
) : Comparable<AttributeSourceKey> {
    init {
        require(NAMESPACE.matches(namespace)) { "属性来源命名空间无效：$namespace" }
        require(value.isNotBlank()) { "属性来源值不能为空" }
        require(value.length <= MAX_VALUE_LENGTH) { "属性来源值不能超过 $MAX_VALUE_LENGTH 个字符" }
        require(value.none { it == '\u0000' || it == '\r' || it == '\n' }) {
            "属性来源值包含禁止使用的控制字符"
        }
    }

    override fun compareTo(other: AttributeSourceKey): Int =
        compareValuesBy(this, other, AttributeSourceKey::namespace, AttributeSourceKey::value)

    override fun toString(): String = "$namespace:$value"

    companion object {
        const val MAX_VALUE_LENGTH: Int = 256
        private val NAMESPACE = Regex("^[a-z0-9._-]+$")
    }
}

enum class AttributeOperation {
    ADD,
    MULTIPLY_BASE,
    MULTIPLY_TOTAL;

    companion object {
        @JvmStatic
        fun parse(value: String): AttributeOperation = when (value.lowercase()) {
            "add" -> ADD
            "multiply_base", "multiply-base" -> MULTIPLY_BASE
            "multiply_total", "multiply-total" -> MULTIPLY_TOTAL
            else -> throw IllegalArgumentException("未知的属性运算方式：$value")
        }
    }
}

data class AttributeModifier(
    val id: String,
    val attribute: AttributeKey,
    val operation: AttributeOperation,
    val value: Double,
    val priority: Int = 0,
    val persistent: Boolean = false,
    val expiresAtMillis: Long? = null,
    val description: String? = null
) {
    init {
        require(id.isNotBlank()) { "属性修改器 ID 不能为空" }
        require(id.length <= 256) { "属性修改器 ID 不能超过 256 个字符" }
        require(value.isFinite()) { "属性修改器数值必须是有限数" }
        require(expiresAtMillis == null || expiresAtMillis >= 0L) { "expiresAtMillis 不能为负数" }
    }

    fun isExpired(nowMillis: Long): Boolean = expiresAtMillis?.let { it <= nowMillis } == true
}

enum class AttributeFormat {
    NUMBER,
    INTEGER,
    PERCENT
}

data class AttributeBounds(
    val min: Double? = null,
    val max: Double? = null
) {
    init {
        require(min == null || min.isFinite()) { "属性下限必须是有限数" }
        require(max == null || max.isFinite()) { "属性上限必须是有限数" }
        require(min == null || max == null || min <= max) { "属性下限不能大于上限" }
    }

    fun clamp(value: Double): Double = when {
        min != null && value < min -> min
        max != null && value > max -> max
        else -> value
    }
}

data class AttributeDefinition(
    val key: AttributeKey,
    val name: String,
    val description: String,
    val category: String,
    val base: Double,
    val bounds: AttributeBounds = AttributeBounds(),
    val format: AttributeFormat = AttributeFormat.NUMBER,
    val roundingScale: Int = 2,
    val priority: Int = 0,
    val dependsOn: Set<AttributeKey> = emptySet()
) {
    init {
        require(name.isNotBlank()) { "属性名称不能为空" }
        require(base.isFinite()) { "属性基础值必须是有限数" }
        require(roundingScale in 0..12) { "roundingScale 必须位于 0 到 12 之间" }
    }
}

data class AttributeValue(
    val key: AttributeKey,
    val value: Double,
    val formatted: String
)

data class AttributeSnapshot(
    val entityId: UUID,
    val revision: Long,
    val definitionRevision: Long,
    val values: Map<AttributeKey, Double>,
    val dirtyReasons: Set<String>,
    val committedAtMillis: Long
) {
    operator fun get(key: AttributeKey): Double? = values[key]
}

data class ModifierContribution(
    val source: AttributeSourceKey,
    val modifier: AttributeModifier,
    val valueBefore: Double,
    val valueAfter: Double
)

data class AttributeExplain(
    val key: AttributeKey,
    val base: Double,
    val contributions: List<ModifierContribution>,
    val standardValue: Double,
    val calculatedValue: Double,
    val boundedValue: Double,
    val finalValue: Double,
    val formatted: String,
    val snapshotRevision: Long,
    val dirtyReasons: Set<String>
)
