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

package priv.seventeen.artist.symphony.bukkit.script

import java.lang.reflect.Proxy
import java.util.UUID
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import org.bukkit.entity.LivingEntity
import priv.seventeen.artist.symphony.engine.attribute.AttributeStateStore
import priv.seventeen.artist.symphony.engine.config.FeatureSettings
import priv.seventeen.artist.symphony.engine.definition.DefinitionRepository
import priv.seventeen.artist.symphony.engine.definition.DefinitionSnapshot
import priv.seventeen.artist.symphony.engine.trigger.EntityTriggerContext

class DefaultCallbackActivationResolverTest {
    private val definitions = DefinitionRepository(DefinitionSnapshot.empty())
    private val store = AttributeStateStore(definitions)
    private val resolver = DefaultCallbackActivationResolver(
        store,
        FeatureSettings(
            affixes = true,
            skills = true,
            gems = true,
            sockets = true,
            enhancement = true,
            interactions = true,
            elements = true,
            resonances = true,
            talents = true,
            statuses = true,
            environments = true
        )
    )
    private val entity = entity(UUID.randomUUID())
    private val owner = CallbackOwner(CallbackOwnerKind.ATTRIBUTE, "symphony:arcane_resistance")

    @Test
    fun `attribute callbacks on combat triggers are active and defer filtering to conditions`() {
        val variables = resolver.variables(owner, context(emptyMap()))
        assertNotNull(variables)
        assertEquals("symphony:arcane_resistance", variables["attributeId"])
    }

    @Test
    fun `attribute calculate callback only runs for the attribute being calculated`() {
        assertNotNull(resolver.variables(owner, context(mapOf("attribute" to "symphony:arcane_resistance"))))
        assertNull(resolver.variables(owner, context(mapOf("attribute" to "symphony:fire_resistance"))))
    }

    private fun context(values: Map<String, Any?>) =
        EntityTriggerContext(UUID.randomUUID(), entity, null, 1L, values)

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
