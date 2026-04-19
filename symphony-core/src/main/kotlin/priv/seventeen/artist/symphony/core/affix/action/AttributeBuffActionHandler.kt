package priv.seventeen.artist.symphony.core.affix.action

import priv.seventeen.artist.symphony.api.affix.AffixInstance
import priv.seventeen.artist.symphony.api.attribute.Operation
import priv.seventeen.artist.symphony.api.trigger.ITriggerContext
import priv.seventeen.artist.symphony.core.attribute.AttributeCache
import priv.seventeen.artist.symphony.core.data.ActiveBuff
import priv.seventeen.artist.symphony.core.storage.PlayerDataManager

class AttributeBuffActionHandler : ActionHandler {
    override fun execute(params: Map<String, Any>, context: ITriggerContext, affix: AffixInstance) {
        val attribute = resolveParam(params["attribute"], affix.parameters)
        val opStr = resolveParam(params["operation"], affix.parameters).uppercase()
        val value = resolveDouble(params["value"], affix.parameters)
        val duration = resolveDouble(params["duration"], affix.parameters).toLong()
        val operation = if (opStr == "PERCENT") Operation.PERCENT else Operation.FLAT

        val data = PlayerDataManager.getData(context.entity.uniqueId) ?: return
        data.runtime.activeBuffs.add(ActiveBuff(
            id = "affix:${affix.affixId}:${System.currentTimeMillis()}",
            attribute = attribute,
            operation = operation,
            value = value,
            expireTime = if (duration > 0) System.currentTimeMillis() + duration else -1L,
            source = "affix:${affix.affixId}"
        ))
        AttributeCache.markDirty(context.entity.uniqueId)
    }
}
