package priv.seventeen.artist.symphony.plugin.listener

import org.bukkit.Bukkit
import org.bukkit.event.player.PlayerJoinEvent
import org.bukkit.event.player.PlayerQuitEvent
import priv.seventeen.artist.blink.bukkitPlugin
import priv.seventeen.artist.blink.event.AutoListener
import priv.seventeen.artist.symphony.api.event.BuffExpireEvent
import priv.seventeen.artist.symphony.api.trigger.TriggerType
import priv.seventeen.artist.symphony.core.data.BuffOps
import priv.seventeen.artist.symphony.core.storage.PlayerDataManager
import priv.seventeen.artist.symphony.core.trigger.TriggerDispatcher

object PlayerJoinListener {

    @AutoListener
    fun onJoin(event: PlayerJoinEvent) {
        PlayerDataManager.onPlayerJoin(event.player.uniqueId)

        Bukkit.getScheduler().runTaskLater(bukkitPlugin, Runnable {
            TriggerDispatcher.dispatch(TriggerType.ON_JOIN, event.player) {
                set("player", event.player)
            }
        }, 20L)
    }

    @AutoListener
    fun onQuit(event: PlayerQuitEvent) {
        TriggerDispatcher.dispatch(TriggerType.ON_QUIT, event.player) {
            set("player", event.player)
        }
        // 玩家下线前发布所有活跃 buff 的 PLAYER_QUIT 事件
        PlayerDataManager.getData(event.player.uniqueId)?.runtime?.let { runtime ->
            BuffOps.clearAll(event.player, runtime, BuffExpireEvent.ExpireReason.PLAYER_QUIT)
        }
        PlayerDataManager.onPlayerQuit(event.player.uniqueId)
    }
}

