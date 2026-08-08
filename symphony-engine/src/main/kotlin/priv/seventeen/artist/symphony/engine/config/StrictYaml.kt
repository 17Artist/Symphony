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

package priv.seventeen.artist.symphony.engine.config

import org.snakeyaml.engine.v2.api.Load
import org.snakeyaml.engine.v2.api.LoadSettings
import java.io.ByteArrayInputStream
import java.io.InputStream
import java.nio.file.Files
import java.nio.file.Path

class StrictYaml {
    private val settings = LoadSettings.builder()
        .setAllowDuplicateKeys(false)
        .setMaxAliasesForCollections(50)
        .setCodePointLimit(2_000_000)
        .build()

    fun load(path: Path): Map<String, Any?> {
        Files.newInputStream(path).use { input ->
            return load(input, path.toString())
        }
    }

    fun load(input: InputStream, source: String): Map<String, Any?> {
        val bytes = input.readNBytes((MAX_SOURCE_BYTES + 1L).toInt())
        require(bytes.size <= MAX_SOURCE_BYTES) {
            "$source 超过 YAML 源文件大小上限 $MAX_SOURCE_BYTES 字节"
        }
        val value = Load(settings).loadFromInputStream(ByteArrayInputStream(bytes))
            ?: return emptyMap()
        return stringMap(value, source)
    }

    @Suppress("UNCHECKED_CAST")
    fun stringMap(value: Any?, path: String): Map<String, Any?> {
        require(value is Map<*, *>) { "$path 必须是映射" }
        val result = linkedMapOf<String, Any?>()
        value.forEach { (key, child) ->
            val normalizedKey = keyString(key, path)
            require(result.put(normalizedKey, normalize(child, "$path.$normalizedKey")) == null) {
                "$path 包含重复键：$normalizedKey"
            }
        }
        return result
    }

    private fun normalize(value: Any?, path: String, depth: Int = 0, counter: Counter = Counter()): Any? {
        require(depth <= MAX_DEPTH) { "$path 超过 YAML 最大嵌套深度 $MAX_DEPTH" }
        require(++counter.nodes <= MAX_NODES) { "$path 超过 YAML 最大节点数 $MAX_NODES" }
        return when (value) {
            null, is Boolean, is Number -> value
            is String -> value.also { require(it.length <= MAX_STRING_LENGTH) { "$path 的字符串过长" } }
            is Map<*, *> -> normalizeMap(value, path, depth, counter)
            is List<*> -> {
                require(value.size <= MAX_LIST_LENGTH) { "$path 超过列表最大长度 $MAX_LIST_LENGTH" }
                value.mapIndexed { index, child -> normalize(child, "$path[$index]", depth + 1, counter) }
            }
            else -> throw IllegalArgumentException("$path 包含不支持的 YAML 值类型 ${value::class.qualifiedName}")
        }
    }

    private class Counter(var nodes: Int = 0)

    private fun normalizeMap(value: Map<*, *>, path: String, depth: Int, counter: Counter): Map<String, Any?> {
        val result = linkedMapOf<String, Any?>()
        value.entries.filter { it.key == "<<" }.forEach { (_, mergeValue) ->
            val mergeSources = if (mergeValue is List<*>) mergeValue else listOf(mergeValue)
            mergeSources.forEachIndexed { index, source ->
                require(source is Map<*, *>) { "$path.<<[$index] 必须是映射" }
                val normalized = normalizeMap(source, "$path.<<[$index]", depth + 1, counter)
                normalized.forEach { (key, child) -> result.putIfAbsent(key, child) }
            }
        }
        value.entries.filterNot { it.key == "<<" }.forEach { (rawKey, child) ->
            val key = keyString(rawKey, path)
            result[key] = normalize(child, "$path.$key", depth + 1, counter)
        }
        return result
    }

    private fun keyString(key: Any?, path: String): String = when (key) {
        is String -> key
        is Byte, is Short, is Int, is Long -> (key as Number).toLong().toString()
        else -> throw IllegalArgumentException("$path 包含不支持的映射键：$key")
    }

