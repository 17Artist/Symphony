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

package priv.seventeen.artist.symphony.bukkit.gui

import org.bukkit.Material
import org.bukkit.entity.Player
import org.bukkit.inventory.Inventory
import org.bukkit.inventory.ItemStack
import priv.seventeen.artist.overture.api.OvertureAPI
import priv.seventeen.artist.symphony.api.attribute.AttributeKey
import priv.seventeen.artist.symphony.api.service.SymphonyApi
import priv.seventeen.artist.symphony.engine.attribute.AttributeStateStore
import priv.seventeen.artist.symphony.engine.config.LanguageBundle
import priv.seventeen.artist.symphony.engine.definition.AffixPoolDefinition
import priv.seventeen.artist.symphony.engine.definition.DefinitionRepository
import priv.seventeen.artist.symphony.engine.definition.affixDescription
import priv.seventeen.artist.symphony.engine.trigger.CallbackFailureState
import java.math.BigDecimal
import java.math.RoundingMode

data class GuiContext(
    val viewer: Player,
    val target: Player,
    val session: GuiSession,
    val inventory: Inventory,
    val api: SymphonyApi,
    val store: AttributeStateStore,
    val definitions: DefinitionRepository,
    val language: LanguageBundle,
    val icons: GuiIconFactory,
    val workshopPreview: (Player, Inventory, GuiLayout, String, Int) -> List<String>,
    val callbackFailures: () -> List<CallbackFailureState>
) {
    lateinit var layout: GuiLayout
    val contentSlots: List<Int> get() = layout.pageSlots
    fun slot(key: String, fallback: Int): Int = layout.slots[key]?.slot ?: layout.scalarSlots[key] ?: fallback
    fun put(slot: Int, material: Material, name: String, lore: List<String> = emptyList(), template: String? = null) {
        if (slot in 0 until inventory.size) inventory.setItem(slot, icons.icon(viewer, template, material, name, lore))
    }

    fun t(key: String, vararg variables: Pair<String, Any?>): String = language.text(key, *variables)

    fun setName(id: String): String = definitions.current().snapshot.sets[id]?.name ?: t("common.unnamed-set")

    fun genericName(type: String, id: String): String {
        val snapshot = definitions.current().snapshot
        val definition = when (type) {
            "affix" -> snapshot.affixes[id]
            "gem" -> snapshot.gems[id]
            "status" -> snapshot.statuses[id]
            else -> null
        }
        return definition?.values?.get("name")?.toString()?.takeIf(String::isNotBlank) ?: t("common.unknown-name")
    }

    fun affixDescription(id: String, level: Int, parameters: Map<String, Double>): List<String> =
        definitions.current().snapshot.affixes[id]?.affixDescription(level, parameters).orEmpty()

    fun sourceName(source: priv.seventeen.artist.symphony.api.attribute.AttributeSourceKey): String = when {
        source.namespace == "equipment" -> language.optional("sources.equipment.${source.value}", t("sources.external"))
        source.namespace == "set" -> t("sources.set")
        source.namespace == "status" -> t("sources.status")
        source.namespace == "environment" -> t("sources.environment")
        source.namespace == "provider" -> t("sources.provider")
        else -> t("sources.external")
    }

    fun socketCategoryName(id: String): String = language.optional(
        "socket-categories.${if (id == "*") "universal" else id.lowercase()}",
        t("socket-categories.other")
    )
}

interface GuiScreen {
    val id: GuiScreenId
    fun render(context: GuiContext): ScreenRender
}

abstract class StandardScreen : GuiScreen {
    protected fun frame(context: GuiContext, openSlots: Set<Int> = emptySet()) {
        val filler = context.icons.filler(context.viewer)
        (0 until context.inventory.size).forEach { slot ->
            if (slot !in openSlots) context.inventory.setItem(slot, filler)
        }
        context.put(context.slot("close", 49), Material.BARRIER, context.t("gui.navigation.close"))
    }

    protected fun navigationActions(context: GuiContext): MutableMap<Int, GuiAction> =
        linkedMapOf(context.slot("close", 49) to GuiAction.Close)

