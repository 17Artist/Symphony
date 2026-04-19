package priv.seventeen.artist.symphony.core.affix.action

import priv.seventeen.artist.symphony.api.affix.AffixInstance
import priv.seventeen.artist.symphony.api.attribute.AttributeModifier
import priv.seventeen.artist.symphony.api.attribute.Operation
import priv.seventeen.artist.symphony.api.trigger.ITriggerContext
import priv.seventeen.artist.symphony.core.attribute.AttributeCache

class AttributePermanentActionHandler : ActionHandler {
    override fun execute(params: Map<String, Any>, context: ITriggerContext, affix: AffixInstance) {
        val attribute = resolveParam(params["attribute"], affix.parameters)
        val opStr = resolveParam(params["operation"], affix.parameters).uppercase()
        val value = resolveDouble(params["value"], affix.parameters)
        val operation = if (opStr == "PERCENT") Operation.PERCENT else Operation.FLAT

        // 永久修改通过标记 dirty 触发重算
        // 实际的永久修改器存储在物品 PDC 中，这里只标记重算
        AttributeCache.markDirty(context.entity.uniqueId)
    }
}
