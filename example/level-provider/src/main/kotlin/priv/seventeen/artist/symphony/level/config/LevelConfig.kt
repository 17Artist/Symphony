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

package priv.seventeen.artist.symphony.level.config

import org.bukkit.configuration.file.YamlConfiguration
import org.bukkit.plugin.java.JavaPlugin
import priv.seventeen.artist.symphony.level.model.LevelCurve

data class LevelSettings(
    val providerPriority: Int,
    val providerDisplayName: String,
    val curve: LevelCurve
) {
    companion object {
        fun load(plugin: JavaPlugin): LevelSettings {
            val yaml = YamlConfiguration.loadConfiguration(plugin.dataFolder.resolve("config.yml"))
            val maximum = yaml.getInt("level-curve.maximum-level", 100)
            val base = yaml.getLong("level-curve.base-experience", 100L)
            val growth = yaml.getDouble("level-curve.growth-factor", 1.18)
            return LevelSettings(
                providerPriority = yaml.getInt("provider.priority", 100),
                providerDisplayName = yaml.getString("provider.display-name", "玩家等级")!!.trim().also { require(it.isNotEmpty()) },
                curve = LevelCurve(maximum, base, growth)
            )
        }
    }
}
