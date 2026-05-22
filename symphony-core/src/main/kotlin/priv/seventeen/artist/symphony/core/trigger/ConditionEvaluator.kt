package priv.seventeen.artist.symphony.core.trigger

import org.bukkit.attribute.Attribute
import org.bukkit.entity.Animals
import org.bukkit.entity.Player
import priv.seventeen.artist.blink.BlinkLog
import priv.seventeen.artist.symphony.api.SymphonyAPI
import priv.seventeen.artist.symphony.api.trigger.ITriggerContext
import priv.seventeen.artist.symphony.core.affix.AffixManagerImpl
import priv.seventeen.artist.symphony.core.attribute.AttributeCache
import priv.seventeen.artist.symphony.core.attribute.AttributeCalculator
import priv.seventeen.artist.symphony.core.script.AriaCallbackManager
import priv.seventeen.artist.symphony.core.storage.PlayerDataManager
import java.util.concurrent.ThreadLocalRandom

/**
 * 条件求值器 — 支持 AND/OR/NOT 组合，短路策略。
 */
object ConditionEvaluator {

    private val UNDEAD_TYPES = setOf(
        "SKELETON", "ZOMBIE", "ZOMBIE_VILLAGER", "STRAY", "HUSK", "PHANTOM",
        "DROWNED", "WITHER_SKELETON", "WITHER", "ZOMBIFIED_PIGLIN", "ZOGLIN",
        "SKELETON_HORSE", "ZOMBIE_HORSE"
    )
    private val ARTHROPOD_TYPES = setOf(
        "SPIDER", "CAVE_SPIDER", "BEE", "SILVERFISH", "ENDERMITE"
    )
    private val BOSS_TYPES = setOf(
        "ENDER_DRAGON", "WITHER", "ELDER_GUARDIAN", "WARDEN"
    )

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
                entity.health / (entity.getAttribute(Attribute.GENERIC_MAX_HEALTH)?.value ?: 20.0) > threshold
            }
            "HEALTH_BELOW" -> {
                val threshold = resolveValue(condition["value"], params) / 100.0
                val entity = context.entity
                entity.health / (entity.getAttribute(Attribute.GENERIC_MAX_HEALTH)?.value ?: 20.0) < threshold
            }
            "IS_SNEAKING" -> (context.entity as? Player)?.isSneaking ?: false
            "IS_SPRINTING" -> (context.entity as? Player)?.isSprinting ?: false
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
                (context.entity as? Player)?.hasPermission(perm) ?: false
            }
            "IN_WORLD" -> {
                val worldName = condition["value"]?.toString() ?: return true
                context.entity.world.name == worldName
            }
            "IN_BIOME" -> {
                val biomeName = condition["value"]?.toString()?.uppercase() ?: return true
                context.entity.location.block.biome.name == biomeName
            }
            "IS_FLYING" -> (context.entity as? Player)?.isFlying ?: false
            "HOLDING_TYPE" -> {
                val material = condition["value"]?.toString()?.uppercase() ?: return true
                (context.entity as? Player)?.inventory?.itemInMainHand?.type?.name == material
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
                    "PLAYER" -> target is Player
                    "MOB" -> target is org.bukkit.entity.Mob
                    "ANIMAL" -> target is Animals
                    "UNDEAD" -> target.type.name in UNDEAD_TYPES
                    "ARTHROPOD" -> target.type.name in ARTHROPOD_TYPES
                    "BOSS" -> target.type.name in BOSS_TYPES
                    else -> target.type.name == expected
                }
            }
            "WEARING_SET" -> {
                val setId = condition["value"]?.toString() ?: return true
                val player = context.entity as? Player ?: return false
                val api = SymphonyAPI.getInstance() ?: return false
                val sets = api.getGrowthManager().getActiveSets(player)
                sets.containsKey(setId)
            }
            "SCRIPT" -> {
                val code = (condition["code"] ?: condition["value"])?.toString() ?: return true
                try {
                    val callbackId = "condition_script:${code.hashCode()}"
                    if (!AriaCallbackManager.has(callbackId)) {
                        AriaCallbackManager.compile(callbackId, code)
                    }
                    // 注入与 ScriptActionHandler 一致的 trigger_* 上下文，
                    // 让 SCRIPT 条件可以读 server.trigger_entity / trigger_victim / trigger_damage 等
                    val vars = buildMap<String, Any?> {
                        put("trigger_entity", context.entity)
                        put("trigger_type", context.triggerType.id)
                        put("trigger_location", context.location)
                        context.target?.let { put("trigger_victim", it) }
                        context.damage?.let { put("trigger_damage", it) }
                        params.forEach { (k, v) -> put(k, v) }
                    }
                    AriaCallbackManager.invokeConditionWithVars(callbackId, vars)
                } catch (e: Exception) {
                    BlinkLog.warn("SCRIPT 条件求值异常: ${e.message}")
                    false
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