    protected fun format(value: Double): String = BigDecimal.valueOf(value)
        .setScale(3, RoundingMode.HALF_UP).stripTrailingZeros().toPlainString()

    protected fun formatDuration(millis: Long): String = if (millis <= 0L) "0" else BigDecimal.valueOf(millis)
        .divide(BigDecimal.valueOf(1000L), 1, RoundingMode.HALF_UP).stripTrailingZeros().toPlainString()

    protected fun playerHeader(context: GuiContext) {
        val level = context.api.levels.snapshot(context.target)
        val combatPower = runCatching { context.api.combatPower.snapshot(context.target) }
            .getOrNull()
            ?.takeIf { it.successful }
            ?.formatted
            ?: context.t("common.unavailable")
        val state = context.store.state(context.target.uniqueId)
        val metadata = context.api.metadata
        val combat = metadata.combatState(context.target)
        context.put(context.slot("summary", 4), Material.PLAYER_HEAD, context.t("gui.overview.player", "name" to context.target.name), listOf(
            context.t("gui.overview.level", "value" to (level?.level ?: context.t("common.unavailable"))),
            context.t("gui.overview.level-provider", "value" to (level?.providerName ?: context.t("common.none"))),
            context.t("gui.overview.combat-power", "value" to combatPower),
            context.t("gui.overview.sets", "value" to state.setResolution.activeThresholds.size),
            context.t("gui.overview.passives", "value" to metadata.activePassives(context.target).size),
            context.t("gui.overview.effects", "statuses" to metadata.statuses(context.target).size, "auras" to metadata.auras(context.target).size),
            context.t(if (combat.active) "gui.overview.in-combat" else "gui.overview.out-of-combat")
        ))
    }
}

data class DisplayEntry(
    val material: Material,
    val name: String,
    val lore: List<String> = emptyList(),
    val action: GuiAction? = null
)

class AttributeBrowserScreen : StandardScreen() {
    override val id = GuiScreenId.ATTRIBUTE_BROWSER

    override fun render(context: GuiContext): ScreenRender {
        frame(context)
        playerHeader(context)
        val actions = navigationActions(context)
        val tabs = listOf(
            AttributeSection.ATTRIBUTES to Triple("section-attributes", Material.BOOK, "gui.attributes.sections.attributes"),
            AttributeSection.SOURCES to Triple("section-sources", Material.ARMOR_STAND, "gui.attributes.sections.sources"),
            AttributeSection.SKILLS to Triple("section-skills", Material.ENCHANTED_BOOK, "gui.attributes.sections.skills"),
            AttributeSection.SETS to Triple("section-sets", Material.CHEST, "gui.attributes.sections.sets"),
            AttributeSection.STATUS to Triple("section-status", Material.CLOCK, "gui.attributes.sections.status")
        )
        tabs.forEach { (section, presentation) ->
            val selected = section == context.session.section
            val slot = context.slot(presentation.first, 9)
            context.put(
                slot,
                if (selected) Material.LIME_STAINED_GLASS_PANE else presentation.second,
                context.t(presentation.third),
                listOf(context.t(if (selected) "gui.attributes.section-selected" else "gui.attributes.section-open"))
            )
            actions[slot] = GuiAction.SelectSection(section)
        }

        val entries = when (context.session.section) {
            AttributeSection.ATTRIBUTES -> attributeEntries(context)
            AttributeSection.SOURCES -> sourceEntries(context)
            AttributeSection.SKILLS -> skillEntries(context)
            AttributeSection.SETS -> setEntries(context)
            AttributeSection.STATUS -> statusEntries(context)
        }
        val page = paginate(entries, context.session.page, context.contentSlots.size)
        page.items.forEachIndexed { index, entry ->
            val slot = context.contentSlots[index]
            context.put(slot, entry.material, entry.name, entry.lore)
            entry.action?.let { actions[slot] = it }
        }
        if (entries.isEmpty()) {
            context.put(context.contentSlots.firstOrNull() ?: 22, Material.GRAY_DYE, context.t("gui.attributes.empty"))
        }
        addPaging(context, actions, page)
        return ScreenRender(actions)
    }

