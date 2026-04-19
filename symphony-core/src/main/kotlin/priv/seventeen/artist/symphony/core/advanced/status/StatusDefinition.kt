package priv.seventeen.artist.symphony.core.advanced.status

data class StatusDefinition(
    val id: String,
    val displayName: String,
    val icon: String = "",
    val maxStacks: Int = 10,
    val stackDuration: Long = 8000,
    val decayMode: DecayMode = DecayMode.INDIVIDUAL,
    val tickInterval: Long = 1000,
    val damageType: String = "physical",
    val perStackDamageRatio: Double = 0.05,
    val perStackAttributes: Map<String, StackAttribute> = emptyMap(),
    val immuneDuration: Long = 3000
)

enum class DecayMode {
    INDIVIDUAL,
    REFRESH
}

data class StackAttribute(
    val operation: String,
    val value: Double
)
