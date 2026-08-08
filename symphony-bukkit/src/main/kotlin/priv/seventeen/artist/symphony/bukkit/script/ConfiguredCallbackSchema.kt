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

package priv.seventeen.artist.symphony.bukkit.script

import org.bukkit.potion.PotionEffectType
import priv.seventeen.artist.symphony.api.attribute.AttributeOperation
import priv.seventeen.artist.symphony.bukkit.compat.BukkitEffectTypes
import java.util.Locale

/** 供运行时重载与公开配置包校验器共同使用的结构校验。 */
internal object ConfiguredCallbackSchema {
    fun validateConditions(ownerId: String, conditions: List<Map<String, Any?>>) {
        conditions.forEachIndexed { index, condition -> validateCondition(condition, "$ownerId.conditions[$index]") }
    }

    fun validateActions(ownerId: String, actions: List<Map<String, Any?>>) {
        actions.forEachIndexed { index, action -> validateAction(action, "$ownerId.actions[$index]") }
    }

    private fun validateCondition(condition: Map<String, Any?>, path: String) {
        val type = requiredString(condition, "type", path)
        val allowed = when (type) {
            "chance" -> setOf("type", "value")
            "cooldown" -> setOf("type", "key", "duration-ms")
            "health" -> setOf("type", "target", "operator", "value", "percent")
            "level" -> setOf("type", "target", "operator", "value")
            "world" -> setOf("type", "names")
            "biome" -> setOf("type", "names", "target")
            "permission" -> setOf("type", "permission", "target")
            "attribute" -> setOf("type", "attribute", "operator", "value", "target")
            "target_type" -> setOf("type", "entity-types")
            "posture", "equipment" -> setOf("type", "value", "slot", "item", "tag")
            "and", "or" -> setOf("type", "conditions")
            "not" -> setOf("type", "condition")
            "aria" -> throw IllegalArgumentException("$path：Aria 条件请通过 callback.script 返回 true 或 false，避免在高频路径中重复编译")
            else -> throw IllegalArgumentException("$path.type 未知: $type")
        }
        rejectUnknown(condition, allowed, path)
        when (type) {
            "chance" -> {
                requireNumeric(condition["value"], "$path.value")
                literalDouble(condition["value"])?.let { require(it in 0.0..1.0) { "$path.value 必须在 0 到 1 之间" } }
            }
            "cooldown" -> {
                requiredString(condition, "key", path)
                requirePositiveLong(condition["duration-ms"], "$path.duration-ms")
            }
            "health" -> {
                validateTarget(condition, path)
                validateOperator(condition, path)
                requireNumeric(condition["value"], "$path.value")
                require(condition["percent"] == null || condition["percent"] is Boolean) { "$path.percent 必须是布尔值" }
            }
            "level" -> {
                validateTarget(condition, path)
                validateOperator(condition, path)
                requireNumeric(condition["value"], "$path.value")
            }
            "world" -> requireStringCollection(condition["names"], "$path.names")
            "biome" -> {
                validateTarget(condition, path)
                requireStringCollection(condition["names"], "$path.names")
            }
            "permission" -> {
                validateTarget(condition, path)
                requiredString(condition, "permission", path)
            }
            "attribute" -> {
                validateTarget(condition, path)
                validateOperator(condition, path)
                requiredString(condition, "attribute", path)
                requireNumeric(condition["value"], "$path.value")
            }
            "target_type" -> requireStringCollection(condition["entity-types"], "$path.entity-types")
            "posture" -> requiredString(condition, "value", path)
            "equipment" -> {
                requiredString(condition, "slot", path)
                require(
                    listOf("item", "tag").count { condition[it] is String && condition[it].toString().isNotBlank() } == 1
                ) { "$path 必须且只能定义 item 或 tag" }
            }
            "and", "or" -> listOfMaps(condition["conditions"], "$path.conditions")
                .forEachIndexed { index, nested -> validateCondition(nested, "$path.conditions[$index]") }
            "not" -> validateCondition(stringMap(condition["condition"], "$path.condition"), "$path.condition")
        }
    }

