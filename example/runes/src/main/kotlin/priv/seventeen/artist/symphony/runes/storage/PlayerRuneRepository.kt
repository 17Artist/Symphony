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

package priv.seventeen.artist.symphony.runes.storage

import org.bukkit.configuration.file.YamlConfiguration
import priv.seventeen.artist.symphony.runes.model.PlayerRuneState
import java.nio.charset.StandardCharsets
import java.nio.file.AtomicMoveNotSupportedException
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.StandardCopyOption
import java.util.UUID
import java.util.concurrent.ConcurrentHashMap

class PlayerRuneRepository(private val dataDirectory: Path) {
    private val cache = ConcurrentHashMap<UUID, PlayerRuneState>()

    fun load(playerId: UUID): PlayerRuneState = cache.computeIfAbsent(playerId, ::read)
    fun current(playerId: UUID): PlayerRuneState? = cache[playerId]

    fun update(playerId: UUID, transform: (PlayerRuneState) -> PlayerRuneState): PlayerRuneState {
        val updated = transform(load(playerId)).validate()
        cache[playerId] = updated
        save(playerId, updated)
        return updated
    }

    fun saveAll() = cache.toMap().forEach(::save)
    fun unload(playerId: UUID) { cache.remove(playerId)?.let { save(playerId, it) } }

    private fun read(playerId: UUID): PlayerRuneState {
        val file = dataDirectory.resolve("$playerId.yml").toFile()
        if (!file.isFile) return PlayerRuneState()
        val yaml = YamlConfiguration.loadConfiguration(file)
        val unlocked = yaml.getConfigurationSection("unlocked")?.getKeys(false).orEmpty().associateWith { id ->
            yaml.getInt("unlocked.$id", 1)
        }
        val equipped = yaml.getConfigurationSection("equipped")?.getKeys(false).orEmpty().associateWith { slot ->
            yaml.getString("equipped.$slot").orEmpty()
        }.filterValues(String::isNotBlank)
        return PlayerRuneState(unlocked, equipped).validate()
    }

    private fun save(playerId: UUID, state: PlayerRuneState) {
        Files.createDirectories(dataDirectory)
        val yaml = YamlConfiguration()
        yaml.set("schema-version", 1)
        state.unlocked.toSortedMap().forEach { (id, rank) -> yaml.set("unlocked.$id", rank) }
        state.equipped.toSortedMap().forEach { (slot, rune) -> yaml.set("equipped.$slot", rune) }
        val target = dataDirectory.resolve("$playerId.yml")
        val temporary = dataDirectory.resolve(".$playerId.yml.tmp")
        Files.writeString(temporary, yaml.saveToString(), StandardCharsets.UTF_8)
        try {
            Files.move(temporary, target, StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING)
        } catch (_: AtomicMoveNotSupportedException) {
            Files.move(temporary, target, StandardCopyOption.REPLACE_EXISTING)
        }
    }

    private fun PlayerRuneState.validate(): PlayerRuneState {
        require(unlocked.keys.all { it.matches(Regex("^[a-z0-9_-]{1,64}$")) })
        require(unlocked.values.all { it in 1..100 })
        require(equipped.keys.all { it.matches(Regex("^[a-z0-9_-]{1,32}$")) })
        require(equipped.values.all { it.matches(Regex("^[a-z0-9_-]{1,64}$")) })
        return this
    }
}
