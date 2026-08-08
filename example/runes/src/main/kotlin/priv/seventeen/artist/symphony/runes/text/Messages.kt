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

package priv.seventeen.artist.symphony.runes.text

import org.bukkit.ChatColor
import org.bukkit.configuration.file.YamlConfiguration
import java.io.File

class Messages(private val file: File) {
    @Volatile private var yaml = YamlConfiguration.loadConfiguration(file)
    fun reload() { yaml = YamlConfiguration.loadConfiguration(file) }
    fun optional(key: String, fallback: String): String = if (yaml.isString(key)) text(key) else fallback
    fun text(key: String, vararg variables: Pair<String, Any?>): String {
        var value = yaml.getString(key) ?: "&c语言配置不完整，请联系服务器管理员。"
        variables.forEach { (name, replacement) -> value = value.replace("{$name}", replacement?.toString().orEmpty()) }
        return ChatColor.translateAlternateColorCodes('&', value)
    }
}
