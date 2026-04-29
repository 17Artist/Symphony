package priv.seventeen.artist.symphony.plugin.listener

import org.bukkit.entity.Player
import org.bukkit.event.entity.EntityDeathEvent
import org.bukkit.event.entity.PlayerDeathEvent
import org.bukkit.event.player.PlayerRespawnEvent
import priv.seventeen.artist.blink.event.AutoListener
import priv.seventeen.artist.symphony.api.trigger.TriggerType
import priv.seventeen.artist.symphony.core.trigger.TriggerDispatcher

object LifecycleTriggerListener {

    @AutoListener
    fun onPlayerDeath(event: PlayerDeathEvent) {
        val player = event.entity
        val killer = player.killer
        TriggerDispatcher.dispatch(TriggerType.ON_DEATH, player) {
            if (killer != null) {
                target(killer)
                set("killer", killer.name)
            }
        }
    }

    @AutoListener
    fun onEntityDeath(event: EntityDeathEvent) {
        val entity = event.entity
        if (entity is Player) return // PlayerDeathEvent 已处理
        val killer = entity.killer ?: return
        TriggerDispatcher.dispatch(TriggerType.ON_KILL, killer) {
            target(entity)
            set("victim", entity.type.name)
        }
    }

    @AutoListener
    fun onRespawn(event: PlayerRespawnEvent) {
        TriggerDispatcher.dispatch(TriggerType.ON_RESPAWN, event.player) {
            set("isBedSpawn", event.isBedSpawn)
        }
    }
}
