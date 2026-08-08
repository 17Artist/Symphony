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

package priv.seventeen.artist.symphony.overture.render

import org.bukkit.NamespacedKey
import org.bukkit.entity.Player
import org.bukkit.plugin.Plugin
import priv.seventeen.artist.overture.api.OvertureAPI
import priv.seventeen.artist.overture.api.data.ItemDataNode
import priv.seventeen.artist.overture.api.registry.RegistrationHandle
import priv.seventeen.artist.overture.api.render.RenderEntryContext
import priv.seventeen.artist.overture.api.render.RenderEntryRenderer
import priv.seventeen.artist.symphony.api.attribute.AttributeFormat
import priv.seventeen.artist.symphony.api.attribute.AttributeKey
import priv.seventeen.artist.symphony.engine.definition.DefinitionRepository
import priv.seventeen.artist.symphony.engine.definition.SetDefinition
import priv.seventeen.artist.symphony.engine.definition.affixDescription
import priv.seventeen.artist.symphony.engine.definition.multiplierAt
import priv.seventeen.artist.symphony.engine.definition.skillActivation
import priv.seventeen.artist.symphony.engine.config.LanguageBundle
import priv.seventeen.artist.symphony.engine.config.ItemDisplayFormats
import priv.seventeen.artist.symphony.overture.data.compound
import priv.seventeen.artist.symphony.overture.data.int
import priv.seventeen.artist.symphony.overture.data.list
import priv.seventeen.artist.symphony.overture.data.number
import priv.seventeen.artist.symphony.overture.data.text
import priv.seventeen.artist.symphony.overture.data.boolean
import java.math.BigDecimal
import java.math.RoundingMode

