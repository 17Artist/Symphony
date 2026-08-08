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

package priv.seventeen.artist.symphony.overture.component

import priv.seventeen.artist.overture.api.component.ComponentDecodeContext
import priv.seventeen.artist.overture.api.component.ComponentDecodeResult
import priv.seventeen.artist.overture.api.component.ComponentIssue
import priv.seventeen.artist.overture.api.component.ItemComponentCodec
import priv.seventeen.artist.overture.api.data.ItemDataNode
import priv.seventeen.artist.symphony.api.attribute.AttributeKey
import priv.seventeen.artist.symphony.api.attribute.AttributeOperation
import priv.seventeen.artist.symphony.engine.config.LanguageBundle
import priv.seventeen.artist.symphony.engine.definition.DefinitionRepository
import priv.seventeen.artist.symphony.overture.data.allowedOnly
import priv.seventeen.artist.symphony.overture.data.boolean
import priv.seventeen.artist.symphony.overture.data.compoundOf
import priv.seventeen.artist.symphony.overture.data.int
import priv.seventeen.artist.symphony.overture.data.number
import priv.seventeen.artist.symphony.overture.data.text

class AttributeComponentCodec(
    private val definitions: DefinitionRepository,
    private val language: () -> LanguageBundle
) : ItemComponentCodec {
    override val schemaVersion: Int = 1

    override fun decode(context: ComponentDecodeContext): ComponentDecodeResult {
        val issues = mutableListOf<ComponentIssue>()
        val output = linkedMapOf<String, ItemDataNode>()
        context.source.values.toSortedMap().forEach { (rawId, rawNode) ->
            val path = rawId
            val key = runCatching { AttributeKey(if (':' in rawId) rawId else "symphony:$rawId") }
                .getOrElse {
                    issues += ComponentIssue(path, t("component.attribute.invalid-id"))
                    return@forEach
                }
            if (!definitions.current().snapshot.attributes.containsKey(key)) {
                issues += ComponentIssue(path, t("component.attribute.undefined", "id" to key))
                return@forEach
            }
            val node = rawNode as? ItemDataNode.Compound
            if (node == null) {
                issues += ComponentIssue(path, t("component.attribute.mapping"))
                return@forEach
            }
            val unknown = node.allowedOnly("operation", "value", "priority", "description")
            if (unknown.isNotEmpty()) issues += ComponentIssue(path, t("component.unknown-fields", "fields" to unknown.sorted()))
            val operation = runCatching { AttributeOperation.parse(node.text("operation") ?: "add") }.getOrElse {
                issues += ComponentIssue("$path.operation", t("component.attribute.invalid-operation"))
                return@forEach
            }
            val value = node.number("value")
            if (value == null || !value.isFinite()) {
                issues += ComponentIssue("$path.value", t("component.attribute.invalid-value"))
                return@forEach
            }
            val priority = runCatching { node.int("priority") ?: 0 }.getOrElse {
                issues += ComponentIssue("$path.priority", t("component.attribute.invalid-priority"))
                return@forEach
            }
            val values = linkedMapOf(
                "operation" to ItemDataNode.Text(operation.name.lowercase()),
                "value" to ItemDataNode.Decimal(value),
                "priority" to ItemDataNode.Integer(priority.toLong())
            )
            node.text("description")?.let { values["description"] = ItemDataNode.Text(it) }
            output[key.value] = ItemDataNode.Compound(values)
        }
        return result(issues, ItemDataNode.Compound(output))
    }

    private fun t(key: String, vararg variables: Pair<String, Any?>): String = language().text(key, *variables)
}

