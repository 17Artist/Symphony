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

package priv.seventeen.artist.symphony.level

import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import priv.seventeen.artist.symphony.level.model.LevelCurve
import priv.seventeen.artist.symphony.level.model.PlayerProgress
import priv.seventeen.artist.symphony.level.storage.PlayerProgressRepository
import java.nio.file.Path
import java.util.UUID
import kotlin.test.assertEquals
import kotlin.test.assertNull

class LevelCurveTest {
    @Test
    fun `experience crosses multiple levels and preserves remainder`() {
        val curve = LevelCurve(10, 100, 2.0)
        val change = curve.addExperience(PlayerProgress(1, 50), 300)
        assertEquals(3, change.progress.level)
        assertEquals(50, change.progress.experience)
        assertEquals(2, change.levelsGained)
        assertEquals(0, change.discardedExperience)
    }

    @Test
    fun `maximum level discards overflow and exposes no next threshold`() {
        val curve = LevelCurve(2, 100, 1.0)
        val change = curve.addExperience(PlayerProgress(1, 0), 150)
        assertEquals(2, change.progress.level)
        assertEquals(0, change.progress.experience)
        assertEquals(50, change.discardedExperience)
        assertNull(curve.experienceForNextLevel(2))
    }

    @Test
    fun `repository round trips one progress record atomically`(@TempDir directory: Path) {
        val playerId = UUID.randomUUID()
        val curve = LevelCurve(100, 100, 1.18)
        val first = PlayerProgressRepository(directory) { curve }
        first.update(playerId) { PlayerProgress(20, 42) }
        first.unload(playerId)

        val second = PlayerProgressRepository(directory) { curve }
        val restored = second.load(playerId)
        assertEquals(20, restored.level)
        assertEquals(42, restored.experience)
    }
}
