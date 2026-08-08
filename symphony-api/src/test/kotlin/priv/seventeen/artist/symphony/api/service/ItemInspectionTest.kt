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

package priv.seventeen.artist.symphony.api.service

import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue
import priv.seventeen.artist.symphony.api.attribute.AttributeKey

class ItemInspectionTest {
    @Test
    fun `definition backed item can enter workshop before first mutation creates instance`() {
        val inspection = ItemInspection(
            overtureItemId = "prismatic_blade",
            instanceId = null,
            attributes = mapOf(AttributeKey.symphony("physical_damage") to 12.0),
            setIds = emptySet(),
            diagnostics = emptyList()
        )

        assertTrue(inspection.isSymphonyItem)
        assertTrue(inspection.isOvertureItem)
        assertTrue(inspection.supportsWorkshops)
    }

    @Test
    fun `unrelated item remains ineligible for workshops`() {
        val inspection = ItemInspection(
            overtureItemId = null,
            instanceId = null,
            attributes = emptyMap(),
            setIds = emptySet(),
            diagnostics = emptyList()
        )

        assertFalse(inspection.isSymphonyItem)
        assertFalse(inspection.isOvertureItem)
        assertFalse(inspection.supportsWorkshops)
    }

    @Test
    fun `offhand metadata alone identifies Symphony ownership without enabling unrelated workshops`() {
        val inspection = ItemInspection(
            overtureItemId = "offhand_token",
            instanceId = null,
            attributes = emptyMap(),
            setIds = emptySet(),
            diagnostics = emptyList(),
            symphonyDataPresent = true,
            offhandAllowed = true,
            offhandAttributeScale = 0.5
        )

        assertTrue(inspection.isOvertureItem)
        assertTrue(inspection.isSymphonyItem)
        assertFalse(inspection.supportsWorkshops)
    }
}
