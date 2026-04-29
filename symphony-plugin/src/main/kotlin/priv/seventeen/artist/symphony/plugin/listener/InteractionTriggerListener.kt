package priv.seventeen.artist.symphony.plugin.listener

import org.bukkit.event.block.Action
import org.bukkit.event.player.PlayerInteractEvent
import priv.seventeen.artist.blink.event.AutoListener
import priv.seventeen.artist.symphony.api.trigger.TriggerType
import priv.seventeen.artist.symphony.core.trigger.TriggerDispatcher

object InteractionTriggerListener {

    @AutoListener
    fun onInteract(event: PlayerInteractEvent) {
        val player = event.player

        TriggerDispatcher.dispatch(TriggerType.ON_INTERACT, player) {
            set("action", event.action.name)
            set("item", event.item?.type?.name ?: "AIR")
        }

        when (event.action) {
            Action.RIGHT_CLICK_AIR, Action.RIGHT_CLICK_BLOCK -> {
                TriggerDispatcher.dispatch(TriggerType.ON_RIGHT_CLICK, player) {
                    set("item", event.item?.type?.name ?: "AIR")
                    set("hasBlock", event.action == Action.RIGHT_CLICK_BLOCK)
                }
            }
            Action.LEFT_CLICK_AIR, Action.LEFT_CLICK_BLOCK -> {
                TriggerDispatcher.dispatch(TriggerType.ON_LEFT_CLICK, player) {
                    set("item", event.item?.type?.name ?: "AIR")
                    set("hasBlock", event.action == Action.LEFT_CLICK_BLOCK)
                }
            }
            else -> {}
        }
    }
}