    private fun validateAction(action: Map<String, Any?>, path: String) {
        val type = requiredString(action, "type", path)
        val allowed = when (type) {
            "damage" -> setOf("type", "channel", "amount", "amount-per-stack", "target", "allow-critical")
            "heal" -> setOf("type", "amount", "amount-per-stack", "target")
            "attribute_buff" -> setOf("type", "attribute", "operation", "value", "duration-ms", "target", "priority")
            "permanent_modifier" -> setOf("type", "attribute", "operation", "value", "target", "priority", "key")
            "skill" -> setOf("type", "skill", "target")
            "potion" -> setOf("type", "effect", "duration-ticks", "amplifier", "ambient", "particles", "icon", "target")
            "particle" -> setOf("type", "particle", "count", "offset-x", "offset-y", "offset-z", "extra", "target")
            "sound" -> setOf("type", "sound", "volume", "pitch", "target")
            "message" -> setOf("type", "message", "target")
            "command" -> setOf("type", "command", "as")
            "shield" -> setOf("type", "amount", "mode", "target")
            "status" -> setOf("type", "status", "stacks", "duration-ms", "target")
            "script" -> throw IllegalArgumentException("$path：脚本动作请直接使用 callback.script 或 callback.file，以便在重载时预编译")
            else -> throw IllegalArgumentException("$path.type 未知: $type")
        }
        rejectUnknown(action, allowed, path)
        when (type) {
            "damage" -> {
                requiredString(action, "channel", path)
                require(action.containsKey("amount") || action.containsKey("amount-per-stack")) { "$path 缺少 amount 字段" }
                action["amount"]?.let { requireNumeric(it, "$path.amount") }
                action["amount-per-stack"]?.let { requireNumeric(it, "$path.amount-per-stack") }
                require(action["allow-critical"] == null || action["allow-critical"] is Boolean) {
                    "$path.allow-critical 必须是布尔值"
                }
                validateTarget(action, path)
            }
            "heal" -> {
                require(action.containsKey("amount") || action.containsKey("amount-per-stack")) { "$path 缺少 amount 字段" }
                action["amount"]?.let { requireNumeric(it, "$path.amount") }
                action["amount-per-stack"]?.let { requireNumeric(it, "$path.amount-per-stack") }
                validateTarget(action, path)
            }
            "attribute_buff", "permanent_modifier" -> {
                requiredString(action, "attribute", path)
                requireNumeric(action["value"], "$path.value")
                AttributeOperation.parse(action["operation"]?.toString() ?: "add")
                validateTarget(action, path)
                action["priority"]?.let { requireInteger(it, "$path.priority") }
                if (type == "attribute_buff") {
                    requirePositiveNumeric(action["duration-ms"], "$path.duration-ms")
                }
            }
            "skill" -> {
                requiredString(action, "skill", path)
                validateTarget(action, path)
            }
            "potion" -> {
                val effect = requiredString(action, "effect", path)
                @Suppress("DEPRECATION")
                require(PotionEffectType.getByName(effect.uppercase(Locale.ROOT)) != null) { "$path.effect 未知: $effect" }
                action["duration-ticks"]?.let { requirePositiveNumeric(it, "$path.duration-ticks") }
                action["amplifier"]?.let { requireNonNegativeInteger(it, "$path.amplifier") }
                listOf("ambient", "particles", "icon").forEach { key ->
                    require(action[key] == null || action[key] is Boolean) { "$path.$key 必须是布尔值" }
                }
                validateTarget(action, path)
            }
            "particle" -> {
                BukkitEffectTypes.particle(requiredString(action, "particle", path))
                listOf("count", "offset-x", "offset-y", "offset-z", "extra").forEach { key ->
                    action[key]?.let { requireNumeric(it, "$path.$key") }
                }
                validateTarget(action, path)
            }
            "sound" -> {
                BukkitEffectTypes.sound(requiredString(action, "sound", path))
                listOf("volume", "pitch").forEach { key -> action[key]?.let { requireNumeric(it, "$path.$key") } }
                validateTarget(action, path)
            }
            "message" -> {
                requiredString(action, "message", path)
                validateTarget(action, path)
            }
            "command" -> {
                requiredString(action, "command", path)
                require(action["as"] == null || action["as"]?.toString() in setOf("console", "self")) {
                    "$path.as 必须是 console 或 self"
                }
            }
            "shield" -> {
                requireNumeric(action["amount"], "$path.amount")
                require(action["mode"] == null || action["mode"]?.toString() in setOf("add", "set")) {
                    "$path.mode 必须是 add 或 set"
                }
                validateTarget(action, path)
            }
            "status" -> {
                requiredString(action, "status", path)
                action["stacks"]?.let { requirePositiveNumeric(it, "$path.stacks") }
                action["duration-ms"]?.let { requirePositiveNumeric(it, "$path.duration-ms") }
                validateTarget(action, path)
            }
        }
    }

