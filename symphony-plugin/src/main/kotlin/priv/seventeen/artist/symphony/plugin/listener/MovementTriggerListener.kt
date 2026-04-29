package priv.seventeen.artist.symphony.plugin.listener

import org.bukkit.event.player.PlayerMoveEvent
import org.bukkit.event.player.PlayerToggleSneakEvent
import org.bukkit.event.player.PlayerToggleSprintEvent
import priv.seventeen.artist.blink.event.AutoListener
import priv.seventeen.artist.symphony.api.trigger.TriggerType
import priv.seventeen.artist.symphony.core.trigger.TriggerDispatcher

object MovementTriggerListener {

    @AutoListener
    fun onMove(event: PlayerMoveEvent) {
        val from = event.from
        val to = event.to ?: return
        // 仅在实际位移时触发（忽略纯视角转动）
        if (from.x == to.x && from.y == to.y && from.z == to.z) return

        TriggerDispatcher.dispatch(TriggerType.ON_MOVE, event.player) {
            set("speed", from.distance(to))
        }

        // 跳跃检测：Y 轴上升且不在地面
        if (to.y > from.y && !event.player.isOnGround) {
            TriggerDispatcher.dispatch(TriggerType.ON_JUMP, event.player) {}
        }
    }

    @AutoListener
    fun onSneak(event: PlayerToggleSneakEvent) {
        if (!event.isSneaking) return
        TriggerDispatcher.dispatch(TriggerType.ON_SNEAK, event.player) {
            set("isSneaking", true)
        }
    }

    @AutoListener
    fun onSprint(event: PlayerToggleSprintEvent) {
        if (!event.isSprinting) return
        TriggerDispatcher.dispatch(TriggerType.ON_SPRINT, event.player) {
            set("isSprinting", true)
        }
    }
}
