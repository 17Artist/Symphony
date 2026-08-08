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

package priv.seventeen.artist.symphony.bukkit.service

import java.lang.reflect.Proxy
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import org.bukkit.NamespacedKey
import org.bukkit.plugin.Plugin

class RegistrationSupportTest {
    @Test
    fun `disabled owners and shutdown release registry entries`() {
        val first = plugin("First")
        val second = plugin("Second")
        val registry = OwnedPriorityRegistry<String>("pressure-registry")
        var changes = 0
        registry.register(first, NamespacedKey("first", "value"), 0, "first") { changes++ }
        registry.register(second, NamespacedKey("second", "value"), 0, "second") { changes++ }
        assertEquals(2, registry.active().size)

        registry.closeOwner(first) { changes++ }
        assertEquals(listOf("second"), registry.active().map { it.value })
        assertEquals(3, changes)

        registry.clear()
        assertTrue(registry.active().isEmpty())
    }

    companion object {
        private fun plugin(name: String): Plugin = Proxy.newProxyInstance(
            Plugin::class.java.classLoader,
            arrayOf(Plugin::class.java)
        ) { _, method, _ ->
            when (method.name) {
                "getName" -> name
                else -> when (method.returnType) {
                    java.lang.Boolean.TYPE -> false
                    java.lang.Integer.TYPE -> 0
                    java.lang.Long.TYPE -> 0L
                    java.lang.Double.TYPE -> 0.0
                    else -> null
                }
            }
        } as Plugin
    }
}