    private fun attributeEntries(context: GuiContext): List<DisplayEntry> = context.api.definitions.attributes().values
        .sortedWith(compareBy({ it.category }, { it.priority }, { it.key.value }))
        .map { definition ->
            DisplayEntry(Material.PAPER, context.t("gui.attribute.name", "name" to definition.name), listOf(
                context.t("gui.attribute.category", "value" to context.language.optional("categories.${definition.category.lowercase()}", context.t("categories.other"))),
                context.t("gui.attribute.value", "value" to format(context.api.attributes.value(context.target, definition.key))),
                context.t("gui.attribute.open-explain")
            ), GuiAction.Explain(definition.key))
        }

    private fun sourceEntries(context: GuiContext): List<DisplayEntry> = context.store.state(context.target.uniqueId).sources.entries
        .filter { it.value.item != null }
        .sortedBy { it.key }
        .map { entry ->
            val item = requireNotNull(entry.value.item)
            val sets = item.setPieces.map { piece ->
                val set = context.definitions.current().snapshot.sets[piece.setId.toString()]
                context.t(
                    "gui.equipment.set-piece",
                    "set" to (set?.name ?: context.t("common.unnamed-set")),
                    "piece" to (set?.pieces?.get(piece.pieceId) ?: context.t("common.unknown-piece")),
                    "amount" to piece.amount
                )
            }
            DisplayEntry(Material.ITEM_FRAME, context.sourceName(entry.key), listOf(
                context.t("gui.equipment.attributes", "value" to item.modifiers.size),
                context.t("gui.equipment.features", "affixes" to item.affixes.size, "gems" to item.gems.size, "skills" to item.skills.size)
            ) + sets.ifEmpty { listOf(context.t("gui.equipment.no-set-piece")) })
        }

    private fun skillEntries(context: GuiContext): List<DisplayEntry> = context.api.skills.inspect(context.target).map { skill ->
        val lore = buildList {
            if (skill.description.isNotBlank()) add(context.t("gui.skills.description", "value" to skill.description))
            add(context.t("gui.skills.target", "value" to context.language.optional("target-types.${skill.targetType.lowercase()}", context.t("target-types.other")), "range" to skill.range?.let { context.t("gui.skills.range", "value" to format(it)) }.orEmpty()))
            add(context.t("gui.skills.source", "value" to context.sourceName(skill.source)))
            add(context.t("gui.skills.level", "value" to skill.level))
            add(skill.activation?.let { activation ->
                context.t("gui.skills.activation", "input" to context.language.optional("skill-activation.inputs.${activation.input.lowercase()}", context.t("skill-activation.inputs.other")), "source" to context.language.optional("skill-activation.sources.${activation.source.lowercase()}", context.t("skill-activation.sources.any")))
            } ?: context.t("gui.skills.api-only"))
            add(context.t("gui.skills.cooldown", "remaining" to formatDuration(skill.remainingCooldownMillis), "total" to formatDuration(skill.cooldownMillis)))
        }
        DisplayEntry(Material.ENCHANTED_BOOK, context.t("gui.skills.name", "name" to skill.name), lore)
    }

    private fun setEntries(context: GuiContext): List<DisplayEntry> {
        val state = context.store.state(context.target.uniqueId)
        return context.api.definitions.sets().values.map { definition ->
            val count = state.setResolution.counts[definition.key.toString()] ?: 0
            val next = definition.thresholds.firstOrNull { it > count }
            val tiers = definition.bonuses.flatMap { bonus ->
                listOf(context.t(
                    if (count >= bonus.threshold) "gui.collection.tier-active" else "gui.collection.tier-inactive",
                    "threshold" to bonus.threshold,
                    "name" to bonus.name
                )) + bonus.description.map { context.t("gui.collection.tier-description", "description" to it) }
            }
            DisplayEntry(Material.CHEST, context.t("gui.collection.set-name", "name" to definition.name), listOf(
                context.t("gui.collection.set-count", "value" to count),
                if (next == null) context.t("gui.collection.complete") else context.t("gui.collection.next", "value" to next),
                context.t("gui.collection.pieces", "value" to definition.pieces.values.joinToString())
            ) + tiers)
        }
    }

