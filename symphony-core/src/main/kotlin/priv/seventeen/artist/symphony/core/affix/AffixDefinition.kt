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
    /** 按触发器类型预索引，避免运行时 filter。 */
    val triggersByType: Map<String, List<TriggerBinding>> = triggers.groupBy { it.type }

    override fun getLevelParams(level: Int): Map<String, Any> {
        return levels[level] ?: levels[1] ?: emptyMap()
    }
}

data class TriggerBinding(
    val type: String,
    val conditions: List<Map<String, Any>> = emptyList(),
    val actions: List<Map<String, Any>> = emptyList(),
    /**
     * ON_TIMER 专用：每多少 tick 派发一次（最小 20，向下取整到 20 的倍数）。
     * 0 或负值表示按外层默认频率（每秒一次）。
     */
    val interval: Int = 0
)

data class PassiveAttribute(
    val operation: String,
    val value: String
)