class SymphonyRenderers(
    private val plugin: Plugin,
    private val definitions: DefinitionRepository,
    private val language: () -> LanguageBundle,
    private val formats: () -> ItemDisplayFormats,
    private val activeSetCount: (Player, String) -> Int
) {
    fun registerAll(): List<RegistrationHandle> = listOf(
        register("attributes", ::renderAttributes),
        register("affixes", ::renderAffixes),
        register("skills", ::renderSkills),
        register("sockets", ::renderSockets),
        register("enhancement", ::renderEnhancement),
        register("set", ::renderSet),
        register("offhand", ::renderOffhand)
    )

    private fun register(key: String, renderer: (RenderEntryContext) -> List<String>): RegistrationHandle =
        OvertureAPI.registerRenderEntry(
            plugin,
            NamespacedKey(plugin, key),
            0
        ) { context -> renderer(context).take(MAX_LINES) }

    private fun renderAttributes(context: RenderEntryContext): List<String> {
        val component = context.data.component(NamespacedKey("symphony", "attributes")) ?: return emptyList()
        val enhancementLevel = context.data.namespace("symphony")?.compound("instance")
            ?.compound("enhancement")?.int("level") ?: 0
        val enhancementMultiplier = definitions.current().snapshot.enhancement?.multiplierAt(enhancementLevel) ?: 1.0
        return component.values.toSortedMap(compareBy<String> {
            definitions.current().snapshot.attributes[AttributeKey(it)]?.definition?.priority ?: 0
        }.thenBy { it }).mapNotNull { (rawKey, rawNode) ->
            val key = runCatching { AttributeKey(rawKey) }.getOrNull() ?: return@mapNotNull null
            val definition = definitions.current().snapshot.attributes[key]?.definition ?: return@mapNotNull null
            val node = rawNode as? ItemDataNode.Compound ?: return@mapNotNull null
            val value = node.number("value") ?: return@mapNotNull null
            val operation = node.text("operation") ?: "add"
            val effectiveValue = if (operation == "add") value * enhancementMultiplier else value
            val renderedValue = if (operation == "add") {
                format(effectiveValue, definition.format, definition.roundingScale)
            } else {
                format(effectiveValue, AttributeFormat.PERCENT, definition.roundingScale)
            }
            val displayValue = if (effectiveValue >= 0.0) "+$renderedValue" else renderedValue
            formats().render(
                context.displayId,
                when (operation) {
                    "multiply_base" -> "attribute.multiply-base"
                    "multiply_total" -> "attribute.multiply-total"
                    else -> "attribute.add"
                },
                "name" to definition.name,
                "value" to displayValue
            )
        }
    }

    private fun renderAffixes(context: RenderEntryContext): List<String> {
        val instance = context.data.namespace("symphony")?.compound("instance") ?: return emptyList()
        return instance.list("affixes")?.values.orEmpty().flatMap { raw ->
            val node = raw as? ItemDataNode.Compound ?: return@flatMap emptyList()
            val id = node.text("id")?.let { if (':' in it) it else "symphony:$it" }
            val name = node.text("name") ?: language().text("lore.common.unknown-name")
            val level = node.int("level") ?: 1
            val title = formats().render(
                context.displayId,
                "affix.line",
                "name" to name,
                "level" to formats().render(context.displayId, "affix.level", "level" to level),
                "locked" to if (node.boolean("locked") == true) formats().render(context.displayId, "affix.locked") else ""
            )
            val details = id?.let { definitions.current().snapshot.affixes[it] }
                ?.affixDescription(level, node.numberMap("parameters"))
                .orEmpty()
                .map { formats().render(context.displayId, "affix.description", "description" to it) }
            listOf(title) + details
        }
    }

    private fun renderSkills(context: RenderEntryContext): List<String> {
        val skills = context.data.component(NamespacedKey("symphony", "skills")) ?: return emptyList()
        return skills.values.toSortedMap().flatMap { (id, raw) ->
            val node = raw as? ItemDataNode.Compound ?: return@flatMap emptyList()
            val definition = definitions.current().snapshot.skills[id] ?: return@flatMap emptyList()
            val lines = mutableListOf(formats().render(
                context.displayId,
                "skill.line",
                "name" to (definition.values["name"]?.toString() ?: language().text("lore.common.unknown-name")),
                "level" to (node.int("level") ?: 1)
            ))
            definition.values["description"]?.toString()?.takeIf(String::isNotBlank)?.let {
                lines += formats().render(context.displayId, "skill.description", "description" to it)
            }
            definition.skillActivation()?.let { activation ->
                val input = language().optional(
                    "skill-activation.inputs.${activation.input.id}",
                    language().text("skill-activation.inputs.other")
                )
                val source = language().optional(
                    "skill-activation.sources.${activation.source.id}",
                    language().text("skill-activation.sources.any")
                )
                lines += formats().render(context.displayId, "skill.activation", "input" to input, "source" to source)
            }
            lines
        }
    }

    private fun renderSockets(context: RenderEntryContext): List<String> {
        val sockets = context.data.component(NamespacedKey("symphony", "sockets")) ?: return emptyList()
        val instance = context.data.namespace("symphony")?.compound("instance")
        val gems = instance?.list("gems")?.values.orEmpty()
        val gemsBySlot = gems.mapIndexedNotNull { index, raw ->
            val gem = raw as? ItemDataNode.Compound ?: return@mapIndexedNotNull null
            (gem.int("slot") ?: index) to gem
        }.toMap()
        val enhancement = instance?.compound("enhancement")?.int("level") ?: 0
        val static = sockets.list("slots")?.values.orEmpty()
        val extra = instance?.list("extra_sockets")?.values.orEmpty()
        return (static + extra).mapIndexedNotNull { index, raw ->
            val slot = raw as? ItemDataNode.Compound ?: return@mapIndexedNotNull null
            val accepts = slot.list("accepts")?.values.orEmpty()
                .mapNotNull { (it as? ItemDataNode.Text)?.value }
                .joinToString { categoryName(it) }
            val unlockAt = slot.int("unlock_at_enhancement") ?: 0
            val gem = gemsBySlot[index]?.text("name")
            when {
                enhancement < unlockAt -> formats().render(context.displayId, "socket.locked", "slot" to index + 1, "categories" to accepts, "level" to unlockAt)
                gem != null -> formats().render(context.displayId, "socket.filled", "slot" to index + 1, "categories" to accepts, "name" to gem)
                else -> formats().render(context.displayId, "socket.empty", "slot" to index + 1, "categories" to accepts)
            }
        }
    }

    private fun renderEnhancement(context: RenderEntryContext): List<String> {
        val enhancement = context.data.namespace("symphony")?.compound("instance")?.compound("enhancement")
            ?: return emptyList()
        val level = enhancement.int("level") ?: 0
        if (level <= 0) return emptyList()
        val multiplier = definitions.current().snapshot.enhancement?.multiplierAt(level) ?: 1.0
        return buildList {
            add(formats().render(context.displayId, "enhancement.level", "level" to level))
            if (multiplier != 1.0) add(formats().render(
                context.displayId,
                "enhancement.bonus",
                "bonus" to format(multiplier - 1.0, AttributeFormat.PERCENT, 2),
                "multiplier" to format(multiplier, AttributeFormat.NUMBER, 3)
            ))
        }
    }

    private fun renderSet(context: RenderEntryContext): List<String> {
        val set = context.data.component(NamespacedKey("symphony", "set")) ?: return emptyList()
        val id = set.text("id") ?: return emptyList()
        val piece = set.text("piece") ?: return emptyList()
        val definition = definitions.current().snapshot.sets[id]
        val count = context.player?.let { activeSetCount(it, id) }
        return composeSetLore(definition, piece, count, language(), formats(), context.displayId)
    }

    private fun renderOffhand(context: RenderEntryContext): List<String> {
        val offhand = context.data.component(NamespacedKey("symphony", "offhand")) ?: return emptyList()
        if (offhand.boolean("enabled") == false) {
            return listOf(formats().render(context.displayId, "offhand.denied"))
        }
        val scale = offhand.number("attribute_scale") ?: 1.0
        return listOf(formats().render(
            context.displayId,
            "offhand.allowed",
            "scale" to format(scale, AttributeFormat.PERCENT, 2)
        ))
    }

    private fun format(value: Double, format: AttributeFormat, scale: Int): String {
        val actual = if (format == AttributeFormat.PERCENT) value * 100.0 else value
        val suffix = if (format == AttributeFormat.PERCENT) "%" else ""
        return BigDecimal.valueOf(actual).setScale(scale, RoundingMode.HALF_UP).stripTrailingZeros().toPlainString() + suffix
    }

    private fun categoryName(id: String): String = language().optional(
        "socket-categories.${if (id == "*") "universal" else id.lowercase()}",
        language().text("socket-categories.other")
    )

    companion object {
        const val MAX_LINES = 128
    }
}

