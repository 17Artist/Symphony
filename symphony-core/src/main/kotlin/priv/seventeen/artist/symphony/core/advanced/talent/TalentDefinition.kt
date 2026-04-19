package priv.seventeen.artist.symphony.core.advanced.talent

data class TalentDefinition(
    val id: String,
    val displayName: String,
    val description: String = "",
    val icon: String = "NETHER_STAR",
    val gate: TalentGate,
    val passiveAttributes: Map<String, PassiveAttr> = emptyMap()
)

data class TalentGate(
    val type: String = "SINGLE",
    val attribute: String? = null,
    val threshold: Double = 0.0,
    val operator: String = ">=",
    val conditions: List<TalentGateCondition> = emptyList()
)

data class TalentGateCondition(
    val attribute: String,
    val threshold: Double,
    val operator: String = ">="
)

data class PassiveAttr(
    val operation: String,
    val value: Double
)
