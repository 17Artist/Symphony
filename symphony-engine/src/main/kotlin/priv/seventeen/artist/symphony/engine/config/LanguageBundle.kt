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

import java.io.InputStream
import java.nio.file.Path


class LanguageBundle private constructor(
    private val strings: Map<String, String>,
    private val lists: Map<String, List<String>>
) {
    fun text(key: String, variables: Map<String, Any?> = emptyMap()): String =
        format(requireNotNull(strings[key]) { "语言文件缺少文本键：$key" }, variables)

    fun text(key: String, vararg variables: Pair<String, Any?>): String = text(key, variables.toMap())

    fun lines(key: String, variables: Map<String, Any?> = emptyMap()): List<String> =
        requireNotNull(lists[key]) { "语言文件缺少文本列表：$key" }.map { format(it, variables) }

    fun optional(key: String, fallback: String, variables: Map<String, Any?> = emptyMap()): String =
        format(strings[key] ?: fallback, variables)

    fun contains(key: String): Boolean = key in strings || key in lists

    fun withFallback(fallback: LanguageBundle): LanguageBundle = LanguageBundle(
        fallback.strings + strings,
        fallback.lists + lists
    )

    private fun format(template: String, variables: Map<String, Any?>): String {
        var result = template.replace('&', '§')
        variables.forEach { (key, value) -> result = result.replace("{$key}", value?.toString().orEmpty()) }
        return result
    }

    companion object {
        fun load(path: Path, yaml: StrictYaml = StrictYaml()): LanguageBundle {
            return fromRoot(path.toString(), yaml.load(path))
        }

        fun load(input: InputStream, source: String, yaml: StrictYaml = StrictYaml()): LanguageBundle {
            return fromRoot(source, yaml.load(input, source))
        }

        private fun fromRoot(source: String, root: Map<String, Any?>): LanguageBundle {
            val strings = linkedMapOf<String, String>()
            val lists = linkedMapOf<String, List<String>>()

            fun visit(prefix: String, value: Any?) {
                when (value) {
                    is String -> strings[prefix] = value
                    is List<*> -> lists[prefix] = value.mapIndexed { index, child ->
                        require(child is String) { "$source.$prefix[$index] 必须是字符串" }
                        child
                    }
                    is Map<*, *> -> value.forEach { (rawKey, child) ->
                        require(rawKey is String && rawKey.isNotBlank()) { "$source.$prefix 包含无效的键" }
                        visit(if (prefix.isEmpty()) rawKey else "$prefix.$rawKey", child)
                    }
                    else -> throw IllegalArgumentException("$source.$prefix 必须是字符串、字符串列表或映射")
                }
            }

            root.forEach { (key, value) -> visit(key, value) }
            require(strings.isNotEmpty()) { "$source 至少要包含一条语言文本" }
            return LanguageBundle(strings.toMap(), lists.mapValues { it.value.toList() })
        }
    }
}
