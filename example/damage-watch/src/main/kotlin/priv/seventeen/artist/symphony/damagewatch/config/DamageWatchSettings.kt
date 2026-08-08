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

package priv.seventeen.artist.symphony.damagewatch.config

import org.bukkit.configuration.file.YamlConfiguration
import org.bukkit.plugin.java.JavaPlugin

data class DamageWatchSettings(
    val notifyAttacker: Boolean,
    val notifyVictim: Boolean,
    val notifyConsole: Boolean,
    val chatDetails: Boolean,
    val actionBarSummary: Boolean,
    val confirmationMessage: Boolean,
    val missMessage: Boolean,
    val includeInactiveAttributes: Boolean,
    val includeMetadata: Boolean,
    val maximumChannels: Int,
    val maximumAttributes: Int,
    val decimals: Int
) {
    companion object {
        fun load(plugin: JavaPlugin): DamageWatchSettings {
            val yaml = YamlConfiguration.loadConfiguration(plugin.dataFolder.resolve("config.yml"))
            val maximumChannels = yaml.getInt("details.maximum-channels", 16)
            val maximumAttributes = yaml.getInt("details.maximum-attributes", 24)
            val decimals = yaml.getInt("details.decimals", 3)
            require(maximumChannels in 1..64) { "details.maximum-channels 必须位于 1 到 64 之间" }
            require(maximumAttributes in 1..128) { "details.maximum-attributes 必须位于 1 到 128 之间" }
            require(decimals in 0..6) { "details.decimals 必须位于 0 到 6 之间" }
            return DamageWatchSettings(
                notifyAttacker = yaml.getBoolean("output.notify-attacker", true),
                notifyVictim = yaml.getBoolean("output.notify-victim", true),
                notifyConsole = yaml.getBoolean("output.notify-console", true),
                chatDetails = yaml.getBoolean("output.chat-details", true),
                actionBarSummary = yaml.getBoolean("output.action-bar-summary", true),
                confirmationMessage = yaml.getBoolean("output.confirmation-message", true),
                missMessage = yaml.getBoolean("output.miss-message", true),
                includeInactiveAttributes = yaml.getBoolean("details.include-inactive-attributes", false),
                includeMetadata = yaml.getBoolean("details.include-metadata", true),
                maximumChannels = maximumChannels,
                maximumAttributes = maximumAttributes,
                decimals = decimals
            )
        }
    }
}
