package priv.seventeen.artist.symphony.api.growth

data class GemSlot(
    val index: Int,
    val gemId: String?,
    val gemLevel: Int,
    val locked: Boolean
)
