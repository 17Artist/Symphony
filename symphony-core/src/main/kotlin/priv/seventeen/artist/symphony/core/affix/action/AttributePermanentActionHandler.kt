package priv.seventeen.artist.symphony.core.affix.action

import org.bukkit.entity.Player
import priv.seventeen.artist.symphony.api.affix.AffixInstance
import priv.seventeen.artist.symphony.api.attribute.Operation
import priv.seventeen.artist.symphony.api.trigger.ITriggerContext
import priv.seventeen.artist.symphony.core.attribute.AttributeCache
import priv.seventeen.artist.symphony.core.storage.PlayerDataManager
import priv.seventeen.artist.symphony.core.data.ActiveBuff

class AttributePermanentActionHandler : ActionHandler {
    override fun execute(params: Map<String, Any>, context: ITriggerContext, affix: AffixInstance) {
        val attribute = resolveParam(params["attribute"], affix.parameters)
        val operation = Operation.parse(resolveParam(params["operation"], affix.parameters))
        val value = resolveDouble(params["value"], affix.parameters)
        val source = "affix_permanent:${affix.affixId}:${affix.uuid}"

        val player = context.entity as? Player ?: return
        val data = PlayerDataManager.getData(player.uniqueId) ?: return

        // 写入永久 Buff（expireTime = -1 表示永久）
        data.runtime.activeBuffs.add(ActiveBuff(
            id = source,
            attribute = attribute,
            operation = operation,
            value = value,
            expireTime = -1L,
            source = source
        ))
        AttributeCache.markDirty(player.uniqueId)
    }
}
