package priv.seventeen.artist.symphony.api.affix

interface IAffix {
    val id: String
    val displayName: String
    val description: List<String>
    val maxLevel: Int
    val rarity: AffixRarity
    val category: String
    val exclusiveGroup: String?
    val tags: List<String>
    fun getLevelParams(level: Int): Map<String, Any>
}