class SocketComponentCodec(
    private val language: () -> LanguageBundle
) : ItemComponentCodec {
    override val schemaVersion: Int = 1

    override fun decode(context: ComponentDecodeContext): ComponentDecodeResult {
        val source = context.source
        val issues = mutableListOf<ComponentIssue>()
        val unknown = source.allowedOnly("max-extra-slots", "slots")
        if (unknown.isNotEmpty()) issues += ComponentIssue(message = t("component.unknown-fields", "fields" to unknown.sorted()))
        val maximumExtraSlots = runCatching { source.int("max-extra-slots") ?: 0 }.getOrNull()
        if (maximumExtraSlots == null || maximumExtraSlots !in 0..32) {
            issues += ComponentIssue("max-extra-slots", t("component.socket.max-extra-slots"))
        }
        val rawSlots = (source.values["slots"] as? ItemDataNode.ListNode)?.values
        if (rawSlots == null) issues += ComponentIssue("slots", t("component.socket.list"))
        if ((rawSlots?.size ?: 0) > 32) issues += ComponentIssue("slots", t("component.socket.too-many"))
        val normalizedSlots = rawSlots.orEmpty().mapIndexedNotNull { index, raw ->
            val node = raw as? ItemDataNode.Compound
            if (node == null) {
                issues += ComponentIssue("slots[$index]", t("component.socket.mapping"))
                return@mapIndexedNotNull null
            }
            val slotUnknown = node.allowedOnly("accepts", "unlock-at-enhancement")
            if (slotUnknown.isNotEmpty()) issues += ComponentIssue("slots[$index]", t("component.unknown-fields", "fields" to slotUnknown.sorted()))
            val accepts = (node.values["accepts"] as? ItemDataNode.ListNode)?.values.orEmpty()
                .mapIndexedNotNull { categoryIndex, categoryNode ->
                    val value = (categoryNode as? ItemDataNode.Text)?.value
                    if (value == null || (value != "*" && !CATEGORY.matches(value))) {
                        issues += ComponentIssue("slots[$index].accepts[$categoryIndex]", t("component.socket.category"))
                        null
                    } else value
                }.distinct().sorted()
            if (accepts.isEmpty()) issues += ComponentIssue("slots[$index].accepts", t("component.socket.empty-accepts"))
            val unlockAt = runCatching { node.int("unlock-at-enhancement") ?: 0 }.getOrNull()
            if (unlockAt == null || unlockAt !in 0..10_000) {
                issues += ComponentIssue("slots[$index].unlock-at-enhancement", t("component.socket.unlock-level"))
                return@mapIndexedNotNull null
            }
            compoundOf(
                "accepts" to ItemDataNode.ListNode(accepts.map(ItemDataNode::Text)),
                "unlock_at_enhancement" to ItemDataNode.Integer(unlockAt.toLong())
            )
        }
        return result(
            issues,
            compoundOf(
                "max_extra_slots" to ItemDataNode.Integer((maximumExtraSlots ?: 0).toLong()),
                "slots" to ItemDataNode.ListNode(normalizedSlots)
            )
        )
    }

    private fun t(key: String, vararg variables: Pair<String, Any?>): String = language().text(key, *variables)

    companion object {
        private val CATEGORY = Regex("^[a-z0-9._/-]+$")
    }
}

class SkillComponentCodec(
    private val definitions: DefinitionRepository,
    private val language: () -> LanguageBundle
) : ItemComponentCodec {
    override val schemaVersion: Int = 1

    override fun decode(context: ComponentDecodeContext): ComponentDecodeResult {
        val issues = mutableListOf<ComponentIssue>()
        val normalized = linkedMapOf<String, ItemDataNode>()
        context.source.values.toSortedMap().forEach { (rawId, raw) ->
            val id = if (':' in rawId) rawId else "symphony:$rawId"
            val definition = definitions.current().snapshot.skills[id]
            if (definition == null) {
                issues += ComponentIssue(rawId, t("component.skill.undefined", "id" to id))
                return@forEach
            }
            val node = raw as? ItemDataNode.Compound
            if (node == null) {
                issues += ComponentIssue(rawId, t("component.skill.mapping"))
                return@forEach
            }
            val unknown = node.allowedOnly("level")
            if (unknown.isNotEmpty()) issues += ComponentIssue(rawId, t("component.unknown-fields", "fields" to unknown.sorted()))
            val level = runCatching { node.int("level") ?: 1 }.getOrNull()
            val maximum = (definition.values["max-level"] as? Number)?.toInt() ?: 1
            if (level == null || level !in 1..maximum) {
                issues += ComponentIssue("$rawId.level", t("component.skill.level", "maximum" to maximum))
                return@forEach
            }
            normalized[id] = compoundOf("level" to ItemDataNode.Integer(level.toLong()))
        }
        return result(issues, ItemDataNode.Compound(normalized))
    }

    private fun t(key: String, vararg variables: Pair<String, Any?>): String = language().text(key, *variables)
}

