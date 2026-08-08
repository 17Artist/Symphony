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

package priv.seventeen.artist.symphony.overture.item

import org.bukkit.NamespacedKey
import org.bukkit.inventory.ItemStack
import priv.seventeen.artist.overture.api.OvertureAPI
import priv.seventeen.artist.overture.api.data.ItemDataNode
import priv.seventeen.artist.overture.api.data.ItemDataView
import priv.seventeen.artist.symphony.api.attribute.AttributeKey
import priv.seventeen.artist.symphony.api.attribute.AttributeModifier
import priv.seventeen.artist.symphony.api.attribute.AttributeOperation
import priv.seventeen.artist.symphony.api.attribute.AttributeSourceKey
import priv.seventeen.artist.symphony.api.source.ItemSourceSnapshot
import priv.seventeen.artist.symphony.api.source.ItemFeatureContribution
import priv.seventeen.artist.symphony.api.source.SetPieceContribution
import priv.seventeen.artist.symphony.engine.definition.DefinitionRepository
import priv.seventeen.artist.symphony.engine.definition.multiplierAt
import priv.seventeen.artist.symphony.engine.equipment.OffhandItemSettings
import priv.seventeen.artist.symphony.engine.equipment.OffhandSettings
import priv.seventeen.artist.symphony.engine.equipment.OffhandSourcePolicy
import priv.seventeen.artist.symphony.overture.data.boolean
import priv.seventeen.artist.symphony.overture.data.compound
import priv.seventeen.artist.symphony.overture.data.int
import priv.seventeen.artist.symphony.overture.data.list
import priv.seventeen.artist.symphony.overture.data.number
import priv.seventeen.artist.symphony.overture.data.text

