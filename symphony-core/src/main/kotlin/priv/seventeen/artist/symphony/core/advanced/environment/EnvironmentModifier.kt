package priv.seventeen.artist.symphony.core.advanced.environment

data class EnvironmentModifier(
    val id: String,
    val displayName: String,
    val type: EnvironmentType,
    val attributes: Map<String, EnvAttributeEffect> = emptyMap(),
    val description: String = ""
)

data class EnvAttributeEffect(
    val operation: String,
    val value: Double
)

enum class EnvironmentType {
    DIMENSION,
    BIOME,
    WEATHER,
    TIME,
    ALTITUDE,
    IN_WATER,
    COMBAT_TARGET
}
