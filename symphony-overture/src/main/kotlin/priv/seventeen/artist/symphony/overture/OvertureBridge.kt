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

package priv.seventeen.artist.symphony.overture

import org.bukkit.NamespacedKey
import org.bukkit.plugin.Plugin
import priv.seventeen.artist.overture.api.OvertureAPI
import priv.seventeen.artist.overture.api.registry.RegistrationHandle
import priv.seventeen.artist.symphony.engine.definition.DefinitionRepository
import priv.seventeen.artist.symphony.engine.config.LanguageBundle
import priv.seventeen.artist.symphony.engine.config.ItemDisplayFormats
import priv.seventeen.artist.symphony.overture.component.AttributeComponentCodec
import priv.seventeen.artist.symphony.overture.component.OffhandComponentCodec
import priv.seventeen.artist.symphony.overture.component.SetComponentCodec
import priv.seventeen.artist.symphony.overture.component.SkillComponentCodec
import priv.seventeen.artist.symphony.overture.component.SocketComponentCodec
import priv.seventeen.artist.symphony.overture.render.SymphonyRenderers
import java.util.concurrent.CopyOnWriteArrayList

class OvertureBridge(
    private val plugin: Plugin,
    private val definitions: DefinitionRepository,
    private val language: () -> LanguageBundle,
    private val displayFormats: () -> ItemDisplayFormats,
    private val activeSetCount: (org.bukkit.entity.Player, String) -> Int
) : AutoCloseable {
    private val handles = CopyOnWriteArrayList<RegistrationHandle>()

    @Synchronized
    fun register() {
        check(handles.isEmpty()) { "Overture 桥接层已经注册" }
        val created = mutableListOf<RegistrationHandle>()
        try {
            created += OvertureAPI.registerItemComponent(
                plugin,
                NamespacedKey(plugin, "attributes"),
                0,
                AttributeComponentCodec(definitions, language)
            )
            created += OvertureAPI.registerItemComponent(
                plugin,
                NamespacedKey(plugin, "sockets"),
                0,
                SocketComponentCodec(language)
            )
            created += OvertureAPI.registerItemComponent(
                plugin,
                NamespacedKey(plugin, "set"),
                0,
                SetComponentCodec(definitions, language)
            )
            created += OvertureAPI.registerItemComponent(
                plugin,
                NamespacedKey(plugin, "skills"),
                0,
                SkillComponentCodec(definitions, language)
            )
            created += OvertureAPI.registerItemComponent(
                plugin,
                NamespacedKey(plugin, "offhand"),
                0,
                OffhandComponentCodec(language)
            )
            created += SymphonyRenderers(plugin, definitions, language, displayFormats, activeSetCount).registerAll()
            handles += created
        } catch (error: Throwable) {
            created.asReversed().forEach { runCatching { it.close() } }
            throw error
        }
    }

    override fun close() {
        handles.asReversed().forEach { runCatching { it.close() } }
        handles.clear()
    }
}
