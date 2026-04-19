package priv.seventeen.artist.symphony.core.storage

import priv.seventeen.artist.symphony.core.data.PersistentData
import java.util.UUID

interface StorageProvider {
    fun initialize()
    fun shutdown()
    fun loadPlayer(uuid: UUID): PersistentData?
    fun savePlayer(uuid: UUID, data: PersistentData)
    fun saveAll(dataMap: Map<UUID, PersistentData>)
    fun deletePlayer(uuid: UUID)
    fun exists(uuid: UUID): Boolean
}
