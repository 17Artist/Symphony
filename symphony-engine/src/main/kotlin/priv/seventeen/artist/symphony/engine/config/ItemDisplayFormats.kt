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

class ItemDisplayFormats private constructor(
    private val defaults: Map<String, String>,
    private val displays: Map<String, Map<String, String>>
) {
    fun render(displayId: String?, key: String, vararg variables: Pair<String, Any?>): String {
        val template = displayId?.let { displays[it]?.get(key) } ?: defaults[key]
            ?: throw IllegalArgumentException("缺少物品显示格式：$key")
        var result = template.replace('&', '§')
        variables.forEach { (name, value) -> result = result.replace("{$name}", value?.toString().orEmpty()) }
        return result
    }

    fun has(displayId: String?, key: String): Boolean = displayId?.let { displays[it]?.containsKey(key) } == true || key in defaults

    fun withFallback(fallback: ItemDisplayFormats): ItemDisplayFormats {
        val ids = fallback.displays.keys + displays.keys
        return ItemDisplayFormats(
            fallback.defaults + defaults,
            ids.associateWith { id -> fallback.displays[id].orEmpty() + displays[id].orEmpty() }
        )
    }

    companion object {
        fun load(path: Path, yaml: StrictYaml = StrictYaml()): ItemDisplayFormats = fromRoot(path.toString(), yaml.load(path))

        fun load(input: InputStream, source: String, yaml: StrictYaml = StrictYaml()): ItemDisplayFormats =
            fromRoot(source, yaml.load(input, source))

        private fun fromRoot(source: String, root: Map<String, Any?>): ItemDisplayFormats {
            require((root.keys - setOf("schema", "default", "displays")).isEmpty()) { "$source 包含未知的根字段" }
            require((root["schema"] as? Number)?.toInt() == 1) { "$source.schema 必须等于 1" }
            val defaults = flatten(root["default"], "$source.default")
            require(defaults.isNotEmpty()) { "$source.default 不能为空" }
            val displays = (root["displays"] as? Map<*, *>)?.entries.orEmpty().associate { (rawId, value) ->
                val id = rawId as? String ?: throw IllegalArgumentException("$source.displays 包含非字符串 ID")
                require(id.isNotBlank()) { "$source.displays 包含空白 ID" }
                id to flatten(value, "$source.displays.$id")
            }
            return ItemDisplayFormats(defaults, displays)
        }

        private fun flatten(raw: Any?, path: String): Map<String, String> {
            val result = linkedMapOf<String, String>()
            fun visit(prefix: String, value: Any?) {
                when (value) {
                    is String -> result[prefix] = value
                    is Map<*, *> -> value.forEach { (rawKey, child) ->
                        val key = rawKey as? String ?: throw IllegalArgumentException("$path 包含非字符串键")
                        require(key.isNotBlank()) { "$path 包含空白键" }
                        visit(if (prefix.isEmpty()) key else "$prefix.$key", child)
                    }
                    else -> throw IllegalArgumentException("$path.$prefix 必须是字符串或映射")
                }
            }
            visit("", raw ?: emptyMap<String, Any?>())
            return result
        }
    }
}
