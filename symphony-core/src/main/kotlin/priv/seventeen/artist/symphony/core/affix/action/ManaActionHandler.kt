package priv.seventeen.artist.symphony.core.affix.action

import org.bukkit.entity.Player
import priv.seventeen.artist.symphony.api.affix.AffixInstance
import priv.seventeen.artist.symphony.api.trigger.ITriggerContext
import priv.seventeen.artist.symphony.core.storage.PlayerDataManager
import priv.seventeen.artist.symphony.core.attribute.AttributeCache

class ManaActionHandler : ActionHandler {
    override fun execute(params: Map<String, Any>, context: ITriggerContext, affix: AffixInstance) {
        val amount = resolveDouble(params["amount"], affix.parameters)
        val player = context.entity as? Player ?: return
        val data = PlayerDataManager.getData(player.uniqueId) ?: return
        val maxMana = AttributeCache.get(player.uniqueId, "max_mana") ?: 100.0
        data.runtime.currentMana = (data.runtime.currentMana + amount).coerceIn(0.0, maxMana)
    }
}
