package priv.seventeen.artist.symphony.core.affix.action

import org.bukkit.entity.LivingEntity
import org.bukkit.entity.Mob
import org.bukkit.entity.Player
import priv.seventeen.artist.symphony.api.affix.IActionHandler
import priv.seventeen.artist.symphony.api.trigger.ITriggerContext

/** 内部别名：与公开 [IActionHandler] 等价，内置处理器继续实现此 interface。 */
interface ActionHandler : IActionHandler

fun resolveParam(raw: Any?, affixParams: Map<String, Any>): String {
    if (raw == null) return ""
    val str = raw.toString()
    var result = str
    val regex = Regex("\\{(\\w+)}")
    for (match in regex.findAll(str)) {
        val key = match.groupValues[1]
        val value = affixParams[key]?.toString() ?: match.value
        result = result.replace(match.value, value)
    }
    return result
}

fun resolveDouble(raw: Any?, affixParams: Map<String, Any>): Double {
    return resolveParam(raw, affixParams).toDoubleOrNull() ?: 0.0
}

fun resolveInt(raw: Any?, affixParams: Map<String, Any>): Int {
    val str = resolveParam(raw, affixParams)
    // 先尝试整数解析；失败则走 Double → Int（兼容 NBT 序列化后 "3.0" 的情况）
    return str.toIntOrNull() ?: str.toDoubleOrNull()?.toInt() ?: 0
}

/**
 * 根据 action 配置中的 target 参数解析目标实体列表。
 * 支持：SELF / TRIGGER_TARGET / TRIGGER_ATTACKER / NEARBY_ENEMIES / NEARBY_ALLIES
 * 默认（无 target 参数时）返回 [defaultTarget]。
 */
fun resolveTargets(
    params: Map<String, Any>,
    context: ITriggerContext,
    affixParams: Map<String, Any>,
    defaultTarget: LivingEntity? = null
): List<LivingEntity> {
    val targetParam = resolveParam(params["target"], affixParams).uppercase().ifEmpty { return listOfNotNull(defaultTarget) }
    val radius = resolveDouble(params["radius"], affixParams).let { if (it <= 0.0) 5.0 else it }
    return when (targetParam) {
        "SELF" -> listOfNotNull(context.entity)
        "TRIGGER_TARGET" -> listOfNotNull(context.target as? LivingEntity)
        "TRIGGER_ATTACKER" -> listOfNotNull(context.entity)
        "NEARBY_ENEMIES" -> {
            val origin = context.entity
            origin.getNearbyEntities(radius, radius, radius)
                .filterIsInstance<LivingEntity>()
                .filter { it != origin && isHostile(origin, it) }
        }
        "NEARBY_ALLIES" -> {
            val origin = context.entity
            origin.getNearbyEntities(radius, radius, radius)
                .filterIsInstance<LivingEntity>()
                .filter { it != origin && !isHostile(origin, it) }
        }
        "NEARBY_ALL" -> {
            val origin = context.entity
            origin.getNearbyEntities(radius, radius, radius)
                .filterIsInstance<LivingEntity>()
                .filter { it != origin }
        }
        else -> listOfNotNull(defaultTarget)
    }
}

/** 判断 target 是否对 origin 敌对。 */
private fun isHostile(origin: LivingEntity, target: LivingEntity): Boolean {
    // 玩家 vs 玩家：视为敌对（PvP 场景）
    if (origin is Player && target is Player) return true
    // 玩家 vs 怪物 / 怪物 vs 玩家：视为敌对
    if (origin is Player && target is Mob) return true
    if (origin is Mob && target is Player) return true
    // 怪物 vs 怪物：同类不敌对
    if (origin is Mob && target is Mob) return origin.type != target.type
    return false
}
