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

package priv.seventeen.artist.symphony.runes

import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import priv.seventeen.artist.symphony.api.attribute.AttributeKey
import priv.seventeen.artist.symphony.api.attribute.AttributeOperation
import priv.seventeen.artist.symphony.runes.model.PlayerRuneState
import priv.seventeen.artist.symphony.runes.model.RuneDefinition
import priv.seventeen.artist.symphony.runes.model.RuneModifierDefinition
import priv.seventeen.artist.symphony.runes.model.RuneSlotDefinition
import priv.seventeen.artist.symphony.runes.model.ScaledLevel
import priv.seventeen.artist.symphony.runes.model.ScaledValue
import priv.seventeen.artist.symphony.runes.storage.PlayerRuneRepository
import java.nio.file.Path
import java.util.UUID
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class RuneModelTest {
    private val rune = RuneDefinition(
        "ember",
        "余烬",
        listOf("测试"),
        "elemental",
        5,
        ScaledLevel(1, 4),
        listOf(RuneModifierDefinition("fire", AttributeKey.symphony("fire_damage"), AttributeOperation.ADD, ScaledValue(5.0, 2.0)))
    )

    @Test
    fun `rank scales level requirement and modifier value`() {
        assertEquals(13, rune.requiredLevel(4))
        assertEquals(11.0, rune.createModifiers(4).single().value)
        assertEquals("ember/fire/rank-4", rune.createModifiers(4).single().id)
    }

    @Test
    fun `slot category rules support multi category and wildcard`() {
        assertTrue(RuneSlotDefinition("focus", "专注", setOf("focus", "elemental")).accepts(rune))
        assertTrue(RuneSlotDefinition("wildcard", "通用", setOf("*")).accepts(rune))
        assertFalse(RuneSlotDefinition("defense", "守护", setOf("defense")).accepts(rune))
    }

    @Test
    fun `repository persists unlock rank and stable slot assignment`(@TempDir directory: Path) {
        val playerId = UUID.randomUUID()
        val first = PlayerRuneRepository(directory)
        first.update(playerId) { PlayerRuneState(mapOf("ember" to 4), mapOf("focus" to "ember")) }
        first.unload(playerId)

        val restored = PlayerRuneRepository(directory).load(playerId)
        assertEquals(4, restored.unlocked["ember"])
        assertEquals("ember", restored.equipped["focus"])
    }
}
