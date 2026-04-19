package priv.seventeen.artist.symphony.core.data

import org.bukkit.Bukkit
import java.util.UUID
import java.util.concurrent.ConcurrentHashMap

object EntityRuntimeCache {
    private val cache = ConcurrentHashMap<UUID, EntityRuntimeData>()

    fun get(entityId: UUID): EntityRuntimeData {
        return cache.getOrPut(entityId) { EntityRuntimeData() }
    }

    fun remove(entityId: UUID) {
        cache.remove(entityId)
    }

    fun cleanup() {
        cache.keys.removeIf { uuid -> Bukkit.getEntity(uuid) == null }
    }
}

class EntityRuntimeData {
    val statusStacks: MutableMap<String, StatusStackData> = mutableMapOf()
    val elementAuras: MutableMap<String, ElementAuraData> = mutableMapOf()
    val activeShields: MutableList<ShieldData> = mutableListOf()
    val cooldowns: MutableMap<String, Long> = mutableMapOf()
}
