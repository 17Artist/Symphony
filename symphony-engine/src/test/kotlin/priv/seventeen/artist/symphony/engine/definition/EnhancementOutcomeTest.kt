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

class EnhancementOutcomeTest {
    private val rule = EnhancementLevelDefinition(
        level = 10,
        successChance = 0.20,
        downgradeChance = 0.30,
        destroyChance = 0.10,
        multiplier = 1.8
    )

    @Test
    fun `probability intervals resolve without falling through`() {
        assertEquals(EnhancementOutcome.SUCCESS, rule.resolveOutcome(0.10))
        assertEquals(EnhancementOutcome.DESTROY, rule.resolveOutcome(0.25))
        assertEquals(EnhancementOutcome.DOWNGRADE, rule.resolveOutcome(0.45))
        assertEquals(EnhancementOutcome.STAY, rule.resolveOutcome(0.90))
    }

    @Test
    fun `protection converts only its matching failure to stay`() {
        assertEquals(EnhancementOutcome.STAY, rule.resolveOutcome(0.25, preventDestroy = true))
        assertEquals(EnhancementOutcome.DOWNGRADE, rule.resolveOutcome(0.45, preventDestroy = true))
        assertEquals(EnhancementOutcome.STAY, rule.resolveOutcome(0.45, preventDowngrade = true))
        assertEquals(EnhancementOutcome.DESTROY, rule.resolveOutcome(0.25, preventDowngrade = true))
    }
}
