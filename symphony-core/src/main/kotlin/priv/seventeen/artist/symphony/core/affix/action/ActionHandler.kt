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
 * 防御侧触发器集合：在这些触发器中 `context.entity` 是受害者、`context.target` 是攻击者。
 * 用于 [resolveTargets] 解析 `TRIGGER_ATTACKER` / `TRIGGER_TARGET` 时正确指向。
 *
 * 攻击侧触发器（ON_ATTACK / ON_KILL / ON_MELEE_ATTACK 等）则相反：
 * `context.entity` = 攻击者、`context.target` = 受害者。
 */
private val DEFEND_SIDE_TRIGGERS: Set<String> = setOf(
    "ON_DEFEND", "ON_DAMAGED", "ON_BLOCK", "ON_DODGE", "ON_LOW_HEALTH"
)

/**
 * 根据 action 配置中的 target 参数解析目标实体列表。
 * 支持：SELF / TRIGGER_TARGET / TRIGGER_ATTACKER / NEARBY_ENEMIES / NEARBY_ALLIES
 * 默认（无 target 参数时）返回 [defaultTarget]。
 *
 * `TRIGGER_ATTACKER` / `TRIGGER_TARGET` 的语义按触发器侧自动反转：
 * - 攻击侧触发器（ON_ATTACK 等）：TRIGGER_ATTACKER = entity（自己），TRIGGER_TARGET = target（受害者）
 * - 防御侧触发器（ON_DEFEND 等）：TRIGGER_ATTACKER = target（攻击者），TRIGGER_TARGET = entity（自己）
 */
fun resolveTargets(
    params: Map<String, Any>,
    context: ITriggerContext,
    affixParams: Map<String, Any>,
    defaultTarget: LivingEntity? = null
): List<LivingEntity> {
    val targetParam = resolveParam(params["target"], affixParams).uppercase().ifEmpty { return listOfNotNull(defaultTarget) }
    val radius = resolveDouble(params["radius"], affixParams).let { if (it <= 0.0) 5.0 else it }
    val isDefendSide = context.triggerType.id in DEFEND_SIDE_TRIGGERS
    return when (targetParam) {
        "SELF" -> listOfNotNull(context.entity)
        "TRIGGER_ATTACKER" -> {
            val attacker = if (isDefendSide) context.target else context.entity
            listOfNotNull(attacker)
        }
        "TRIGGER_TARGET" -> {
            // 攻击侧 = 受害者(target)；防御侧 = 自己(entity)
            val target = if (isDefendSide) context.entity else context.target
            listOfNotNull(target)
        }
        "TRIGGER_VICTIM" -> {
            // 显式语义：受害者。攻击侧=target，防御侧=entity
            val victim = if (isDefendSide) context.entity else context.target
            listOfNotNull(victim)
        }
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
