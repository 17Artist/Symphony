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

package priv.seventeen.artist.symphony.bukkit.persistence

import priv.seventeen.artist.symphony.engine.config.StrictYaml
import java.nio.file.Files
import java.nio.file.Path

internal data class MysqlHealthDatabaseSettings(
    val host: String,
    val port: Int,
    val database: String,
    val username: String,
    val password: String,
    val useSsl: Boolean,
    val allowPublicKeyRetrieval: Boolean,
    val poolSize: Int
)

internal object HealthDatabaseSettingsLoader {
    fun load(path: Path): MysqlHealthDatabaseSettings {
        require(Files.isRegularFile(path)) { "缺少 database.yml" }
        val root = ConfigNode(StrictYaml().load(path), "database")
        require(root.int("schema", 0, 1..1) == 1) { "database.schema 必须为 1" }
        val mysql = ConfigNode(root.map("mysql"), "database.mysql")
        val settings = MysqlHealthDatabaseSettings(
            host = mysql.nonBlankString("host"),
            port = mysql.int("port", 3306, 1..65_535),
            database = mysql.nonBlankString("database"),
            username = mysql.nonBlankString("username"),
            password = mysql.string("password", "")!!,
            useSsl = mysql.boolean("use-ssl", false),
            allowPublicKeyRetrieval = mysql.boolean("allow-public-key-retrieval", true),
            poolSize = mysql.int("pool-size", 4, 2..16)
        )
        mysql.finish()
        root.finish()
        return settings
    }

    private class ConfigNode(
        private val values: Map<String, Any?>,
        private val path: String
    ) {
        private val consumed = hashSetOf<String>()

        fun nonBlankString(key: String): String = string(key, null)
            ?.also { require(it.isNotBlank()) { "$path.$key 不能为空" } }
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

        fun int(key: String, default: Int, range: IntRange): Int {
            consumed += key
            val value = values[key] ?: return default
            require(value is Byte || value is Short || value is Int || value is Long) {
                "$path.$key 必须是整数"
            }
            val converted = (value as Number).toLong()
            require(converted in range.first.toLong()..range.last.toLong()) {
                "$path.$key 必须位于 $range 范围内"
            }
            return converted.toInt()
        }

        fun map(key: String): Map<String, Any?> {
            consumed += key
            val value = values[key] ?: return emptyMap()
            require(value is Map<*, *>) { "$path.$key 必须是映射" }
            return value.entries.associateTo(linkedMapOf()) { (rawKey, child) ->
                require(rawKey is String) { "$path.$key 包含非字符串键：$rawKey" }
                rawKey to child
            }
        }

        fun finish() {
            val unknown = values.keys - consumed
            require(unknown.isEmpty()) { "$path 包含未知字段：${unknown.sorted()}" }
        }
    }
}
