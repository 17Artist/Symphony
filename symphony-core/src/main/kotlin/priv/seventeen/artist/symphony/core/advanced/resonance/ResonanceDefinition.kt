package priv.seventeen.artist.symphony.core.advanced.resonance

data class ResonanceDefinition(
    val id: String,
    val displayName: String,
    val description: List<String> = emptyList(),
    val condition: ResonanceCondition,
    val effects: ResonanceEffects = ResonanceEffects()
)

data class ResonanceCondition(
    val type: ResonanceConditionType,
    val tag: String? = null,
    val count: Int = 0,
    val affixIds: List<String> = emptyList(),
    val tags: Map<String, Int> = emptyMap(),
    val rarity: String? = null,
    val minSum: Int = 0,
    val mode: String = "ANY"
)

enum class ResonanceConditionType {
    AFFIX_TAG_COUNT,
    AFFIX_ID_SET,
    AFFIX_RARITY_COUNT,
    AFFIX_CATEGORY_COUNT,
    AFFIX_LEVEL_SUM,
    MULTI_TAG,
    SCRIPT
}

data class ResonanceEffects(
    val attributes: Map<String, AttributeEffect> = emptyMap(),
    val triggers: List<Map<String, Any>> = emptyList(),
    val reactionBonuses: Map<String, Double> = emptyMap()
)

data class AttributeEffect(
    val operation: String,
    val value: Double
)