    companion object {
        const val MAX_SOURCE_BYTES = 2L * 1024L * 1024L
        const val MAX_DEPTH = 32
        const val MAX_NODES = 16_384
        const val MAX_STRING_LENGTH = 32_767
        const val MAX_LIST_LENGTH = 65_536
    }
}

internal class StrictObject(
    private val values: Map<String, Any?>,
    private val path: String
) {
    private val consumed = hashSetOf<String>()

    fun requiredString(key: String): String = string(key, null)
        ?: throw IllegalArgumentException("缺少必填项 $path.$key")

    fun string(key: String, default: String?): String? {
        consumed += key
        val value = values[key] ?: return default
        require(value is String) { "$path.$key 必须是字符串" }
        return value
    }

    fun boolean(key: String, default: Boolean): Boolean {
        consumed += key
        val value = values[key] ?: return default
        require(value is Boolean) { "$path.$key 必须是布尔值" }
        return value
    }

    fun int(key: String, default: Int, range: IntRange = Int.MIN_VALUE..Int.MAX_VALUE): Int {
        val value = long(key, default.toLong())
        require(value in range.first.toLong()..range.last.toLong()) { "$path.$key 必须位于 $range 范围内" }
        return value.toInt()
    }

    fun long(key: String, default: Long, range: LongRange = Long.MIN_VALUE..Long.MAX_VALUE): Long {
        consumed += key
        val value = values[key] ?: return default
        val converted = when (value) {
            is Byte, is Short, is Int, is Long -> (value as Number).toLong()
            else -> throw IllegalArgumentException("$path.$key 必须是整数")
        }
        require(converted in range) { "$path.$key 必须位于 $range 范围内" }
        return converted
    }

    fun double(key: String, default: Double): Double {
        consumed += key
        val value = values[key] ?: return default
        return parseFiniteNumber(value, "$path.$key")
    }

    fun nullableDouble(key: String): Double? {
        consumed += key
        val value = values[key] ?: return null
        return parseFiniteNumber(value, "$path.$key")
    }

    fun numberOrPercent(key: String): Double {
        consumed += key
        val value = values[key] ?: throw IllegalArgumentException("缺少必填项 $path.$key")
        return parseNumberOrPercent(value, "$path.$key")
    }

    fun map(key: String, default: Map<String, Any?> = emptyMap()): Map<String, Any?> {
        consumed += key
        val value = values[key] ?: return default
        return asMap(value, "$path.$key")
    }

    fun list(key: String, default: List<Any?> = emptyList()): List<Any?> {
        consumed += key
        val value = values[key] ?: return default
        require(value is List<*>) { "$path.$key 必须是列表" }
        return value
    }

    fun stringList(key: String, default: List<String> = emptyList()): List<String> =
        list(key, default).mapIndexed { index, value ->
            require(value is String) { "$path.$key[$index] 必须是字符串" }
            value
        }

    fun raw(key: String): Any? {
        consumed += key
        return values[key]
    }

    fun finish(vararg additionallyAllowed: String) {
        val unknown = values.keys - consumed - additionallyAllowed.toSet()
        require(unknown.isEmpty()) { "$path 包含未知字段：${unknown.sorted()}" }
    }

    companion object {
        fun asMap(value: Any?, path: String): Map<String, Any?> {
            require(value is Map<*, *>) { "$path 必须是映射" }
            return value.entries.associateTo(linkedMapOf()) { (key, child) ->
                require(key is String) { "$path 包含非字符串键：$key" }
                key to child
            }
        }

        fun parseFiniteNumber(value: Any?, path: String): Double {
            val result = when (value) {
                is Number -> value.toDouble()
                is String -> value.toDoubleOrNull()
                else -> null
            } ?: throw IllegalArgumentException("$path 必须是数字")
            require(result.isFinite()) { "$path 必须是有限数" }
            return result
        }

        fun parseNumberOrPercent(value: Any?, path: String): Double {
            if (value is String && value.endsWith('%')) {
                return parseFiniteNumber(value.dropLast(1), path) / 100.0
            }
            return parseFiniteNumber(value, path)
        }
    }
}