private fun ItemDataNode.Compound.numberMap(name: String): Map<String, Double> =
    compound(name)?.values.orEmpty().mapNotNull { (key, value) ->
        val number = when (value) {
            is ItemDataNode.Integer -> value.value.toDouble()
            is ItemDataNode.Decimal -> value.value
            is ItemDataNode.Text -> value.value.removeSuffix("%").toDoubleOrNull()?.let {
                if (value.value.endsWith('%')) it / 100.0 else it
            }
            else -> null
        }
        number?.let { key to it }
    }.toMap()

internal fun composeSetLore(
    definition: SetDefinition?,
    pieceId: String,
    count: Int?,
    language: LanguageBundle,
    formats: ItemDisplayFormats,
    displayId: String? = null
): List<String> {
    val setName = definition?.name ?: language.text("lore.set.unknown-name")
    val pieceName = definition?.pieces?.get(pieceId) ?: language.text("lore.set.unknown-piece")
    val lines = mutableListOf(
        formats.render(displayId, "set.title", "name" to setName),
        formats.render(displayId, "set.piece", "piece" to pieceName)
    )
    definition?.bonuses?.toSortedMap()?.forEach { (threshold, bonus) ->
        val bonusName = bonus.display.name
        val key = when {
            count == null -> "set.unknown"
            count >= threshold -> "set.active"
            else -> "set.inactive"
        }
        lines += formats.render(displayId, key, "threshold" to threshold, "count" to (count ?: 0), "name" to bonusName)
        bonus.display.description.forEach { description ->
            lines += formats.render(displayId, "set.description", "description" to description)
        }
    }
    return lines
}
