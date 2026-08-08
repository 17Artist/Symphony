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

package priv.seventeen.artist.symphony.level.storage

import org.bukkit.configuration.file.YamlConfiguration
import priv.seventeen.artist.symphony.level.model.LevelCurve
import priv.seventeen.artist.symphony.level.model.PlayerProgress
import java.nio.charset.StandardCharsets
import java.nio.file.AtomicMoveNotSupportedException
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.StandardCopyOption
import java.util.UUID
import java.util.concurrent.ConcurrentHashMap

class PlayerProgressRepository(
    private val dataDirectory: Path,
    private val curve: () -> LevelCurve
) {
    private val cache = ConcurrentHashMap<UUID, PlayerProgress>()

    fun load(playerId: UUID): PlayerProgress = cache.computeIfAbsent(playerId, ::read)

    fun current(playerId: UUID): PlayerProgress? = cache[playerId]

    fun update(playerId: UUID, transform: (PlayerProgress) -> PlayerProgress): PlayerProgress {
        val updated = curve().normalize(transform(load(playerId)))
        cache[playerId] = updated
        save(playerId, updated)
        return updated
    }

    fun save(playerId: UUID) {
        cache[playerId]?.let { save(playerId, it) }
    }

    fun saveAll() = cache.toMap().forEach(::save)

    fun unload(playerId: UUID) {
        cache.remove(playerId)?.let { save(playerId, it) }
    }

    private fun read(playerId: UUID): PlayerProgress {
        val file = dataDirectory.resolve("$playerId.yml").toFile()
        if (!file.isFile) return initial()
        val yaml = YamlConfiguration.loadConfiguration(file)
        return curve().normalize(PlayerProgress(
            level = yaml.getInt("level", 1),
            experience = yaml.getLong("experience", 0L)
        ))
    }

    private fun initial(): PlayerProgress = PlayerProgress(level = 1, experience = 0L)

    private fun save(playerId: UUID, progress: PlayerProgress) {
        Files.createDirectories(dataDirectory)
        val yaml = YamlConfiguration()
        yaml.set("schema-version", 1)
        yaml.set("level", progress.level)
        yaml.set("experience", progress.experience)
        val target = dataDirectory.resolve("$playerId.yml")
        val temporary = dataDirectory.resolve(".$playerId.yml.tmp")
        Files.writeString(temporary, yaml.saveToString(), StandardCharsets.UTF_8)
        try {
            Files.move(temporary, target, StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING)
        } catch (_: AtomicMoveNotSupportedException) {
            Files.move(temporary, target, StandardCopyOption.REPLACE_EXISTING)
        }
    }

}
