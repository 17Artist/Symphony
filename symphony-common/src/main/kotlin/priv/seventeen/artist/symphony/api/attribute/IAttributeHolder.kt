package priv.seventeen.artist.symphony.api.attribute

import java.util.UUID

interface IAttributeHolder {
    val holderId: UUID
    fun getAttribute(id: String): Double
    fun getAttributes(): Map<String, Double>
    fun isDirty(): Boolean
    fun markDirty()
}
