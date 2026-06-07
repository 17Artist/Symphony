package priv.seventeen.artist.symphony.plugin.listener

import org.bukkit.Bukkit
import org.bukkit.event.EventPriority
import priv.seventeen.artist.blink.bukkitPlugin
import org.bukkit.event.player.PlayerChangedWorldEvent
import org.bukkit.event.player.PlayerTeleportEvent
import priv.seventeen.artist.blink.BlinkLog
import priv.seventeen.artist.blink.event.AutoListener

/**
 * 跨世界传送时重置 Epic Fight 动画追踪，避免 ANIMATION_END 未触发导致状态卡死。
 */
object PlayerWorldListener {

    @AutoListener(priority = EventPriority.MONITOR)
    fun onChangedWorld(event: PlayerChangedWorldEvent) {
        val p = event.player
        BlinkLog.info(
            "§7[Symphony/EF]§f 事件 ChangedWorld ${p.name} " +
                "${event.from.name} -> ${p.world.name}"
        )
        Bukkit.getScheduler().runTask(bukkitPlugin, Runnable {
            EpicFightAnimationListener.onWorldChange(p)
        })
    }

    /** 部分跨世界插件只走 Teleport 不走 ChangedWorld */
    @AutoListener(priority = EventPriority.MONITOR, ignoreCancelled = true)
    fun onTeleport(event: PlayerTeleportEvent) {
        val from = event.from.world ?: return
        val to = event.to?.world ?: return
        if (from != to) {
            BlinkLog.info(
                "§7[Symphony/EF]§f 事件 Teleport跨世界 ${event.player.name} " +
                    "${from.name} -> ${to.name} cause=${event.cause}"
            )
            // 延迟 1 tick：此时玩家可能仍在旧世界，NMS/世界状态未就绪
            Bukkit.getScheduler().runTask(bukkitPlugin, Runnable {
                EpicFightAnimationListener.onWorldChange(event.player)
            })
        }
    }
}