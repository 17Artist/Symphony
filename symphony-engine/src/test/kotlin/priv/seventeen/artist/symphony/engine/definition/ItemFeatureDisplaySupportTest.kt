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

package priv.seventeen.artist.symphony.engine.definition

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class ItemFeatureDisplaySupportTest {
    @Test
    fun `affix descriptions resolve level snapshot parameters with explicit formats`() {
        val definition = GenericDefinition(
            id = "symphony:test_affix",
            sourcePath = "affixes/test.yml",
            values = mapOf(
                "display" to mapOf(
                    "description" to listOf(
                        "概率 {chance|percent}，伤害 {damage|number}，等级 {level|integer}"
                    )
                )
            )
        )

        assertEquals(
            listOf("概率 12.5%，伤害 18.75，等级 2"),
            definition.affixDescription(2, mapOf("chance" to 0.125, "damage" to 18.75))
        )
    }

    @Test
    fun `skill activation remains optional and parses all player input controls`() {
        val externalOnly = GenericDefinition("symphony:external", "skills/external.yml", emptyMap())
        assertNull(externalOnly.skillActivation())

        val interactive = GenericDefinition(
            "symphony:interactive",
            "skills/interactive.yml",
            mapOf(
                "activation" to mapOf(
                    "input" to "sneak_right_click",
                    "source" to "any",
                    "cancel-event" to "always",
                    "priority" to 42
                )
            )
        )
        assertEquals(
            SkillActivationDefinition(
                SkillActivationInput.SNEAK_RIGHT_CLICK,
                SkillActivationSource.ANY,
                SkillCancelPolicy.ALWAYS,
                42
            ),
            interactive.skillActivation()
        )
    }

    @Test
    fun `enhancement uses the latest configured threshold at or below the level`() {
        val definition = EnhancementDefinition(
            levels = mapOf(
                1 to EnhancementLevelDefinition(1, 1.0, 0.0, 0.0, 1.1),
                5 to EnhancementLevelDefinition(5, 1.0, 0.0, 0.0, 1.5),
                10 to EnhancementLevelDefinition(10, 1.0, 0.0, 0.0, 2.0)
            ),
            preventDestroyItem = null,
            preventDowngradeItem = null
        )

        assertEquals(1.0, definition.multiplierAt(0))
        assertEquals(1.1, definition.multiplierAt(4))
        assertEquals(1.5, definition.multiplierAt(9))
        assertEquals(2.0, definition.multiplierAt(10))
    }
}