class SetComponentCodec(
    private val definitions: DefinitionRepository,
    private val language: () -> LanguageBundle
) : ItemComponentCodec {
    override val schemaVersion: Int = 1

    override fun decode(context: ComponentDecodeContext): ComponentDecodeResult {
        val source = context.source
        val issues = mutableListOf<ComponentIssue>()
        val unknown = source.allowedOnly("id", "piece", "amount")
        if (unknown.isNotEmpty()) issues += ComponentIssue(message = t("component.unknown-fields", "fields" to unknown.sorted()))
        val rawId = source.text("id")
        val setId = rawId?.let { if (':' in it) it else "symphony:$it" }
        val definition = setId?.let { definitions.current().snapshot.sets[it] }
        if (definition == null) issues += ComponentIssue("id", t("component.set.undefined", "id" to (rawId ?: t("component.missing-value"))))
        val piece = source.text("piece")
        if (piece == null || !PIECE.matches(piece)) {
            issues += ComponentIssue("piece", t("component.set.piece-id"))
        } else if (definition != null && piece !in definition.pieces) {
            issues += ComponentIssue("piece", t("component.set.piece-undefined", "set" to definition.name, "piece" to piece))
        }
        val amount = runCatching { source.int("amount") ?: 1 }.getOrNull()
        if (amount == null || amount !in 1..64) issues += ComponentIssue("amount", t("component.set.amount"))
        return result(
            issues,
            compoundOf(
                "id" to ItemDataNode.Text(setId.orEmpty()),
                "piece" to ItemDataNode.Text(piece.orEmpty()),
                "amount" to ItemDataNode.Integer((amount ?: 1).toLong())
            )
        )
    }

    private fun t(key: String, vararg variables: Pair<String, Any?>): String = language().text(key, *variables)

    companion object {
        private val PIECE = Regex("^[a-z0-9._/-]+$")
    }
}

class OffhandComponentCodec(
    private val language: () -> LanguageBundle
) : ItemComponentCodec {
    override val schemaVersion: Int = 1

    override fun decode(context: ComponentDecodeContext): ComponentDecodeResult {
        val source = context.source
        val issues = mutableListOf<ComponentIssue>()
        val unknown = source.allowedOnly("enabled", "attribute-scale")
        if (unknown.isNotEmpty()) {
            issues += ComponentIssue(message = t("component.unknown-fields", "fields" to unknown.sorted()))
        }
        val rawEnabled = source.values["enabled"]
        val enabled = if (rawEnabled == null) true else source.boolean("enabled")
        if (enabled == null) {
            issues += ComponentIssue("enabled", t("component.offhand.enabled"))
        }
        val rawScale = source.values["attribute-scale"]
        val scale = if (rawScale == null) 1.0 else source.number("attribute-scale")
        if (scale == null || !scale.isFinite() || scale !in 0.0..1.0) {
            issues += ComponentIssue("attribute-scale", t("component.offhand.attribute-scale"))
        }
        return result(
            issues,
            compoundOf(
                "enabled" to ItemDataNode.Bool(enabled ?: true),
                "attribute_scale" to ItemDataNode.Decimal(scale ?: 1.0)
            )
        )
    }

    private fun t(key: String, vararg variables: Pair<String, Any?>): String = language().text(key, *variables)
}

private fun result(issues: List<ComponentIssue>, value: ItemDataNode.Compound): ComponentDecodeResult =
    if (issues.isEmpty()) ComponentDecodeResult.Success(value) else ComponentDecodeResult.Failure(issues)
