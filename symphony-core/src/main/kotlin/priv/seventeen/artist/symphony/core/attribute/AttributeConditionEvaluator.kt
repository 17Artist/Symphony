package priv.seventeen.artist.symphony.core.attribute

import org.bukkit.Material
import org.bukkit.attribute.Attribute
import org.bukkit.entity.LivingEntity
import org.bukkit.entity.Player
import priv.seventeen.artist.blink.BlinkLog
import priv.seventeen.artist.symphony.core.storage.PlayerDataManager
import java.util.concurrent.ConcurrentHashMap

/**
 * 属性条件谓词 — 属性 @when(...) 注解使用。
 *
 * 支持谓词：
 * - in_combat / out_of_combat
 * - in_water / on_ground / flying
 * - sneaking / sprinting
 * - day / night / raining / thundering
 * - hp_low（< 30%）/ hp_full（>= 95%）
 *
 * 多条件全部为真（AND）时才激活；未知谓词会落为 false 并告警一次。
 */
object AttributeConditionEvaluator {

    private val warnedUnknown = ConcurrentHashMap.newKeySet<String>()

    fun allMatch(entity: LivingEntity, conditions: List<String>): Boolean {
        if (conditions.isEmpty()) return true
        for (cond in conditions) {
            if (!match(entity, cond.trim())) return false
        }
        return true
    }

    fun match(entity: LivingEntity, cond: String): Boolean {
        return when (cond) {
            "in_combat" -> inCombat(entity)
            "out_of_combat" -> !inCombat(entity)
            "in_water" -> entity.isInWater
            "on_ground" -> entity.isOnGround
            "flying" -> (entity as? Player)?.isFlying == true
            "sneaking" -> (entity as? Player)?.isSneaking == true
            "sprinting" -> (entity as? Player)?.isSprinting == true
            "day" -> entity.world.time in 0..11999
            "night" -> entity.world.time in 13000..22999
            "raining" -> entity.world.hasStorm()
            "thundering" -> entity.world.isThundering
            "hp_low" -> {
                val max = entity.getAttribute(Attribute.GENERIC_MAX_HEALTH)?.value ?: 20.0
                entity.health / max < 0.3
            }
            "hp_full" -> {
                val max = entity.getAttribute(Attribute.GENERIC_MAX_HEALTH)?.value ?: 20.0
                entity.health / max >= 0.95
            }
            "holding_weapon" -> (entity as? Player)?.inventory?.itemInMainHand?.type
                ?.let { it.name.endsWith("_SWORD") || it.name.endsWith("_AXE") || it == Material.TRIDENT } == true
            else -> {
                if (warnedUnknown.add(cond)) BlinkLog.warn("未知 @when 条件: §c${cond}")
                false
            }
        }
    }

    private fun inCombat(entity: LivingEntity): Boolean {
        if (entity !is Player) return false
        val data = PlayerDataManager.getData(entity.uniqueId) ?: return false
        return data.runtime.inCombat
    }
}
