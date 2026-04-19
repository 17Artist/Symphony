package priv.seventeen.artist.symphony.api.affix

import java.util.UUID

data class AffixInstance(
    val uuid: UUID,
    val affixId: String,
    val level: Int,
    val parameters: Map<String, Any>,
    val timestamp: Long = System.currentTimeMillis()
)
