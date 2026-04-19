package priv.seventeen.artist.symphony.api.attribute

interface IAttribute {
    val id: String
    val displayName: String
    val description: String
    val category: String
    val defaultValue: Double
    val minValue: Double
    val maxValue: Double
    val format: String
    val priority: Int
    val vanillaBinding: String?
    val readonly: Boolean
    val tags: List<String>
}