    private fun statusEntries(context: GuiContext): List<DisplayEntry> {
        val metadata = context.api.metadata
        return buildList {
            val combat = metadata.combatState(context.target)
            add(DisplayEntry(Material.SHIELD, context.t("gui.runtime.shield-title"), listOf(
                context.t("gui.runtime.shield", "current" to format(context.api.damage.shield(context.target)), "maximum" to format(context.api.attributes.value(context.target, AttributeKey.symphony("shield_capacity")))),
                if (combat.active) context.t("gui.runtime.in-combat", "seconds" to formatDuration(combat.remainingMillis)) else context.t("gui.runtime.out-of-combat")
            )))
            metadata.statuses(context.target).forEach {
                add(DisplayEntry(Material.POTION, context.t("gui.runtime.status", "name" to context.genericName("status", it.id.toString()), "stacks" to it.stacks, "seconds" to formatDuration(it.remainingMillis))))
            }
            metadata.auras(context.target).forEach {
                val name = context.definitions.current().snapshot.damageChannels[it.channel]?.name ?: context.t("common.unknown-effect")
                add(DisplayEntry(Material.BLAZE_POWDER, context.t("gui.runtime.aura", "name" to name, "gauge" to format(it.gauge), "seconds" to formatDuration(it.remainingMillis))))
            }
            metadata.activePassives(context.target).forEach { key ->
                val passive = context.api.definitions.passives().firstOrNull { it.key == key }
                add(DisplayEntry(Material.ENCHANTED_BOOK, context.t("gui.runtime.passive", "name" to (passive?.name ?: context.t("common.unknown-name")))))
            }
        }
    }
}

class AttributeExplainScreen : StandardScreen() {
    override val id = GuiScreenId.ATTRIBUTE_EXPLAIN

    override fun render(context: GuiContext): ScreenRender {
        frame(context)
        val actions = navigationActions(context)
        val key = runCatching { AttributeKey(context.session.filter) }.getOrNull()
        val explain = key?.let { context.api.attributes.explain(context.target, it) }
        if (explain == null) {
            context.put(4, Material.BARRIER, context.t("gui.attribute.missing"))
            return ScreenRender(actions)
        }
        val definition = context.api.definitions.attribute(explain.key)
        context.put(4, Material.NAME_TAG, context.t("gui.attribute.explain-name", "name" to (definition?.name ?: context.t("common.unknown-name"))), listOf(
            context.t("gui.attribute.base", "value" to format(explain.base)),
            context.t("gui.attribute.aggregated", "value" to format(explain.standardValue)),
            context.t("gui.attribute.calculated", "value" to format(explain.calculatedValue)),
            context.t("gui.attribute.bounded", "value" to format(explain.boundedValue)),
            context.t("gui.attribute.final", "value" to explain.formatted)
        ))
        val page = paginate(explain.contributions, context.session.page, context.contentSlots.size)
        page.items.forEachIndexed { index, contribution ->
            context.put(context.contentSlots[index], Material.MAP, context.sourceName(contribution.source), listOf(
                context.t("gui.attribute.operation", "value" to context.t("operations.${contribution.modifier.operation.name.lowercase()}")),
                context.t("gui.attribute.modifier-value", "value" to format(contribution.modifier.value)),
                context.t("gui.attribute.change", "before" to format(contribution.valueBefore), "after" to format(contribution.valueAfter))
            ))
        }
        addPaging(context, actions, page)
        return ScreenRender(actions)
    }
}

internal fun attributeExplainContributionSlots(layout: GuiLayout, summarySlot: Int): List<Int> =
    layout.pageSlots.filterNot { it == summarySlot }

