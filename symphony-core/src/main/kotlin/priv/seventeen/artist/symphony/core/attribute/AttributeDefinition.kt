package priv.seventeen.artist.symphony.core.attribute

import priv.seventeen.artist.symphony.api.attribute.IAttribute

data class AttributeDefinition(
    override val id: String,
    override val displayName: String,
    override val description: String = "",
    override val category: String = "custom",
    override val defaultValue: Double = 0.0,
    override val minValue: Double = -Double.MAX_VALUE,
    override val maxValue: Double = Double.MAX_VALUE,
    override val format: String = "number",
    override val priority: Int = 0,
    override val vanillaBinding: String? = null,
    override val readonly: Boolean = false,
    override val tags: List<String> = emptyList(),
    val formulaId: String? = null,
    val deriveId: String? = null,
    val onChangeId: String? = null,
    /** 静态依赖声明：当 readonly 且含表达式/派生时，可提前校验拼写。 */
    val dependsOn: List<String> = emptyList(),
    /** 条件门：只有所有条件全部为真时属性才生效，否则结果填 [defaultValue]。 */
    val whenConditions: List<String> = emptyList()
) : IAttribute
