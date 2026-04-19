package priv.seventeen.artist.symphony.api.entity

import java.util.UUID

interface IPlayerData {
    val uuid: UUID
    var level: Int
    var exp: Long
    var freePoints: Int
    fun isDirty(): Boolean
    fun markDirty()
}
