package priv.seventeen.artist.symphony.plugin.listener

import org.bukkit.event.player.PlayerItemBreakEvent
import org.bukkit.event.player.PlayerItemConsumeEvent
import priv.seventeen.artist.blink.event.AutoListener
import priv.seventeen.artist.symphony.api.trigger.TriggerType
import priv.seventeen.artist.symphony.core.trigger.TriggerDispatcher

object ItemTriggerListener {

    @AutoListener
    fun onConsume(event: PlayerItemConsumeEvent) {
        TriggerDispatcher.dispatch(TriggerType.ON_CONSUME, event.player) {
            set("item", event.item.type.name)
        }
    }

    @AutoListener
    fun onBreakItem(event: PlayerItemBreakEvent) {
        TriggerDispatcher.dispatch(TriggerType.ON_BREAK_ITEM, event.player) {
            set("item", event.brokenItem.type.name)
        }
    }
}
