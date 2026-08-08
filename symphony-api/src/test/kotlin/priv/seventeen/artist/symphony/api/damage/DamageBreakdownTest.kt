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

package priv.seventeen.artist.symphony.api.damage

import java.util.UUID
import kotlin.system.measureNanoTime
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import kotlin.test.assertFalse
import priv.seventeen.artist.symphony.api.attribute.AttributeKey

class DamageBreakdownTest {
    private val criticalChance = AttributeKey.symphony("critical_chance")
    private val fireResistance = AttributeKey.symphony("fire_resistance")

    @Test
    fun `precomputes channel relation critical and activated attribute views`() {
        val uses = listOf(
            use(criticalChance, DamageAttributeOwner.ATTACKER, DamageAttributeRole.CRITICAL_CHANCE, 0.25, true),
            use(fireResistance, DamageAttributeOwner.VICTIM, DamageAttributeRole.RESISTANCE, 0.10, true, "fire")
        )
        val breakdown = DamageBreakdown(
            listOf(
                channel("fire", 30.0, 45.0, DamageRelation.ADVANTAGED, critical = true),
                channel("ice", 20.0, 12.0, DamageRelation.DISADVANTAGED),
                channel("arcane", 10.0, 8.0, DamageRelation.NEUTRAL)
            ),
            uses
        )

        assertEquals(45.0, breakdown.advantagedDamage)
        assertEquals(12.0, breakdown.disadvantagedDamage)
        assertEquals(8.0, breakdown.neutralDamage)
        assertEquals(45.0, breakdown.total(DamageRelation.ADVANTAGED))
        assertEquals("fire", breakdown.channel("fire")?.channel)
        assertEquals(setOf(criticalChance, fireResistance), breakdown.triggeredAttributes)
        assertTrue(breakdown.critical)
    }

    @Test
    fun `cached event queries remain constant time under repeated listener access`() {
        val channels = (0 until 32).map { index ->
            channel("channel_$index", 10.0 + index, 5.0 + index, DamageRelation.values()[index % 3])
        }
        val breakdown = DamageBreakdown(channels, emptyList())
        var checksum = 0.0
        val elapsed = measureNanoTime {
            repeat(500_000) { index ->
                checksum += breakdown.channel("channel_${index and 31}")!!.finalAmount
                checksum += breakdown.total(DamageRelation.values()[index % 3])
                if (breakdown.critical) checksum += 1.0
            }
        }
        assertTrue(checksum > 0.0)
        assertTrue(elapsed < 5_000_000_000L, "500,000 cached breakdown queries took ${elapsed / 1_000_000} ms")
    }

    @Test
    fun `missed outcome is distinct from a generic cancellation`() {
        val result = DamageResult(
            transactionId = UUID.randomUUID(),
            parentTransactionId = null,
            state = DamageResultState.CANCELLED,
            requested = listOf(DamageChannelAmount("physical", 10.0)),
            applied = emptyList(),
            finalDamage = 0.0,
            critical = false,
            reason = "Damage missed",
            outcome = DamageOutcome.MISSED
        )

        assertTrue(result.missed)
        assertFalse(result.hit)
        assertEquals(DamageOutcome.MISSED, result.outcome)
    }

    private fun channel(
        id: String,
        requested: Double,
        finalAmount: Double,
        relation: DamageRelation,
        critical: Boolean = false
    ) = DamageChannelResult(id, requested, requested, requested, finalAmount, finalAmount, relation, critical)

    private fun use(
        key: AttributeKey,
        owner: DamageAttributeOwner,
        role: DamageAttributeRole,
        value: Double,
        activated: Boolean,
        channel: String? = null
    ) = DamageAttributeUse(owner, UUID.randomUUID(), key, role, value, channel, activated)
}
