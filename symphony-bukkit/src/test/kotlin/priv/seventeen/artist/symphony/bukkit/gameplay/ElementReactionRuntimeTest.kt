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

package priv.seventeen.artist.symphony.bukkit.gameplay

import java.lang.reflect.Proxy
import java.time.Instant
import java.util.UUID
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import org.bukkit.entity.LivingEntity
import priv.seventeen.artist.symphony.api.damage.DamageChannelAmount
import priv.seventeen.artist.symphony.api.damage.DamageRequest
import priv.seventeen.artist.symphony.engine.definition.DamageChannelDefinition
import priv.seventeen.artist.symphony.engine.definition.DefinitionRepository
import priv.seventeen.artist.symphony.engine.definition.DefinitionSnapshot
import priv.seventeen.artist.symphony.engine.definition.GenericDefinition

class ElementReactionRuntimeTest {
    private var now = 1_000L
    private val victim = entity(UUID.randomUUID())
    private val definitions = DefinitionRepository(snapshot())
    private val runtime = ElementReactionRuntime(definitions, clock = { now })

    @Test
    fun `aura and gauge mutations happen only after confirmed commit`() {
        val lightning = runtime.prepare(request("lightning", 4.0), victim, mapOf("lightning" to 4.0))
        assertEquals(mapOf("lightning" to 4.0), lightning.channels)
        assertTrue(runtime.snapshots(victim).isEmpty(), "prepare must not attach an aura")

        lightning.commitConfirmed()
        assertEquals(1.0, runtime.snapshots(victim).single { it.channel == "lightning" }.gauge)

        val fire = runtime.prepare(request("fire", 10.0), victim, mapOf("fire" to 10.0))
        assertEquals(25.0, fire.channels.getValue("fire"))
        assertEquals(1.0, runtime.snapshots(victim).single { it.channel == "lightning" }.gauge,
            "preview must not consume the existing aura")

        fire.commitConfirmed()
        val committed = runtime.snapshots(victim).associate { it.channel to it.gauge }
        assertEquals(0.5, committed.getValue("lightning"))
        assertEquals(1.0, committed.getValue("fire"))
    }

    @Test
    fun `discarded reaction preview leaves aura unchanged`() {
        runtime.prepare(request("lightning", 1.0), victim, mapOf("lightning" to 1.0)).commitConfirmed()
        val preview = runtime.prepare(request("fire", 8.0), victim, mapOf("fire" to 8.0))
        assertEquals(20.0, preview.channels.getValue("fire"))
        assertEquals(mapOf("lightning" to 1.0), runtime.snapshots(victim).associate { it.channel to it.gauge })
    }

    @Test
    fun `expired auras cannot trigger an element advantage`() {
        runtime.prepare(request("lightning", 1.0), victim, mapOf("lightning" to 1.0)).commitConfirmed()
        assertEquals(1, runtime.trackedEntityCount())
        now += 10_001L
        assertTrue(runtime.snapshots(victim).isEmpty())
        assertEquals(8.0, runtime.prepare(request("fire", 8.0), victim, mapOf("fire" to 8.0)).channels.getValue("fire"))
        runtime.maintenance(now)
        assertEquals(0, runtime.trackedEntityCount())
    }

    @Test
    fun `effect failure is reported without rolling back confirmed aura state`() {
        val failures = mutableListOf<String>()
        val isolated = ElementReactionRuntime(
            DefinitionRepository(snapshot(mapOf("particle" to "NOT_A_REAL_PARTICLE"))),
            clock = { now },
            onEffectFailure = { reaction, _ -> failures += reaction }
        )
        isolated.prepare(request("lightning", 1.0), victim, mapOf("lightning" to 1.0)).commitConfirmed()
        isolated.prepare(request("fire", 10.0), victim, mapOf("fire" to 10.0)).commitConfirmed()
        assertEquals(listOf("symphony:overload"), failures)
        assertEquals(
            mapOf("fire" to 1.0, "lightning" to 0.5),
            isolated.snapshots(victim).associate { it.channel to it.gauge }
        )
    }

    private fun request(channel: String, amount: Double) = DamageRequest(
        null,
        victim,
        listOf(DamageChannelAmount(channel, amount)),
        "reaction-test",
        null,
        false,
        emptyMap()
    )

    private fun snapshot(effects: Map<String, Any?> = emptyMap()): DefinitionSnapshot {
        val channels = listOf("fire", "lightning").associateWith { id ->
            DamageChannelDefinition(id, id, null, null, null, null, false, true, null)
        }
        val overload = GenericDefinition(
            "symphony:overload",
            "test",
            mapOf(
                "trigger" to "fire",
                "aura" to "lightning",
                "type" to "amplify",
                "multiplier" to 2.5,
                "gauge-consume" to 0.5,
                "effects" to effects
            )
        )
        return DefinitionSnapshot.empty().copy(
            revision = 1,
            createdAt = Instant.EPOCH,
            damageChannels = channels,
            reactions = mapOf(overload.id to overload)
        )
    }

    companion object {
        private fun entity(id: UUID): LivingEntity = Proxy.newProxyInstance(
            LivingEntity::class.java.classLoader,
            arrayOf(LivingEntity::class.java)
        ) { _, method, _ ->
            when (method.name) {
                "getUniqueId" -> id
                else -> when (method.returnType) {
                    java.lang.Boolean.TYPE -> false
                    java.lang.Integer.TYPE -> 0
                    java.lang.Long.TYPE -> 0L
                    java.lang.Double.TYPE -> 0.0
                    java.lang.Float.TYPE -> 0f
                    else -> null
                }
            }
        } as LivingEntity
    }
}
