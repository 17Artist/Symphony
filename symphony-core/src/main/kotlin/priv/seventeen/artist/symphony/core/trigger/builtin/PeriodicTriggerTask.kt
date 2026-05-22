package priv.seventeen.artist.symphony.core.trigger.builtin

import org.bukkit.Bukkit
import org.bukkit.scheduler.BukkitRunnable
import priv.seventeen.artist.symphony.api.trigger.TriggerType
import priv.seventeen.artist.symphony.core.affix.AffixManagerImpl
import priv.seventeen.artist.symphony.core.trigger.CooldownManager
import priv.seventeen.artist.symphony.core.trigger.TriggerDispatcher

class PeriodicTriggerTask : BukkitRunnable() {
    private var tickCount = 0L

    override fun run() {
        tickCount++

        // 每 100 tick 清理过期冷却
        if (tickCount % 100 == 0L) {
            CooldownManager.cleanup()
        }

        // 每 20 tick (1秒): ON_TIMER 触发器 — 词条层级再按 binding.interval 节流
        if (tickCount % 20 == 0L) {
            val capturedTick = tickCount
            for (player in Bukkit.getOnlinePlayers()) {
                TriggerDispatcher.dispatch(TriggerType.ON_TIMER, player) {
                    set("tick", capturedTick)
                    set("tickCount", capturedTick)
                }
            }
        }
    }
}
