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

package priv.seventeen.artist.symphony.bukkit.gameplay

import priv.seventeen.artist.symphony.api.attribute.AttributeKey
import priv.seventeen.artist.symphony.api.attribute.AttributeModifier
import priv.seventeen.artist.symphony.api.attribute.AttributeOperation

internal fun compileModifiers(
    raw: Any?,
    ownerId: String,
    valueMultiplier: Double = 1.0,
    parameters: Map<String, Double> = emptyMap()
): List<AttributeModifier> {
    val root = raw as? Map<*, *> ?: return emptyList()
    return root.entries.sortedBy { it.key.toString() }.map { (rawAttribute, rawDefinition) ->
        val attribute = namespaced(rawAttribute.toString())
        val node = rawDefinition as? Map<*, *>
            ?: throw IllegalArgumentException("$ownerId.modifiers.$rawAttribute 必须是映射")
        val rawValue = node["value"] ?: throw IllegalArgumentException("缺少必填项 $ownerId.modifiers.$rawAttribute.value")
        AttributeModifier(
            id = "$ownerId:$attribute",
            attribute = AttributeKey(attribute),
            operation = AttributeOperation.parse(node["operation"]?.toString() ?: "add"),
            value = resolveDefinitionNumber(rawValue, parameters) * valueMultiplier,
            priority = (node["priority"] as? Number)?.toInt() ?: 0,
            description = node["description"]?.toString() ?: ownerId
        )
    }
}

internal fun resolveDefinitionNumber(raw: Any?, parameters: Map<String, Double> = emptyMap()): Double = when (raw) {
    is Number -> raw.toDouble()
    is String -> if (raw.startsWith('{') && raw.endsWith('}')) parameters[raw.substring(1, raw.length - 1)]
        else raw.removeSuffix("%").toDoubleOrNull()?.let { if (raw.endsWith('%')) it / 100.0 else it }
    else -> null
}?.also { require(it.isFinite()) { "定义中的数字必须是有限数" } }
    ?: throw IllegalArgumentException("无法解析定义中的数字 $raw")

internal fun namespaced(raw: String): String = if (':' in raw) raw else "symphony:$raw"

internal fun stringMap(raw: Any?, path: String): Map<String, Any?> =
    (raw as? Map<*, *>)?.entries?.associate { it.key.toString() to it.value }
        ?: throw IllegalArgumentException("$path 必须是映射")
