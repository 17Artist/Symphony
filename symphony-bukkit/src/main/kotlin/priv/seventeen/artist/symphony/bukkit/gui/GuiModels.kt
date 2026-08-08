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

import org.bukkit.Bukkit
import org.bukkit.inventory.Inventory
import org.bukkit.inventory.InventoryHolder
import org.bukkit.inventory.ItemStack
import priv.seventeen.artist.symphony.api.attribute.AttributeKey
import java.util.EnumMap
import java.util.UUID

enum class GuiScreenId(val alias: String) {
    ATTRIBUTE_BROWSER("attributes"),
    ATTRIBUTE_EXPLAIN("detail"),
    AFFIX_WORKSHOP("affix"),
    SOCKET_WORKSHOP("socket"),
    UNSOCKET_WORKSHOP("unsocket"),
    ENHANCEMENT_WORKSHOP("enhance"),
    ADMIN_DIAGNOSTICS("admin");

    companion object {
        fun fromAlias(value: String): GuiScreenId? = values().firstOrNull {
            it.alias.equals(value, true) || it.name.equals(value, true)
        }
    }
}

enum class AttributeSection {
    ATTRIBUTES,
    SOURCES,
    SKILLS,
    SETS,
    STATUS
}

enum class WorkshopSlotRole(val configKey: String, val defaultSlot: Int) {
    TARGET("target-slot", 10),
    MATERIAL("material-slot", 19),
    DOWNGRADE_PROTECTION("downgrade-protection-slot", 23),
    DESTROY_PROTECTION("destroy-protection-slot", 25),
    OUTPUT("output-slot", 28)
}

data class GuiSession(
    val token: UUID,
    val viewerId: UUID,
    val targetId: UUID,
    var screen: GuiScreenId,
    var page: Int = 0,
    var filter: String = "",
    var section: AttributeSection = AttributeSection.ATTRIBUTES,
    var selectedIndex: Int = 0,
    var snapshotRevision: Long = -1,
    var transactionToken: UUID? = null,
    var transitioning: Boolean = false,
    val stagedItems: MutableMap<WorkshopSlotRole, ItemStack> = EnumMap(WorkshopSlotRole::class.java),
    val openedAtMillis: Long = System.currentTimeMillis()
)

class GuiInventoryHolder(
    val sessionToken: UUID,
    val screen: GuiScreenId
) : InventoryHolder {
    private lateinit var backing: Inventory

    fun create(size: Int, title: String): Inventory = Bukkit.createInventory(this, size, title).also { backing = it }
    override fun getInventory(): Inventory = backing
}

sealed interface GuiAction {
    data class Explain(val attribute: AttributeKey) : GuiAction
    data class SelectSection(val section: AttributeSection) : GuiAction
    data class SelectIndex(val index: Int) : GuiAction
    data class Page(val delta: Int) : GuiAction
    object Close : GuiAction
    data class Transaction(val type: String, val operation: String = "default") : GuiAction
}

data class ScreenRender(
    val actions: Map<Int, GuiAction> = emptyMap()
)

internal val ITEM_INTERACTION_SCREENS = setOf(
    GuiScreenId.AFFIX_WORKSHOP,
    GuiScreenId.SOCKET_WORKSHOP,
    GuiScreenId.UNSOCKET_WORKSHOP,
    GuiScreenId.ENHANCEMENT_WORKSHOP
)

internal fun GuiScreenId.workshopRoles(): Set<WorkshopSlotRole> = when (this) {
    GuiScreenId.AFFIX_WORKSHOP -> setOf(WorkshopSlotRole.TARGET, WorkshopSlotRole.MATERIAL)
    GuiScreenId.SOCKET_WORKSHOP -> setOf(WorkshopSlotRole.TARGET, WorkshopSlotRole.MATERIAL, WorkshopSlotRole.OUTPUT)
    GuiScreenId.UNSOCKET_WORKSHOP -> setOf(WorkshopSlotRole.TARGET, WorkshopSlotRole.MATERIAL, WorkshopSlotRole.OUTPUT)
    GuiScreenId.ENHANCEMENT_WORKSHOP -> setOf(
        WorkshopSlotRole.TARGET,
        WorkshopSlotRole.MATERIAL,
        WorkshopSlotRole.DOWNGRADE_PROTECTION,
        WorkshopSlotRole.DESTROY_PROTECTION
    )
    else -> emptySet()
}
