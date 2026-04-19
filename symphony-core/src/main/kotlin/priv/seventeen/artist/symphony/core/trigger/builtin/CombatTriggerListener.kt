package priv.seventeen.artist.symphony.core.trigger.builtin

import org.bukkit.entity.LivingEntity
import org.bukkit.entity.Player
import org.bukkit.entity.Projectile
import org.bukkit.event.EventPriority
import org.bukkit.event.entity.EntityDamageByEntityEvent
import org.bukkit.event.entity.EntityDeathEvent
import org.bukkit.event.entity.PlayerDeathEvent
import priv.seventeen.artist.symphony.api.trigger.TriggerType
import priv.seventeen.artist.symphony.core.trigger.TriggerDispatcher

object CombatTriggerListener {

    fun onEntityDamageByEntity(event: EntityDamageByEntityEvent) {
        val victim = event.entity as? LivingEntity ?: return
        var attacker = event.damager as? LivingEntity

        // 处理投射物
        if (attacker == null && event.damager is Projectile) {
            attacker = (event.damager as Projectile).shooter as? LivingEntity
        }

        if (attacker != null) {
            // ON_ATTACK
            TriggerDispatcher.dispatch(TriggerType.ON_ATTACK, attacker) {
                target(victim)
                set("damage", event.damage)
                set("damageType", "physical")
                set("attacker", attacker)
                set("victim", victim)
            }

            // ON_DEFEND
            TriggerDispatcher.dispatch(TriggerType.ON_DEFEND, victim) {
                target(attacker)
                set("damage", event.damage)
                set("damageType", "physical")
                set("attacker", attacker)
                set("victim", victim)
            }
        }
    }

    fun onEntityDeath(event: EntityDeathEvent) {
        val victim = event.entity
        val killer = victim.killer ?: return

        TriggerDispatcher.dispatch(TriggerType.ON_KILL, killer) {
            target(victim)
            set("victim", victim)
            set("killer", killer)
        }
    }
}
