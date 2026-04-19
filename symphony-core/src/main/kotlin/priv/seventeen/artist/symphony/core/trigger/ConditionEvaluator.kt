package priv.seventeen.artist.symphony.core.trigger

import priv.seventeen.artist.blink.BlinkLog
import priv.seventeen.artist.symphony.api.trigger.ITriggerContext
import priv.seventeen.artist.symphony.core.affix.AffixManagerImpl
import priv.seventeen.artist.symphony.core.attribute.AttributeCache
import priv.seventeen.artist.symphony.core.attribute.AttributeCalculator
import priv.seventeen.artist.symphony.core.storage.PlayerDataManager
import java.util.concurrent.ThreadLocalRandom

/**
 * 条件求值器 — 支持 AND/OR/NOT 组合，短路策略。
 */
object ConditionEvaluator {

    fun evaluate(
        conditions: List<Map<String, Any>>,
        context: ITriggerContext,
        params: Map<String, Any>
    ): Boolean {
        if (conditions.isEmpty()) return true
        return conditions.all { evaluateSingle(it, context, params) }
    }

    private fun evaluateSingle(
        condition: Map<String, Any>,
        context: ITriggerContext,
        params: Map<String, Any>
    ): Boolean {
        val type = condition["type"]?.toString()?.uppercase() ?: return true

        return when (type) {
            "AND" -> {
                @Suppress("UNCHECKED_CAST")
                val children = condition["children"] as? List<Map<String, Any>> ?: return true
                children.all { evaluateSingle(it, context, params) }
            }
            "OR" -> {
                @Suppress("UNCHECKED_CAST")
                val children = condition["children"] as? List<Map<String, Any>> ?: return true
                children.any { evaluateSingle(it, context, params) }
            }
            "NOT" -> {
                @Suppress("UNCHECKED_CAST")
                val children = condition["children"] as? List<Map<String, Any>> ?: return true
                !children.all { evaluateSingle(it, context, params) }
            }
            "CHANCE" -> {
                val value = resolveValue(condition["value"], params)
                ThreadLocalRandom.current().nextDouble(100.0) < value
            }
            "COOLDOWN" -> {
                val value = resolveValue(condition["value"], params).toLong()
                // key 包含触发类型 + 冷却时长 + 词条参数哈希，确保不同词条独立冷却
                val key = "cd:${context.triggerType.id}:${value}:${params.hashCode()}"
                if (CooldownManager.isOnCooldown(context.entity.uniqueId, key)) {
                    false
                } else {
                    CooldownManager.setCooldown(context.entity.uniqueId, key, value)
                    true
                }
            }
            "HEALTH_ABOVE" -> {
                val threshold = resolveValue(condition["value"], params) / 100.0
                val entity = context.entity
                entity.health / (entity.getAttribute(org.bukkit.attribute.Attribute.GENERIC_MAX_HEALTH)?.value ?: 20.0) > threshold
            }
            "HEALTH_BELOW" -> {
                val threshold = resolveValue(condition["value"], params) / 100.0
                val entity = context.entity
                entity.health / (entity.getAttribute(org.bukkit.attribute.Attribute.GENERIC_MAX_HEALTH)?.value ?: 20.0) < threshold
            }
            "IS_SNEAKING" -> (context.entity as? org.bukkit.entity.Player)?.isSneaking ?: false
            "IS_SPRINTING" -> (context.entity as? org.bukkit.entity.Player)?.isSprinting ?: false
            "MANA_ABOVE" -> {
                val threshold = resolveValue(condition["value"], params) / 100.0
                val data = PlayerDataManager.getData(context.entity.uniqueId)
                val maxMana = AttributeCache.get(context.entity.uniqueId, "max_mana") ?: 100.0
                val currentMana = data?.runtime?.currentMana ?: 0.0
                if (maxMana > 0) currentMana / maxMana > threshold else false
            }
            "MANA_BELOW" -> {
                val threshold = resolveValue(condition["value"], params) / 100.0
                val data = PlayerDataManager.getData(context.entity.uniqueId)
                val maxMana = AttributeCache.get(context.entity.uniqueId, "max_mana") ?: 100.0
                val currentMana = data?.runtime?.currentMana ?: 0.0
                if (maxMana > 0) currentMana / maxMana < threshold else false
            }
            "HAS_PERMISSION" -> {
                val perm = condition["value"]?.toString() ?: return true
                (context.entity as? org.bukkit.entity.Player)?.hasPermission(perm) ?: false
            }
            "IN_WORLD" -> {
                val worldName = condition["value"]?.toString() ?: return true
                context.entity.world.name == worldName
            }
            "IN_BIOME" -> {
                val biomeName = condition["value"]?.toString()?.uppercase() ?: return true
                context.entity.location.block.biome.name == biomeName
            }
            "IS_FLYING" -> (context.entity as? org.bukkit.entity.Player)?.isFlying ?: false
            "HOLDING_TYPE" -> {
                val material = condition["value"]?.toString()?.uppercase() ?: return true
                (context.entity as? org.bukkit.entity.Player)?.inventory?.itemInMainHand?.type?.name == material
            }
            "HAS_AFFIX" -> {
                val affixId = condition["value"]?.toString() ?: return true
                val affixes = AffixManagerImpl.collectEntityAffixes(context.entity)
                affixes.any { it.affixId == affixId }
            }
            "ATTRIBUTE_ABOVE" -> {
                val attrId = condition["attribute"]?.toString() ?: return true
                val threshold = resolveValue(condition["value"], params)
                AttributeCalculator.getValue(context.entity, attrId) > threshold
            }
            "ATTRIBUTE_BELOW" -> {
                val attrId = condition["attribute"]?.toString() ?: return true
                val threshold = resolveValue(condition["value"], params)
                AttributeCalculator.getValue(context.entity, attrId) < threshold
            }
            "LEVEL_RANGE" -> {
                val min = (condition["min"] as? Number)?.toInt() ?: 0
                val max = (condition["max"] as? Number)?.toInt() ?: Int.MAX_VALUE
                val level = PlayerDataManager.getData(context.entity.uniqueId)?.persistent?.level ?: 1
                level in min..max
            }
            "DAMAGE_TYPE" -> {
                val expected = condition["value"]?.toString() ?: return true
                context.get<String>("damageType") == expected
            }
            "TARGET_TYPE" -> {
                val expected = condition["value"]?.toString()?.uppercase() ?: return true
                val target = context.target ?: return false
                when (expected) {
                    "PLAYER" -> target is org.bukkit.entity.Player
                    "MOB" -> target is org.bukkit.entity.Mob
                    else -> target.type.name == expected
                }
            }
            else -> {
                val custom = CustomConditionRegistry.get(type)
                if (custom != null) {
                    try { custom.evaluate(condition, context, params) } catch (e: Exception) {
                        BlinkLog.warn("自定义条件 $type 求值异常: ${e.message}")
                        false
                    }
                } else {
                    BlinkLog.warn("未知条件类型: $type，默认不通过")
                    false
                }
            }
        }
    }

    private fun resolveValue(raw: Any?, params: Map<String, Any>): Double {
        if (raw == null) return 0.0
        val str = raw.toString()
        // 解析参数引用 {paramName}
        if (str.startsWith("{") && str.endsWith("}")) {
            val paramName = str.substring(1, str.length - 1)
            return (params[paramName] as? Number)?.toDouble() ?: 0.0
        }
        return str.toDoubleOrNull() ?: 0.0
    }
}
