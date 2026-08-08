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

import kotlin.test.Test
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue
import priv.seventeen.artist.symphony.bukkit.script.ConfiguredCallbackSchema

class ConfiguredCallbackSchemaTest {
    @Test
    fun `accepts percentage conditions placeholders and current Bukkit effects`() {
        ConfiguredCallbackSchema.validateConditions(
            "test",
            listOf(
                mapOf("type" to "chance", "value" to "25%"),
                mapOf(
                    "type" to "attribute",
                    "attribute" to "critical_chance",
                    "operator" to ">=",
                    "value" to "{threshold}",
                    "target" to "self"
                )
            )
        )
        ConfiguredCallbackSchema.validateActions(
            "test",
            listOf(
                mapOf("type" to "particle", "particle" to "CLOUD", "target" to "target"),
                mapOf("type" to "sound", "sound" to "block.amethyst_block.chime", "target" to "self")
            )
        )
    }

    @Test
    fun `rejects effects unavailable on the target Paper floor`() {
        val error = assertFailsWith<IllegalArgumentException> {
            ConfiguredCallbackSchema.validateActions(
                "test",
                listOf(mapOf("type" to "particle", "particle" to "SPLASH"))
            )
        }
        assertTrue(error.message.orEmpty().contains("不存在粒子效果"))
    }

    @Test
    fun `rejects unknown fields and runtime compiled script actions`() {
        assertFailsWith<IllegalArgumentException> {
            ConfiguredCallbackSchema.validateConditions(
                "test",
                listOf(mapOf("type" to "chance", "value" to 0.5, "unexpected" to true))
            )
        }
        assertFailsWith<IllegalArgumentException> {
            ConfiguredCallbackSchema.validateActions(
                "test",
                listOf(mapOf("type" to "script", "source" to "return true"))
            )
        }
    }
}
