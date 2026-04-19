package priv.seventeen.artist.symphony.api.attribute

data class AttributeModifier(
    val attributeId: String,
    val operation: Operation,
    val value: Double,
    val source: String
)
