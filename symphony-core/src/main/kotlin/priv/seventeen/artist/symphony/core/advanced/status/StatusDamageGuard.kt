package priv.seventeen.artist.symphony.core.advanced.status

import org.bukkit.Bukkit
import org.bukkit.entity.Entity
import org.bukkit.entity.LivingEntity
import org.bukkit.event.entity.EntityDamageByEntityEvent
import org.bukkit.event.entity.EntityDamageEvent
import org.bukkit.metadata.FixedMetadataValue

/**
 * 状态层伤害守卫 — 标记状态层 DOT/爆发伤害，防止 DamageListener
 * 将其当作普通攻击处理，从而触发 ON_ATTACK 词条造成无限循环叠层。
 */
object StatusDamageGuard {
    private const val KEY = "symphony_status_damage"

    /**
     * 判断事件是否应该进入 Symphony 伤害管线。
     * 状态层伤害（带 metadata 标记）返回 false，跳过管线。
     */
    fun shouldHandle(event: EntityDamageEvent): Boolean {
        if (event !is EntityDamageByEntityEvent) return false
        val victim = event.entity
        return !victim.hasMetadata(KEY)
    }

    /**
     * 执行状态层伤害 — 先标记 metadata，伤害完成后移除。
     * DamageListener 检测到标记后会跳过该事件。
     */
    fun damage(victim: LivingEntity, damage: Double, attacker: Entity?) {
        val plugin = Bukkit.getPluginManager().getPlugin("Symphony") ?: run {
            victim.damage(damage, attacker)
            return
        }
        victim.setMetadata(KEY, FixedMetadataValue(plugin, true))
        try {
            victim.damage(damage, attacker)
        } finally {
            victim.removeMetadata(KEY, plugin)
        }
    }
}
