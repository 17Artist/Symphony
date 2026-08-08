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

import java.nio.file.Path
import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue
import priv.seventeen.artist.symphony.engine.config.ItemDisplayFormats
import priv.seventeen.artist.symphony.engine.config.LanguageBundle
import priv.seventeen.artist.symphony.engine.definition.SetBonusDefinition
import priv.seventeen.artist.symphony.engine.definition.SetBonusDisplayDefinition
import priv.seventeen.artist.symphony.engine.definition.SetDefinition
import priv.seventeen.artist.symphony.engine.definition.SetDisplayDefinition

class SymphonySetLoreTest {
    private val assetRoot = Path.of("..", "symphony-bukkit", "src", "main", "resources", "assets")
        .toAbsolutePath().normalize()
    private val language = LanguageBundle.load(assetRoot.resolve("language.yml"))
    private val formats = ItemDisplayFormats.load(assetRoot.resolve("display.yml"))
    private val definition = SetDefinition(
        id = "symphony:test_internal_id",
        duplicateInstanceOnce = true,
        allowDuplicatePieceId = false,
        bonuses = linkedMapOf(
            2 to SetBonusDefinition(
                2,
                emptyList(),
                emptyList(),
                SetBonusDisplayDefinition("两件效果", listOf("获得基础防御"))
            ),
            4 to SetBonusDefinition(
                4,
                emptyList(),
                emptyList(),
                SetBonusDisplayDefinition("四件效果", listOf("获得元素反制"))
            )
        ),
        display = SetDisplayDefinition("测试套装", mapOf("helm_internal" to "测试头盔"))
    )

    @Test
    fun `renders active and inactive thresholds from wearer count without leaking ids`() {
        val lines = composeSetLore(definition, "helm_internal", 3, language, formats)
        assertTrue(lines.any { "✔ 两件效果" in it && "3/2" in it })
        assertTrue(lines.any { "○ 四件效果" in it && "3/4" in it })
        assertTrue(lines.any { "获得基础防御" in it })
        assertTrue(lines.any { "获得元素反制" in it })
        assertFalse(lines.any { "test_internal_id" in it || "helm_internal" in it })
    }

    @Test
    fun `template render without player does not pretend a threshold is active`() {
        val lines = composeSetLore(definition, "helm_internal", null, language, formats)
        assertTrue(lines.count { "穿戴后显示进度" in it } == 2)
        assertFalse(lines.any { "✔" in it })
    }
}
