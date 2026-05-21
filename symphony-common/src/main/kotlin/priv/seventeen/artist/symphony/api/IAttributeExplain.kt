package priv.seventeen.artist.symphony.api

import priv.seventeen.artist.symphony.api.attribute.Operation

/** 单个 Provider 贡献的条目（用于 [IAttributeExplain]）。 */
interface IAttributeContribution {
    val providerId: String
    val source: String
    val operation: Operation
    val value: Double
}

/** 属性交互对终值的影响（用于 [IAttributeExplain]）。 */
interface IAttributeInteractionEffect {
    /** 交互定义 ID */
    val interactionId: String
    /** 交互类型（CONVERSION / OVERFLOW / SYNERGY / CONFLICT / AMPLIFY / THRESHOLD / DIMINISH） */
    val type: String
    /** 角色 — source / target / synergy_member / conflict_a / conflict_b */
    val role: String
    /** 人类可读描述 */
    val description: String
}

/** 属性计算流水线的可读快照。 */
interface IAttributeExplain {
    val attrId: String
    val displayName: String
    val base: Double
    val contributions: List<IAttributeContribution>
    val interactions: List<IAttributeInteractionEffect>
    val formulaDescription: String
    val finalValue: Double
    val whenActive: Boolean
    val readonly: Boolean
}
