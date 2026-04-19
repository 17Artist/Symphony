package priv.seventeen.artist.symphony.core.trigger

import java.util.UUID
import java.util.concurrent.ConcurrentHashMap

object CooldownManager {
    // key = "uuid:cooldownKey" -> expireTimestamp
    private val cooldowns = ConcurrentHashMap<String, Long>()

    private fun key(uuid: UUID, key: String) = "$uuid:$key"

    fun setCooldown(uuid: UUID, key: String, durationMs: Long) {
        cooldowns[key(uuid, key)] = System.currentTimeMillis() + durationMs
    }

    fun isOnCooldown(uuid: UUID, key: String): Boolean {
        val expire = cooldowns[key(uuid, key)] ?: return false
        if (System.currentTimeMillis() >= expire) {
            cooldowns.remove(key(uuid, key))
            return false
        }
        return true
    }

    fun getRemaining(uuid: UUID, key: String): Long {
        val expire = cooldowns[key(uuid, key)] ?: return 0
        val remaining = expire - System.currentTimeMillis()
        if (remaining <= 0) {
            cooldowns.remove(key(uuid, key))
            return 0
        }
        return remaining
    }

    fun clearCooldown(uuid: UUID, key: String) {
        cooldowns.remove(key(uuid, key))
    }

    fun clearAll(uuid: UUID) {
        val prefix = "$uuid:"
        cooldowns.keys.removeIf { it.startsWith(prefix) }
    }

    fun cleanup() {
        val now = System.currentTimeMillis()
        cooldowns.entries.removeIf { it.value < now }
    }
}
