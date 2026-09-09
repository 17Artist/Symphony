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

package priv.seventeen.artist.symphony.bukkit.persistence

import priv.seventeen.artist.symphony.api.level.LevelSnapshot
import java.nio.ByteBuffer
import java.nio.charset.StandardCharsets
import java.security.MessageDigest
import java.util.UUID
import kotlin.math.abs

internal data class StoredHealth(
    val health: Double,
    val maximumHealth: Double
) {
    init {
        require(health.isFinite() && health >= 0.0) { "生命值必须是非负有限数" }
        require(maximumHealth.isFinite() && maximumHealth > 0.0) { "最大生命值必须是正有限数" }
    }
}

internal data class HealthStateKey(
    val clusterId: String,
    val playerId: UUID,
    val characterKey: String
)

internal data class HealthSessionToken(
    val key: HealthStateKey,
    val sessionId: UUID
)

internal object HealthCharacterKey {
    fun from(level: LevelSnapshot?): String {
        val identity = if (level?.characterId == null) {
            "player"
        } else {
            "${level.provider}:${level.characterId}"
        }
        return sha256(identity)
    }

    private fun sha256(value: String): String = MessageDigest.getInstance("SHA-256")
        .digest(value.toByteArray(StandardCharsets.UTF_8))
        .joinToString("") { byte -> "%02x".format(byte.toInt() and 0xff) }
}

internal object StoredHealthCodec {
    private const val VERSION: Byte = 1
    private const val LENGTH = 17

    fun encode(value: StoredHealth): ByteArray = ByteBuffer.allocate(LENGTH)
        .put(VERSION)
        .putDouble(value.health)
        .putDouble(value.maximumHealth)
        .array()

    fun decode(bytes: ByteArray): StoredHealth? {
        if (bytes.size != LENGTH) return null
        val buffer = ByteBuffer.wrap(bytes)
        if (buffer.get() != VERSION) return null
        return runCatching { StoredHealth(buffer.double, buffer.double) }.getOrNull()
    }
}

internal object HealthRestorePolicy {
    private const val EPSILON = 1.0e-6

    fun hasChanged(first: StoredHealth, second: StoredHealth): Boolean =
        abs(first.health - second.health) > EPSILON ||
            abs(first.maximumHealth - second.maximumHealth) > EPSILON

    fun mayRestore(joinHealth: Double, currentHealth: Double, stored: StoredHealth): Boolean =
        stored.health > 0.0 && abs(joinHealth - currentHealth) <= EPSILON

    fun healthChangedSinceJoin(joinHealth: Double, currentHealth: Double): Boolean =
        abs(joinHealth - currentHealth) > EPSILON

    fun restoredHealth(stored: StoredHealth, currentMaximumHealth: Double): Double =
        stored.health.coerceAtMost(currentMaximumHealth).coerceAtLeast(EPSILON)
}
