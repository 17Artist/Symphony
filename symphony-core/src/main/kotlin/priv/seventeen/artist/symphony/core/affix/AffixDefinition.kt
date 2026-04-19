package priv.seventeen.artist.symphony.core.affix

import priv.seventeen.artist.symphony.api.affix.AffixRarity
import priv.seventeen.artist.symphony.api.affix.IAffix

data class AffixDefinition(
    override val id: String,
    override val displayName: String,
    override val description: List<String> = emptyList(),
    override val maxLevel: Int = 1,
    override val rarity: AffixRarity = AffixRarity.COMMON,
    override val category: String = "any",
    override val exclusiveGroup: String? = null,
    override val tags: List<String> = emptyList(),
    val levels: Map<Int, Map<String, Any>> = emptyMap(),
    val triggers: List<TriggerBinding> = emptyList(),
    val passiveAttributes: Map<String, PassiveAttribute> = emptyMap()
) : IAffix {
    override fun getLevelParams(level: Int): Map<String, Any> {
        return levels[level] ?: levels[1] ?: emptyMap()
    }
}

data class TriggerBinding(
    val type: String,
    val conditions: List<Map<String, Any>> = emptyList(),
    val actions: List<Map<String, Any>> = emptyList()
)

data class PassiveAttribute(
    val operation: String,
    val value: String
)
