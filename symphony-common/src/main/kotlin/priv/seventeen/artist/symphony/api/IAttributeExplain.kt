package priv.seventeen.artist.symphony.api

import priv.seventeen.artist.symphony.api.attribute.Operation

/** 单个 Provider 贡献的条目（用于 [IAttributeExplain]）。 */
interface IAttributeContribution {
    val providerId: String
    val source: String
    val operation: Operation
    val value: Double
}

/** 属性计算流水线的可读快照。 */
interface IAttributeExplain {
    val attrId: String
    val displayName: String
    val base: Double
    val contributions: List<IAttributeContribution>
    val formulaDescription: String
    val finalValue: Double
    val whenActive: Boolean
    val readonly: Boolean
}
