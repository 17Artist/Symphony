package priv.seventeen.artist.symphony.core.affix.action

import org.bukkit.Bukkit
import priv.seventeen.artist.symphony.api.affix.AffixInstance
import priv.seventeen.artist.symphony.api.attribute.Operation
import priv.seventeen.artist.symphony.api.event.BuffApplyEvent
import priv.seventeen.artist.symphony.api.event.BuffExpireEvent
import priv.seventeen.artist.symphony.api.trigger.ITriggerContext
import priv.seventeen.artist.symphony.core.attribute.AttributeCache
import priv.seventeen.artist.symphony.core.data.ActiveBuff
import priv.seventeen.artist.symphony.core.data.BuffOps
import priv.seventeen.artist.symphony.core.storage.PlayerDataManager

class AttributeBuffActionHandler : ActionHandler {
    override fun execute(params: Map<String, Any>, context: ITriggerContext, affix: AffixInstance) {
        val attribute = resolveParam(params["attribute"], affix.parameters)
        val opStr = resolveParam(params["operation"], affix.parameters).uppercase()
        val value = resolveDouble(params["value"], affix.parameters)
        val duration = resolveDouble(params["duration"], affix.parameters).toLong()
        val operation = if (opStr == "PERCENT") Operation.PERCENT else Operation.FLAT

        val buffId = "affix:${affix.affixId}:${System.currentTimeMillis()}"
        val source = "affix:${affix.affixId}"

        // 发布 BuffApplyEvent，允许外部取消或修改 value/duration
        val applyEvent = BuffApplyEvent(context.entity, buffId, attribute, value, duration, source)
        Bukkit.getPluginManager().callEvent(applyEvent)
        if (applyEvent.isCancelled) return

        val data = PlayerDataManager.getData(context.entity.uniqueId) ?: return
        // 同 source + attribute 的旧 buff 视为被替换，发 REPLACED
        BuffOps.removeWhere(context.entity, data.runtime, BuffExpireEvent.ExpireReason.REPLACED) {
            it.source == source && it.attribute == attribute
        }
        data.runtime.activeBuffs.add(ActiveBuff(
            id = buffId,
            attribute = attribute,
            operation = operation,
            value = applyEvent.value,
            expireTime = if (applyEvent.durationMs > 0) System.currentTimeMillis() + applyEvent.durationMs else -1L,
            source = source
        ))
        AttributeCache.markDirty(context.entity.uniqueId)
    }
}
