package priv.seventeen.artist.symphony.core.growth.rune

import java.util.concurrent.ConcurrentHashMap

data class RunePassiveAttribute(
    val operation: String,
    val value: Double
)

data class RuneActivation(
    val type: String = "FRAGMENT",
    val fragmentsRequired: Map<Int, Int> = emptyMap()
)

data class RuneDefinition(
    val id: String,
    val displayName: String,
    val description: List<String>,
    val maxLevel: Int,
    val category: String,
    val activation: RuneActivation,
    val passiveAttributes: Map<Int, Map<String, RunePassiveAttribute>>,
    val triggers: List<Map<String, Any>>
) {
    fun passivesFor(level: Int): Map<String, RunePassiveAttribute> {
        if (passiveAttributes.isEmpty()) return emptyMap()
        passiveAttributes[level]?.let { return it }
        // 没有精确匹配时，取 ≤ level 的最大配置（阶梯式生效）
        val key = passiveAttributes.keys.filter { it <= level }.maxOrNull()
            ?: passiveAttributes.keys.min()
        return passiveAttributes[key] ?: emptyMap()
    }
}

object RuneRegistry {
    private val definitions = ConcurrentHashMap<String, RuneDefinition>()

    fun register(def: RuneDefinition) { definitions[def.id] = def }
    fun get(id: String): RuneDefinition? = definitions[id]
    fun all(): Collection<RuneDefinition> = definitions.values
    fun ids(): Set<String> = definitions.keys
    fun clear() { definitions.clear() }
    fun size(): Int = definitions.size
}
