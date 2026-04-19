package priv.seventeen.artist.symphony.core.advanced.interaction

data class InteractionDefinition(
    val id: String,
    val type: InteractionType,
    val source: String? = null,
    val target: String? = null,
    val attributes: List<String> = emptyList(),
    val threshold: Double = 0.0,
    val thresholdA: Double = 0.0,
    val ratio: Double = 0.0,
    val bonus: Double = 0.0,
    val penaltyB: Double = 0.0,
    val multiplier: Double = 1.0,
    val description: String = "",
    val attributeA: String? = null,
    val attributeB: String? = null
)
