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

package priv.seventeen.artist.symphony.bukkit.integration

import org.bukkit.Bukkit
import priv.seventeen.artist.blink.BlinkLog
import priv.seventeen.artist.symphony.api.service.SymphonyApi
import org.bukkit.plugin.Plugin
import priv.seventeen.artist.symphony.bukkit.runtime.SymphonyRuntime
import java.util.function.BiConsumer
import java.util.function.Consumer

object OptionalIntegrations {
    private var papiInstalled = false
    private var mythicInstalled = false

    fun installPlaceholderApi(api: SymphonyApi, version: String): Boolean {
        val dependency = Bukkit.getPluginManager().getPlugin("PlaceholderAPI")
        if (dependency == null || !dependency.isEnabled) return false
        return try {
            val type = Class.forName("priv.seventeen.artist.symphony.integrations.papi.PlaceholderApiIntegration")
            papiInstalled = type.getMethod("install", SymphonyApi::class.java, String::class.java)
                .invoke(null, api, version) as Boolean
            if (papiInstalled) BlinkLog.success(language("console.integration-installed", "integration" to language("console.integrations.placeholderapi")))
            else BlinkLog.error(language("console.integration-rejected", "integration" to language("console.integrations.placeholderapi")))
            papiInstalled
        } catch (error: Throwable) {
            BlinkLog.error(language("console.integration-failed", "integration" to language("console.integrations.placeholderapi")), error)
            false
        }
    }

    fun installMythicMobs(plugin: Plugin, api: SymphonyApi): Boolean {
        val dependency = Bukkit.getPluginManager().getPlugin("MythicMobs")
        if (dependency == null || !dependency.isEnabled) return false
        return try {
            val type = Class.forName("priv.seventeen.artist.symphony.integrations.mythic.MythicMobsIntegration")
            val rejected = BiConsumer<String, String> { mob, reason ->
                BlinkLog.error(language("console.mythic-attributes-rejected", "mob" to mob, "reason" to reason))
            }
            val removed = Consumer<java.util.UUID>(SymphonyRuntime::forgetEntity)
            type.getMethod("install", Plugin::class.java, SymphonyApi::class.java, BiConsumer::class.java, Consumer::class.java)
                .invoke(null, plugin, api, rejected, removed)
            mythicInstalled = true
            BlinkLog.success(language("console.integration-installed", "integration" to language("console.integrations.mythicmobs")))
            true
        } catch (error: Throwable) {
            BlinkLog.error(language("console.integration-failed", "integration" to language("console.integrations.mythicmobs")), error)
            false
        }
    }

    fun close() {
        if (papiInstalled) runCatching {
            Class.forName("priv.seventeen.artist.symphony.integrations.papi.PlaceholderApiIntegration")
                .getMethod("uninstall").invoke(null)
        }.onFailure { BlinkLog.error(language("console.integration-close-failed", "integration" to language("console.integrations.placeholderapi")), it) }
        papiInstalled = false
        if (mythicInstalled) runCatching {
            Class.forName("priv.seventeen.artist.symphony.integrations.mythic.MythicMobsIntegration")
                .getMethod("uninstall").invoke(null)
        }.onFailure {
            BlinkLog.error(language("console.integration-close-failed", "integration" to language("console.integrations.mythicmobs")), it)
        }
        mythicInstalled = false
    }

    private fun language(key: String, vararg variables: Pair<String, Any?>): String =
        SymphonyRuntime.language().text(key, *variables)
}
