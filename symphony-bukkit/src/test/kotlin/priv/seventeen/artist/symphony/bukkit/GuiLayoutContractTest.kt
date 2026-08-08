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

package priv.seventeen.artist.symphony.bukkit

import java.nio.file.Path
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue
import priv.seventeen.artist.symphony.bukkit.gui.GuiLayout
import priv.seventeen.artist.symphony.bukkit.gui.GuiLayoutRepository
import priv.seventeen.artist.symphony.bukkit.gui.GuiScreenId
import priv.seventeen.artist.symphony.bukkit.gui.WorkshopSlotRole
import priv.seventeen.artist.symphony.bukkit.gui.attributeExplainContributionSlots
import priv.seventeen.artist.symphony.bukkit.gui.workshopSlot
import priv.seventeen.artist.symphony.bukkit.gui.validateWorkshopLayout
import priv.seventeen.artist.symphony.bukkit.gui.workshopRoles

class GuiLayoutContractTest {
    @Test
    fun `only independent menu screens remain`() {
        assertEquals(
            setOf("attributes", "detail", "affix", "socket", "unsocket", "enhance", "admin"),
            GuiScreenId.values().mapTo(linkedSetOf()) { it.alias }
        )
        assertEquals(null, GuiScreenId.fromAlias("main"))
        assertEquals(null, GuiScreenId.fromAlias("item"))
    }

    @Test
    fun `attribute summary slot cannot be overwritten by source contributions`() {
        val assets = Path.of("src", "main", "resources", "assets").toAbsolutePath().normalize()
        val layout = GuiLayoutRepository.load(assets).layout(GuiScreenId.ATTRIBUTE_EXPLAIN)
        val summarySlot = layout.slots.getValue("summary").slot

        assertEquals(4, summarySlot)
        assertFalse(summarySlot in layout.pageSlots, "the detail header must stay outside contribution slots")
        assertFalse(
            summarySlot in attributeExplainContributionSlots(layout, summarySlot),
            "attribute source contributions must reserve the summary card slot"
        )
    }

    @Test
    fun `workshop inputs are distinct real container slots outside navigation`() {
        val assets = Path.of("src", "main", "resources", "assets").toAbsolutePath().normalize()
        val layout = GuiLayoutRepository.load(assets).layout(GuiScreenId.ENHANCEMENT_WORKSHOP)
        val roles = GuiScreenId.ENHANCEMENT_WORKSHOP.workshopRoles()
        val slots = roles.associateWith(layout::workshopSlot)

        assertEquals(19, slots.getValue(WorkshopSlotRole.TARGET))
        assertEquals(21, slots.getValue(WorkshopSlotRole.MATERIAL))
        assertEquals(23, slots.getValue(WorkshopSlotRole.DOWNGRADE_PROTECTION))
        assertEquals(25, slots.getValue(WorkshopSlotRole.DESTROY_PROTECTION))
        assertEquals(4, slots.values.toSet().size)
        assertTrue(slots.values.none { it in setOf(45, 48, 49, 50, 53) })
        assertTrue(layout.pageSlots.none { it in slots.values })
    }

    @Test
    fun `workshop layout rejects overlapping configurable slots`() {
        val layout = GuiLayout(
            titleKey = "gui.titles.enhance",
            rows = 6,
            refreshTicks = 20,
            pageSlots = listOf(28, 29, 30, 31, 32, 33, 34),
            slots = emptyMap(),
            scalarSlots = mapOf("target-slot" to 19, "material-slot" to 19)
        )

        assertFailsWith<IllegalArgumentException> { validateWorkshopLayout(layout, 54) }
    }
}
