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

import org.bukkit.Bukkit
import org.bukkit.NamespacedKey
import org.bukkit.entity.LivingEntity
import org.bukkit.entity.Player
import org.bukkit.plugin.java.JavaPlugin
import priv.seventeen.artist.blink.BlinkLog
import priv.seventeen.artist.symphony.api.level.LevelProvider
import priv.seventeen.artist.symphony.api.level.ProvidedLevel
import priv.seventeen.artist.symphony.api.registration.RegistrationHandle
import priv.seventeen.artist.symphony.api.service.SymphonyApi
import priv.seventeen.artist.symphony.level.config.LevelSettings
import priv.seventeen.artist.symphony.level.model.ExperienceChange
import priv.seventeen.artist.symphony.level.model.PlayerProgress
import priv.seventeen.artist.symphony.level.storage.PlayerProgressRepository
import priv.seventeen.artist.symphony.level.text.Messages
import java.util.UUID

object LevelRuntime {
    private lateinit var plugin: JavaPlugin
    private lateinit var api: SymphonyApi
    private lateinit var settings: LevelSettings
    private lateinit var messages: Messages
    private lateinit var repository: PlayerProgressRepository
    private var providerRegistration: RegistrationHandle? = null
    private var enabled = false

    fun load(plugin: JavaPlugin) {
        this.plugin = plugin
        saveDefault("config.yml")
        saveDefault("language.yml")
    }

    fun enable() {
        api = Bukkit.getServicesManager().load(SymphonyApi::class.java)
            ?: error("SymphonyApi 服务不可用")
        settings = LevelSettings.load(plugin)
        messages = Messages(plugin.dataFolder.resolve("language.yml"))
        repository = PlayerProgressRepository(plugin.dataFolder.toPath().resolve("players")) { settings.curve }
        providerRegistration = registerProvider(settings)
        enabled = true
        Bukkit.getOnlinePlayers().forEach(::loadPlayer)
        BlinkLog.success(messages.text("console.enabled", "priority" to settings.providerPriority))
    }

    fun disable() {
        if (!enabled) return
        providerRegistration?.close()
        providerRegistration = null
        Bukkit.getOnlinePlayers().forEach { api.levels.refresh(it, "symphonylevel:disable") }
        repository.saveAll()
        enabled = false
        BlinkLog.info(messages.text("console.disabled"))
    }

    fun reload(): Result<Unit> = runCatching {
        val candidate = LevelSettings.load(plugin)
        val previous = settings
        providerRegistration?.close()
        providerRegistration = null
        try {
            providerRegistration = registerProvider(candidate)
            settings = candidate
        } catch (error: Throwable) {
            providerRegistration = registerProvider(previous)
            throw error
        }
        messages.reload()
        Bukkit.getOnlinePlayers().forEach { api.levels.refresh(it, "symphonylevel:reload") }
    }

    fun loadPlayer(player: Player) {
        repository.load(player.uniqueId)
        api.levels.refresh(player, "symphonylevel:join")
    }

    fun unloadPlayer(player: Player) = repository.unload(player.uniqueId)

    fun progress(playerId: UUID): PlayerProgress = repository.load(playerId)

    fun addExperience(player: Player, amount: Long): ExperienceChange {
        require(amount >= 0L)
        var change: ExperienceChange? = null
        repository.update(player.uniqueId) { progress ->
            settings.curve.addExperience(progress, amount).also { change = it }.progress
        }
        api.levels.refresh(player, "symphonylevel:experience")
        return requireNotNull(change)
    }

    fun setLevel(player: Player, level: Int, experience: Long): PlayerProgress {
        require(level in 1..settings.curve.maximumLevel)
        require(experience >= 0L)
        val updated = repository.update(player.uniqueId) { PlayerProgress(level, experience) }
        api.levels.refresh(player, "symphonylevel:set")
        return updated
    }

    fun message(key: String, vararg variables: Pair<String, Any?>): String = messages.text(key, *variables)
    fun maximumLevel(): Int = settings.curve.maximumLevel
    fun nextExperience(level: Int): Long? = settings.curve.experienceForNextLevel(level)

    private fun registerProvider(configuration: LevelSettings): RegistrationHandle {
        val ownerNamespace = plugin.name.lowercase().replace(Regex("[^a-z0-9._-]"), "_")
        return api.levels.registerProvider(plugin, object : LevelProvider {
            override val id = NamespacedKey(ownerNamespace, "player_level")
            override val displayName = configuration.providerDisplayName

            override fun snapshot(entity: LivingEntity): ProvidedLevel? {
                val player = entity as? Player ?: return null
                val progress = repository.current(player.uniqueId) ?: return null
                return ProvidedLevel(
                    level = progress.level,
                    experience = progress.experience,
                    experienceForNextLevel = configuration.curve.experienceForNextLevel(progress.level)
                )
            }
        }, configuration.providerPriority)
    }

    private fun saveDefault(path: String) {
        if (!plugin.dataFolder.resolve(path).isFile) plugin.saveResource(path, false)
    }
}