abstract class WorkshopScreen(
    override val id: GuiScreenId,
    protected val actionType: String
) : StandardScreen() {
    protected fun prepare(context: GuiContext): MutableMap<Int, GuiAction> {
        val openSlots = id.workshopRoles().mapTo(linkedSetOf()) { context.layout.workshopSlot(it) }
        frame(context, openSlots)
        context.put(4, Material.CRAFTING_TABLE, context.t("gui.workshop.title.$actionType"), context.language.lines("gui.workshop.$actionType-description"))
        return navigationActions(context)
    }

    protected fun target(context: GuiContext): ItemStack = context.inventory
        .getItem(context.layout.workshopSlot(WorkshopSlotRole.TARGET)) ?: ItemStack(Material.AIR)

    protected fun validInspection(context: GuiContext) = context.api.items.inspect(target(context))

    protected fun preview(context: GuiContext) {
        val inspection = validInspection(context)
        if (!inspection.supportsWorkshops || inspection.diagnostics.isNotEmpty()) {
            context.put(context.slot("preview-slot", 37), Material.BARRIER, context.t("item.invalid"), listOf(context.t("gui.item.invalid-help")))
            return
        }
        context.put(
            context.slot("preview-slot", 37),
            Material.PAPER,
            context.t("gui.workshop.preview"),
            context.workshopPreview(context.viewer, context.inventory, context.layout, actionType, context.session.selectedIndex)
        )
    }

    protected fun labels(context: GuiContext, materialType: String, output: Boolean = false) {
        context.put(context.slot("target-label", 9), Material.HOPPER, context.t("gui.workshop.slots.target"), context.language.lines("gui.workshop.slots.target-help"))
        context.put(context.slot("material-label", 18), Material.CHEST, context.t("gui.workshop.slots.material.$materialType"), context.language.lines("gui.workshop.slots.material-help.$materialType"))
        if (output) context.put(context.slot("output-label", 27), Material.CHEST, context.t("gui.workshop.slots.output"), context.language.lines("gui.workshop.slots.output-help"))
    }

    protected fun selectableCards(
        context: GuiContext,
        actions: MutableMap<Int, GuiAction>,
        entries: List<DisplayEntry>
    ) {
        val page = paginate(entries, context.session.page, context.contentSlots.size)
        val offset = page.index * context.contentSlots.size
        page.items.forEachIndexed { localIndex, entry ->
            val absolute = offset + localIndex
            val selected = absolute == context.session.selectedIndex
            val slot = context.contentSlots[localIndex]
            context.put(
                slot,
                if (selected) Material.LIME_STAINED_GLASS_PANE else entry.material,
                entry.name,
                entry.lore + context.t(if (selected) "gui.workshop.position-selected" else "gui.workshop.position-select")
            )
            actions[slot] = GuiAction.SelectIndex(absolute)
        }
        addPaging(context, actions, page)
    }
}

class AffixWorkshopScreen : WorkshopScreen(GuiScreenId.AFFIX_WORKSHOP, "affix") {
    override fun render(context: GuiContext): ScreenRender {
        val actions = prepare(context)
        labels(context, "affix")
        val inspection = validInspection(context)
        val capacity = affixCapacity(context, target(context))
        val cards = (0 until capacity).map { index ->
            val affix = inspection.affixes.getOrNull(index)
            if (affix == null) DisplayEntry(Material.GRAY_STAINED_GLASS_PANE, context.t("gui.workshop.affix-position-empty", "index" to index + 1))
            else DisplayEntry(
                if (affix.locked) Material.GOLD_NUGGET else Material.NAME_TAG,
                context.t("gui.item.affix-name", "name" to context.genericName("affix", affix.key.toString()), "level" to affix.level),
                listOf(context.t(if (affix.locked) "gui.item.locked" else "gui.item.unlocked")) +
                    context.affixDescription(affix.key.toString(), affix.level, affix.parameters).map {
                        context.t("gui.item.affix-description", "description" to it)
                    }
            )
        }
        selectableCards(context, actions, cards)
        preview(context)
        listOf(
            "add" to "gui.workshop.affix-add",
            "replace" to "gui.workshop.affix-replace",
            "lock" to "gui.workshop.affix-lock",
            "remove" to "gui.workshop.affix-remove"
        ).forEach { (operation, key) ->
            val slot = context.slot(operation, 40)
            context.put(slot, if (operation == "remove") Material.RED_CONCRETE else Material.LIME_CONCRETE, context.t(key), listOf(context.t("gui.workshop.action-help")))
            actions[slot] = GuiAction.Transaction("affix", operation)
        }
        return ScreenRender(actions)
    }