    private fun requireNumeric(raw: Any?, path: String) {
        require(raw is Number || (raw is String && (
            raw.toDoubleOrNull() != null || PERCENT.matches(raw) || EXACT_PLACEHOLDER.matches(raw)
        ))) {
            "$path 必须是数字或单个 {placeholder} 占位符"
        }
    }

    private fun requirePositiveNumeric(raw: Any?, path: String) {
        requireNumeric(raw, path)
        literalDouble(raw)?.let { require(it > 0.0) { "$path 必须大于 0" } }
    }

    private fun requireInteger(raw: Any?, path: String) {
        require(raw is Number && raw.toDouble().isFinite() && raw.toDouble() == raw.toLong().toDouble()) {
            "$path 必须是整数"
        }
    }

    private fun requireNonNegativeInteger(raw: Any?, path: String) {
        requireInteger(raw, path)
        require((raw as Number).toLong() >= 0L) { "$path 必须是非负整数" }
    }

    private fun requirePositiveLong(raw: Any?, path: String) {
        require(raw is Number && raw.toLong() > 0 && raw.toDouble() == raw.toLong().toDouble()) { "$path 必须是正整数" }
    }

    private fun requiredString(map: Map<String, Any?>, key: String, path: String): String =
        (map[key] as? String)?.takeIf(String::isNotBlank)
            ?: throw IllegalArgumentException("$path.$key 必须是非空字符串")

    private fun requireStringCollection(raw: Any?, path: String) {
        val values = when (raw) {
            is String -> listOf(raw)
            is Collection<*> -> raw.map { it as? String ?: throw IllegalArgumentException("$path 必须只包含字符串") }
            else -> throw IllegalArgumentException("$path 必须是字符串或字符串列表")
        }
        require(values.isNotEmpty() && values.all(String::isNotBlank)) { "$path 不能为空" }
    }

    private fun validateOperator(map: Map<String, Any?>, path: String) {
        require(map["operator"] == null || map["operator"]?.toString() in OPERATORS) { "$path.operator 无效" }
    }

    private fun validateTarget(map: Map<String, Any?>, path: String) {
        require(map["target"] == null || map["target"]?.toString() in TARGETS) { "$path.target 无效" }
    }

    private fun literalDouble(raw: Any?): Double? = when (raw) {
        is Number -> raw.toDouble()
        is String -> when {
            EXACT_PLACEHOLDER.matches(raw) -> null
            raw.endsWith('%') -> raw.dropLast(1).toDoubleOrNull()?.div(100.0)
            else -> raw.toDoubleOrNull()
        }
        else -> null
    }

    private fun rejectUnknown(map: Map<String, Any?>, allowed: Set<String>, path: String) {
        require((map.keys - allowed).isEmpty()) { "$path 包含未知字段 ${(map.keys - allowed).sorted()}" }
    }

    private fun listOfMaps(raw: Any?, path: String): List<Map<String, Any?>> =
        (raw as? List<*>)?.mapIndexed { index, value -> stringMap(value, "$path[$index]") }
            ?: throw IllegalArgumentException("$path 必须是列表")

    private fun stringMap(raw: Any?, path: String): Map<String, Any?> =
        (raw as? Map<*, *>)?.entries?.associate { it.key.toString() to it.value }
            ?: throw IllegalArgumentException("$path 必须是 YAML 映射")

    private val EXACT_PLACEHOLDER = Regex("^\\{([a-zA-Z0-9_.-]+)}$")
    private val PERCENT = Regex("^[+-]?(?:\\d+(?:\\.\\d*)?|\\.\\d+)%$")
    private val OPERATORS = setOf(">", ">=", "<", "<=", "==", "=", "!=", "<>")
    private val TARGETS = setOf("self", "caster", "target", "attacker", "victim")
}
