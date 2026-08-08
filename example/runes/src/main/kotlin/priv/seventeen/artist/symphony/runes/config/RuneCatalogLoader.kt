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

package priv.seventeen.artist.symphony.runes.config

import org.bukkit.configuration.ConfigurationSection
import org.bukkit.configuration.file.YamlConfiguration
import org.bukkit.plugin.java.JavaPlugin
import priv.seventeen.artist.symphony.api.attribute.AttributeBounds
import priv.seventeen.artist.symphony.api.attribute.AttributeDefinition
import priv.seventeen.artist.symphony.api.attribute.AttributeFormat
import priv.seventeen.artist.symphony.api.attribute.AttributeKey
import priv.seventeen.artist.symphony.api.attribute.AttributeOperation
import priv.seventeen.artist.symphony.runes.model.RuneCatalog
import priv.seventeen.artist.symphony.runes.model.RuneDefinition
import priv.seventeen.artist.symphony.runes.model.RuneModifierDefinition
import priv.seventeen.artist.symphony.runes.model.RuneSlotDefinition
import priv.seventeen.artist.symphony.runes.model.ScaledLevel
import priv.seventeen.artist.symphony.runes.model.ScaledValue

class RuneCatalogLoader(private val plugin: JavaPlugin) {
    private val ownerNamespace = plugin.name.lowercase().replace(Regex("[^a-z0-9._-]"), "_")

    fun load(): RuneCatalog {
        val config = YamlConfiguration.loadConfiguration(plugin.dataFolder.resolve("config.yml"))
        val slots = parseSlots(requireNotNull(config.getConfigurationSection("slots")) { "config.yml 缺少 slots" })
        val attributes = parseAttributes(YamlConfiguration.loadConfiguration(plugin.dataFolder.resolve("attributes.yml")))
        val runeDirectory = plugin.dataFolder.resolve("runes")
        val runes = runeDirectory.listFiles { file -> file.isFile && file.extension.equals("yml", true) }.orEmpty()
            .sortedBy { it.name }
            .associate { file ->
                val rune = parseRune(YamlConfiguration.loadConfiguration(file), file.name)
                rune.id to rune
            }
        require(runes.isNotEmpty()) { "未找到符文定义" }
        require(runes.size == runeDirectory.listFiles { file -> file.isFile && file.extension.equals("yml", true) }.orEmpty().size) {
            "符文 ID 重复"
        }
        return RuneCatalog(config.getInt("definition-priority", 100), slots, runes, attributes)
    }

    private fun parseSlots(section: ConfigurationSection): Map<String, RuneSlotDefinition> = section.getKeys(false)
        .sorted().associateWith { id ->
            require(id.matches(Regex("^[a-z0-9_-]{1,32}$"))) { "槽位 ID 无效：$id" }
            val path = id
            val displayName = requireNotNull(section.getString("$path.display-name")) { "槽位 $id 缺少 display-name" }.trim()
            val accepted = section.getStringList("$path.accepts").map(String::trim).filter(String::isNotEmpty).toSet()
            RuneSlotDefinition(id, displayName, accepted)
        }

    private fun parseAttributes(yaml: YamlConfiguration): Map<AttributeKey, AttributeDefinition> {
        val section = yaml.getConfigurationSection("attributes") ?: return emptyMap()
        return section.getKeys(false).sorted().associate { id ->
            val key = AttributeKey(if (':' in id) id else "$ownerNamespace:$id")
            require(key.value.substringBefore(':') == ownerNamespace) {
                "外部属性 ${key.value} 必须使用所有者命名空间 $ownerNamespace"
            }
            val path = id
            val definition = AttributeDefinition(
                key = key,
                name = requireNotNull(section.getString("$path.name")).trim(),
                description = section.getString("$path.description", "")!!,
                category = section.getString("$path.category", "rune")!!,
                base = section.getDouble("$path.base", 0.0),
                bounds = AttributeBounds(
                    section.get("$path.bounds.min")?.asDouble("$path.bounds.min"),
                    section.get("$path.bounds.max")?.asDouble("$path.bounds.max")
                ),
                format = when (section.getString("$path.format", "number")!!.lowercase()) {
                    "number" -> AttributeFormat.NUMBER
                    "integer" -> AttributeFormat.INTEGER
                    "percent" -> AttributeFormat.PERCENT
                    else -> error("$id 使用了未知的显示格式")
                },
                roundingScale = section.getInt("$path.rounding-scale", 2),
                priority = section.getInt("$path.priority", 0),
                dependsOn = section.getStringList("$path.depends-on").map(::attributeKey).toSet()
            )
            key to definition
        }
    }

    private fun parseRune(yaml: YamlConfiguration, source: String): RuneDefinition {
        val id = requireNotNull(yaml.getString("id")) { "$source 缺少 id" }.trim()
        val modifiers = yaml.getMapList("modifiers").mapIndexed { index, map ->
            val modifierId = map["id"]?.toString()?.trim().orEmpty()
            require(modifierId.matches(Regex("^[a-z0-9._/-]{1,64}$"))) { "$source modifiers[$index] 的 id 无效" }
            val attribute = attributeKey(map["attribute"]?.toString().orEmpty())
            val operation = AttributeOperation.parse(map["operation"]?.toString() ?: "add")
            val valueMap = map["value"] as? Map<*, *> ?: error("$source modifiers[$index].value 必须是配置块")
            val base = valueMap["base"].asDouble("$source modifiers[$index].value.base")
            val perRank = valueMap["per-rank"]?.asDouble("$source modifiers[$index].value.per-rank") ?: 0.0
            val priority = (map["priority"] as? Number)?.toInt() ?: 0
            RuneModifierDefinition(modifierId, attribute, operation, ScaledValue(base, perRank), priority)
        }
        return RuneDefinition(
            id = id,
            displayName = requireNotNull(yaml.getString("display.name")) { "$source 缺少 display.name" }.trim(),
            description = yaml.getStringList("display.description"),
            category = requireNotNull(yaml.getString("category")) { "$source 缺少 category" }.trim(),
            maximumRank = yaml.getInt("maximum-rank", 1),
            minimumLevel = ScaledLevel(
                yaml.getInt("minimum-level.base", 0),
                yaml.getInt("minimum-level.per-rank", 0)
            ),
            modifiers = modifiers
        )
    }

    private fun attributeKey(raw: String): AttributeKey {
        val value = raw.trim()
        require(value.isNotEmpty()) { "属性键不能为空" }
        return AttributeKey(if (':' in value) value else "symphony:$value")
    }

    private fun Any?.asDouble(path: String): Double = when (this) {
        is Number -> toDouble()
        is String -> toDoubleOrNull()
        else -> null
    }?.also { require(it.isFinite()) { "$path 必须是有限数" } } ?: error("$path 必须是数字")
}
