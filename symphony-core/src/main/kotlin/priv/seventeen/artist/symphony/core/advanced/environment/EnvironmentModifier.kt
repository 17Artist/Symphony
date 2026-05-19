package priv.seventeen.artist.symphony.core.advanced.environment

data class EnvironmentModifier(
    val id: String,
    val displayName: String,
    val type: EnvironmentType,
    val attributes: Map<String, EnvAttributeEffect> = emptyMap(),
    val description: String = "",
    // ── 类型相关的匹配字段（可选） ──
    /** DIMENSION 类型：维度名（NORMAL / NETHER / THE_END），不区分大小写。 */
    val dimension: String? = null,
    /** BIOME 类型：生物群系名列表（如 ["DESERT", "BADLANDS"]），不区分大小写。 */
    val biomes: List<String> = emptyList(),
    /** TIME 类型：起始 tick（含），范围 0-23999。 */
    val timeStart: Long = -1,
    /** TIME 类型：结束 tick（含），范围 0-23999。 */
    val timeEnd: Long = -1,
    /** WEATHER 类型：CLEAR / RAIN / THUNDER。 */
    val weather: String? = null,
    /** ALTITUDE 类型：最低 Y 坐标（含），未设时不限制。 */
    val minY: Double = Double.NEGATIVE_INFINITY,
    /** ALTITUDE 类型：最高 Y 坐标（含），未设时不限制。 */
    val maxY: Double = Double.POSITIVE_INFINITY,
    /** WEATHER / TIME 类型：是否要求实体在户外（默认 false）。 */
    val requireOutdoor: Boolean = false
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
