package priv.seventeen.artist.symphony.core.affix.action

import org.bukkit.Bukkit
import org.bukkit.entity.Player
import priv.seventeen.artist.symphony.api.affix.AffixInstance
import priv.seventeen.artist.symphony.api.event.SymphonyManaConsumeEvent
import priv.seventeen.artist.symphony.api.trigger.ITriggerContext
import priv.seventeen.artist.symphony.core.storage.PlayerDataManager
import priv.seventeen.artist.symphony.core.attribute.AttributeCache

class ManaActionHandler : ActionHandler {
    override fun execute(params: Map<String, Any>, context: ITriggerContext, affix: AffixInstance) {
        val amount = resolveDouble(params["amount"], affix.parameters)
        val targets = resolveTargets(params, context, affix.parameters, defaultTarget = context.entity)
        for (target in targets) {
            val player = target as? Player ?: continue
            val data = PlayerDataManager.getData(player.uniqueId) ?: continue
            // amount < 0 表示消耗法力，发布事件允许取消
            if (amount < 0) {
                val consumeEvent = SymphonyManaConsumeEvent(player, -amount, "affix:${affix.affixId}")
                Bukkit.getPluginManager().callEvent(consumeEvent)
                if (consumeEvent.isCancelled) continue
                val maxMana = AttributeCache.get(player.uniqueId, "max_mana") ?: 100.0
                data.runtime.currentMana = (data.runtime.currentMana - consumeEvent.amount).coerceIn(0.0, maxMana)
            } else {
                val maxMana = AttributeCache.get(player.uniqueId, "max_mana") ?: 100.0
                data.runtime.currentMana = (data.runtime.currentMana + amount).coerceIn(0.0, maxMana)
            }
        }
    }
}
