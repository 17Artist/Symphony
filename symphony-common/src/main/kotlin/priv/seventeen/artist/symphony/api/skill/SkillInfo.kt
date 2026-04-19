package priv.seventeen.artist.symphony.api.skill

data class SkillInfo(
    val id: String,
    val displayName: String,
    val description: List<String>,
    val maxLevel: Int,
    val cooldown: Long,
    val manaCost: Double
)