class OvertureItemSourceCompiler(
    private val definitions: DefinitionRepository,
    private val affixesEnabled: Boolean = true,
    private val itemFeaturesEnabled: Boolean = true
) {
    fun compile(source: AttributeSourceKey, item: ItemStack): ItemSourceSnapshot =
        compile(source, OvertureAPI.readItemData(item))

    fun compileOffhand(
        source: AttributeSourceKey,
        item: ItemStack,
        settings: OffhandSettings
    ): ItemSourceSnapshot? {
        val view = OvertureAPI.readItemData(item)
        val snapshot = compile(source, view)
        val itemSettings = view.component(OFFHAND)?.let { component ->
            OffhandItemSettings(
                enabled = component.boolean("enabled")
                    ?: throw IllegalArgumentException("symphony:offhand.enabled 缺失或类型错误"),
                attributeScale = component.number("attribute_scale")
                    ?: throw IllegalArgumentException("symphony:offhand.attribute-scale 缺失或类型错误")
            )
        }
        return OffhandSourcePolicy.apply(snapshot, settings, itemSettings)
    }

    private fun compile(source: AttributeSourceKey, view: ItemDataView): ItemSourceSnapshot {
        // 原版物品或无关物品只代表一个正常的空来源，并非损坏的 Symphony 数据。
        // 因此调用方可以安全扫描整个背包或装备栏。
        val itemId = view.itemId
            ?: return ItemSourceSnapshot(source, null, null, emptyList(), emptyList())
        val namespace = view.namespace("symphony")
            ?: return ItemSourceSnapshot(source, itemId, null, emptyList(), emptyList())
        val schema = namespace.int("schema") ?: throw IllegalArgumentException("物品缺少 symphony.schema")
        require(schema == 1) { "不支持的 Symphony 物品 schema: $schema" }

        val modifiers = mutableListOf<AttributeModifier>()
        view.component(ATTRIBUTES)?.let { parseModifiers(it, "definition", modifiers) }
        val definitionModifiers = modifiers.toList()
        val instance = namespace.compound("instance")
        val affixes = instance?.list("affixes")?.values.orEmpty().mapIndexed { index, value ->
            val entry = value as? ItemDataNode.Compound ?: throw IllegalArgumentException("instance.affixes[$index] 必须是映射")
            if (affixesEnabled) entry.compound("modifiers")?.let { parseModifiers(it, "affix:$index", modifiers) }
            parseFeature(entry, "instance.affixes[$index]")
        }
        val gems = instance?.list("gems")?.values.orEmpty().mapIndexed { index, value ->
            val entry = value as? ItemDataNode.Compound ?: throw IllegalArgumentException("instance.gems[$index] 必须是映射")
            if (itemFeaturesEnabled) entry.compound("modifiers")?.let { parseModifiers(it, "gem:$index", modifiers) }
            val id = entry.text("id")?.let { if (':' in it) it else "symphony:$it" }
            val category = id?.let { definitions.current().snapshot.gems[it]?.values?.get("category") as? String }
            parseFeature(entry, "instance.gems[$index]", category, entry.int("slot") ?: index)
        }
        if (itemFeaturesEnabled) instance?.compound("enhancement")?.compound("modifiers")?.let {
            parseModifiers(it, "enhancement", modifiers)
        }
        val enhancementLevel = instance?.compound("enhancement")?.int("level") ?: 0
        if (itemFeaturesEnabled && enhancementLevel > 0) {
            val multiplier = definitions.current().snapshot.enhancement?.multiplierAt(enhancementLevel) ?: 1.0
            if (multiplier != 1.0) {
                definitionModifiers.filter { it.operation == AttributeOperation.ADD }.forEach { base ->
                    modifiers += base.copy(
                        id = "enhancement-scale:${base.id}",
                        value = base.value * (multiplier - 1.0),
                        description = "强化等级 $enhancementLevel"
                    )
                }
            }
        }

        val setPieces = view.component(SET)?.let { set ->
            val setId = requireNotNull(set.text("id")) { "symphony:set.id 缺失" }
            val key = NamespacedKey.fromString(setId) ?: throw IllegalArgumentException("套装 ID 不合法：$setId")
            listOf(
                SetPieceContribution(
                    key,
                    requireNotNull(set.text("piece")) { "symphony:set.piece 缺失" },
                    set.int("amount") ?: 1
                )
            )
        }.orEmpty()
        val skills = view.component(SKILLS)?.values.orEmpty().map { (rawId, raw) ->
            val node = raw as? ItemDataNode.Compound
                ?: throw IllegalArgumentException("symphony:skills.$rawId 必须是映射")
            val id = NamespacedKey.fromString(rawId)
                ?: throw IllegalArgumentException("技能 ID 不合法：$rawId")
            ItemFeatureContribution(id, node.int("level") ?: 1)
        }

        return ItemSourceSnapshot(
            source = source,
            overtureItemId = itemId,
            instanceId = instance?.text("uuid"),
            modifiers = modifiers,
            setPieces = setPieces,
            affixes = affixes,
            gems = gems,
            skills = skills,
            enhancementLevel = enhancementLevel,
            instanceRevision = instance?.int("revision") ?: 0
        )
    }

    private fun parseFeature(
        entry: ItemDataNode.Compound,
        path: String,
        category: String? = null,
        slot: Int? = null
    ): ItemFeatureContribution {
        val rawId = entry.text("id") ?: throw IllegalArgumentException("$path.id 缺失")
        val id = NamespacedKey.fromString(if (':' in rawId) rawId else "symphony:$rawId")
            ?: throw IllegalArgumentException("$path.id 不合法：$rawId")
        val parameters = entry.compound("parameters")?.values.orEmpty().mapValues { (key, value) ->
            when (value) {
                is ItemDataNode.Integer -> value.value.toDouble()
                is ItemDataNode.Decimal -> value.value
                is ItemDataNode.Text -> value.value.removeSuffix("%").toDoubleOrNull()?.let {
                    if (value.value.endsWith('%')) it / 100.0 else it
                }
                else -> null
            }?.also { require(it.isFinite()) { "$path.parameters.$key 必须是有限数字" } }
                ?: throw IllegalArgumentException("$path.parameters.$key 必须是数字")
        }
        val tags = entry.list("tags")?.values.orEmpty().mapIndexed { index, value ->
            (value as? ItemDataNode.Text)?.value ?: throw IllegalArgumentException("$path.tags[$index] 必须是字符串")
        }.toSet()
        return ItemFeatureContribution(id, entry.int("level") ?: 1, parameters, tags, category, slot)
    }

    private fun parseModifiers(
        compound: ItemDataNode.Compound,
        prefix: String,
        output: MutableList<AttributeModifier>
    ) {
        require(output.size + compound.values.size <= MAX_MODIFIERS) { "物品属性数量超过 $MAX_MODIFIERS" }
        compound.values.toSortedMap().forEach { (rawKey, rawNode) ->
            val key = AttributeKey(if (':' in rawKey) rawKey else "symphony:$rawKey")
            require(definitions.current().snapshot.attributes.containsKey(key)) { "物品引用了未定义属性 $key" }
            val node = rawNode as? ItemDataNode.Compound ?: throw IllegalArgumentException("属性 $key 必须是映射")
            val value = node.number("value") ?: throw IllegalArgumentException("属性 $key 缺少有限 value")
            val operation = AttributeOperation.parse(node.text("operation") ?: "add")
            output += AttributeModifier(
                id = "$prefix:$rawKey",
                attribute = key,
                operation = operation,
                value = value,
                priority = node.int("priority") ?: 0,
                description = node.text("description")
            )
        }
    }

    companion object {
        const val MAX_MODIFIERS = 1024
        val ATTRIBUTES = NamespacedKey("symphony", "attributes")
        val SOCKETS = NamespacedKey("symphony", "sockets")
        val SET = NamespacedKey("symphony", "set")
        val SKILLS = NamespacedKey("symphony", "skills")
        val OFFHAND = NamespacedKey("symphony", "offhand")
    }
}