    private fun affixCapacity(context: GuiContext, item: ItemStack): Int {
        if (item.type.isAir) return 1
        val overtureId = OvertureAPI.getOvertureId(item)
        return context.definitions.current().snapshot.affixPools.values.asSequence()
            .filter { it.matches(overtureId, item.type.name) }
            .sortedWith(compareByDescending<AffixPoolDefinition> {
                it.specificity(overtureId, item.type.name)
            }.thenByDescending { it.priority }.thenBy { it.id })
            .firstOrNull()?.maxAffixes ?: 1
    }
}

class SocketWorkshopScreen : WorkshopScreen(GuiScreenId.SOCKET_WORKSHOP, "socket") {
    override fun render(context: GuiContext): ScreenRender {
        val actions = prepare(context)
        labels(context, "socket", output = true)
        val slots = validInspection(context).sockets?.slots.orEmpty()
        selectableCards(context, actions, slots.map { socket ->
            DisplayEntry(
                when {
                    !socket.unlocked -> Material.IRON_BARS
                    socket.gem != null -> Material.EMERALD
                    else -> Material.LIGHT_BLUE_STAINED_GLASS_PANE
                },
                context.t("gui.workshop.socket-position", "index" to socket.index + 1),
                listOf(
                    context.t("gui.workshop.socket-categories", "value" to socket.accepts.joinToString { context.socketCategoryName(it) }),
                    when {
                        !socket.unlocked -> context.t("gui.item.socket-locked", "level" to (socket.unlockAtEnhancement ?: 0))
                        socket.gem != null -> context.t("gui.item.socket-gem", "name" to context.genericName("gem", requireNotNull(socket.gem).key.toString()))
                        else -> context.t("gui.item.socket-empty")
                    }
                )
            )
        })
        preview(context)
        listOf("insert" to "gui.workshop.socket-insert", "replace" to "gui.workshop.socket-replace", "drill" to "gui.workshop.socket-drill").forEach { (operation, key) ->
            val slot = context.slot(operation, 42)
            context.put(slot, Material.LIME_CONCRETE, context.t(key), listOf(context.t("gui.workshop.action-help")))
            actions[slot] = GuiAction.Transaction("socket", operation)
        }
        return ScreenRender(actions)
    }
}

class UnsocketWorkshopScreen : WorkshopScreen(GuiScreenId.UNSOCKET_WORKSHOP, "unsocket") {
    override fun render(context: GuiContext): ScreenRender {
        val actions = prepare(context)
        labels(context, "unsocket", output = true)
        val slots = validInspection(context).sockets?.slots.orEmpty()
        selectableCards(context, actions, slots.map { socket ->
            DisplayEntry(
                if (socket.gem == null) Material.GRAY_STAINED_GLASS_PANE else Material.EMERALD,
                context.t("gui.workshop.socket-position", "index" to socket.index + 1),
                listOf(
                    context.t("gui.workshop.socket-categories", "value" to socket.accepts.joinToString { context.socketCategoryName(it) }),
                    socket.gem?.let { context.t("gui.item.socket-gem", "name" to context.genericName("gem", it.key.toString())) }
                        ?: context.t("gui.item.socket-empty")
                )
            )
        })
        preview(context)
        val slot = context.slot("remove", 41)
        context.put(slot, Material.RED_CONCRETE, context.t("gui.workshop.socket-remove"), listOf(context.t("gui.workshop.action-help")))
        actions[slot] = GuiAction.Transaction("unsocket", "remove")
        return ScreenRender(actions)
    }
}

