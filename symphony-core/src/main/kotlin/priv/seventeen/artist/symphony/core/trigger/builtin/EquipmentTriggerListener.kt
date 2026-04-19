package priv.seventeen.artist.symphony.core.trigger.builtin

import org.bukkit.entity.Player
import org.bukkit.event.player.PlayerItemHeldEvent
import priv.seventeen.artist.symphony.api.trigger.TriggerType
import priv.seventeen.artist.symphony.core.trigger.TriggerDispatcher

object EquipmentTriggerListener {

    fun onItemHeld(event: PlayerItemHeldEvent) {
        val player = event.player
        val newItem = player.inventory.getItem(event.newSlot)
        val oldItem = player.inventory.getItem(event.previousSlot)

        TriggerDispatcher.dispatch(TriggerType.ON_HOLD, player) {
            set("item", newItem ?: return@dispatch)
            set("previousItem", oldItem ?: return@dispatch)
        }
    }
}
