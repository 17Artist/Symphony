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

package priv.seventeen.artist.symphony.damagewatch.text

import org.bukkit.ChatColor
import org.bukkit.configuration.file.YamlConfiguration
import java.io.File

class Messages(file: File) {
    private val yaml = YamlConfiguration.loadConfiguration(file)

    fun text(key: String, vararg variables: Pair<String, Any?>): String = render(
        yaml.getString(key) ?: "&c缺少语言项：$key",
        variables
    )

    fun optional(key: String, fallback: String): String = if (yaml.isString(key)) text(key) else fallback

    private fun render(template: String, variables: Array<out Pair<String, Any?>>): String {
        var rendered = template
        variables.forEach { (name, value) -> rendered = rendered.replace("{$name}", value?.toString().orEmpty()) }
        return ChatColor.translateAlternateColorCodes('&', rendered)
    }
}
