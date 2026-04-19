package priv.seventeen.artist.symphony.core.trigger.builtin

import org.bukkit.scheduler.BukkitRunnable
import priv.seventeen.artist.symphony.core.trigger.CooldownManager

class PeriodicTriggerTask : BukkitRunnable() {
    private var tickCount = 0L

    override fun run() {
        tickCount++

        // 每 100 tick 清理过期冷却
        if (tickCount % 100 == 0L) {
            CooldownManager.cleanup()
        }
        // EntityRuntimeCache.cleanup() 由 RuntimeTickTask 统一处理
    }
}
