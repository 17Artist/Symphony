package priv.seventeen.artist.symphony.core.trigger.builtin

import org.bukkit.entity.Player
import org.bukkit.event.player.PlayerMoveEvent
import org.bukkit.event.player.PlayerToggleSneakEvent
import org.bukkit.event.player.PlayerToggleSprintEvent
import priv.seventeen.artist.symphony.api.trigger.TriggerType
import priv.seventeen.artist.symphony.core.trigger.TriggerDispatcher

object MovementTriggerListener {

    fun onPlayerMove(event: PlayerMoveEvent) {
        val from = event.from
        val to = event.to ?: return
        if (from.x == to.x && from.y == to.y && from.z == to.z) return

        TriggerDispatcher.dispatch(TriggerType.ON_MOVE, event.player) {
            set("from", from)
            set("to", to)
            set("speed", event.player.velocity.length())
        }

        // 跳跃检测
        if (to.y > from.y && event.player.velocity.y > 0 && from.y % 1.0 == 0.0) {
            TriggerDispatcher.dispatch(TriggerType.ON_JUMP, event.player) {
                set("location", from)
            }
        }
    }

    fun onToggleSneak(event: PlayerToggleSneakEvent) {
        TriggerDispatcher.dispatch(TriggerType.ON_SNEAK, event.player) {
            set("isSneaking", event.isSneaking)
        }
    }

    fun onToggleSprint(event: PlayerToggleSprintEvent) {
        TriggerDispatcher.dispatch(TriggerType.ON_SPRINT, event.player) {
            set("isSprinting", event.isSprinting)
        }
    }
}
