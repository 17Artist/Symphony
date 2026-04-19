package priv.seventeen.artist.symphony.core.advanced.element

data class ReactionDefinition(
    val id: String,
    val displayName: String,
    val trigger: String,
    val aura: String,
    val type: ReactionType,
    val multiplier: Double = 1.0,
    val gaugeConsume: Double = 0.5,
    val particle: String? = null,
    val sound: String? = null,
    val radius: Double = 0.0,
    val ticks: Int = 0,
    val interval: Long = 1000,
    val reverse: ReactionReverse? = null
)

data class ReactionReverse(
    val id: String,
    val multiplier: Double
)

enum class ReactionType {
    AMPLIFY,
    DEBUFF,
    DOT_AOE,
    CONTROL,
    SPREAD
}