class EnhancementWorkshopScreen : WorkshopScreen(GuiScreenId.ENHANCEMENT_WORKSHOP, "enhancement") {
    override fun render(context: GuiContext): ScreenRender {
        val actions = prepare(context)
        context.put(context.slot("target-label", 10), Material.HOPPER, context.t("gui.workshop.slots.target"), context.language.lines("gui.workshop.slots.target-help"))
        context.put(context.slot("material-label", 12), Material.CHEST, context.t("gui.workshop.slots.material.enhancement"), context.language.lines("gui.workshop.slots.material-help.enhancement"))
        context.put(context.slot("downgrade-protection-label", 14), Material.TOTEM_OF_UNDYING, context.t("gui.workshop.slots.downgrade-protection"), context.language.lines("gui.workshop.slots.downgrade-protection-help"))
        context.put(context.slot("destroy-protection-label", 16), Material.NETHER_STAR, context.t("gui.workshop.slots.destroy-protection"), context.language.lines("gui.workshop.slots.destroy-protection-help"))
        preview(context)
        val slot = context.slot("confirm", 40)
        context.put(slot, Material.LIME_CONCRETE, context.t("gui.workshop.confirm"), listOf(context.t("gui.workshop.action-help")))
        actions[slot] = GuiAction.Transaction("enhancement")
        return ScreenRender(actions)
    }
}

class AdminDiagnosticsScreen : StandardScreen() {
    override val id = GuiScreenId.ADMIN_DIAGNOSTICS
    override fun render(context: GuiContext): ScreenRender {
        frame(context)
        val actions = navigationActions(context)
        if (!context.viewer.hasPermission("symphony.gui.admin")) {
            context.put(4, Material.BARRIER, context.t("permission.denied"))
            return ScreenRender(actions)
        }
        val state = context.store.state(context.target.uniqueId)
        context.put(4, Material.COMPARATOR, context.t("gui.admin.title"), listOf(
            context.t("gui.admin.uuid", "value" to context.target.uniqueId),
            context.t("gui.admin.snapshot-revision", "value" to state.snapshot.revision),
            context.t("gui.admin.definition-revision", "value" to state.snapshot.definitionRevision),
            context.t("gui.admin.sources", "value" to state.sources.size),
            context.t("gui.admin.cached-entities", "value" to context.store.size()),
            context.t("gui.admin.transactions", "value" to context.api.damage.recentTransactions(context.target, 10).size)
        ))
        val page = paginate(context.callbackFailures(), context.session.page, context.contentSlots.size)
        page.items.forEachIndexed { index, failure ->
            context.put(context.contentSlots[index], if (failure.enabled) Material.LIME_DYE else Material.RED_DYE, context.t("gui.admin.callback", "value" to failure.callbackId), listOf(
                context.t("gui.admin.enabled", "value" to context.t("booleans.${if (failure.enabled) "yes" else "no"}")),
                context.t("gui.admin.failures", "value" to failure.recentFailures),
                context.t("gui.admin.last-error", "value" to (failure.lastMessage ?: context.t("common.none")))
            ))
        }
        addPaging(context, actions, page)
        return ScreenRender(actions)
    }
}

internal data class Page<T>(val items: List<T>, val index: Int, val count: Int)

internal fun <T> paginate(items: List<T>, requested: Int, pageSize: Int): Page<T> {
    val pageCount = maxOf(1, (items.size + pageSize - 1) / pageSize)
    val index = requested.coerceIn(0, pageCount - 1)
    return Page(items.drop(index * pageSize).take(pageSize), index, pageCount)
}

internal fun StandardScreen.addPaging(context: GuiContext, actions: MutableMap<Int, GuiAction>, page: Page<*>) {
    context.session.page = page.index
    val previous = context.slot("previous", 45)
    val next = context.slot("next", 53)
    context.put(previous, Material.ARROW, context.t("gui.navigation.previous"), listOf(context.t("gui.navigation.page", "current" to page.index + 1, "total" to page.count)))
    context.put(next, Material.ARROW, context.t("gui.navigation.next"), listOf(context.t("gui.navigation.page", "current" to page.index + 1, "total" to page.count)))
    if (page.index > 0) actions[previous] = GuiAction.Page(-1)
    if (page.index + 1 < page.count) actions[next] = GuiAction.Page(1)
}
