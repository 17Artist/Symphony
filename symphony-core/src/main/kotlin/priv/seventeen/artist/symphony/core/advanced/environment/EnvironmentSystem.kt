package priv.seventeen.artist.symphony.core.advanced.environment

import org.bukkit.World
import org.bukkit.entity.LivingEntity
import org.bukkit.entity.Player
import priv.seventeen.artist.symphony.api.attribute.AttributeModifier
import priv.seventeen.artist.symphony.api.attribute.Operation
import priv.seventeen.artist.symphony.core.script.AriaCallbackManager
import priv.seventeen.artist.symphony.core.storage.PlayerDataManager
import java.util.concurrent.ConcurrentHashMap

/**
 * 动态环境属性系统 — 属性随环境动态调整。
 */
object EnvironmentSystem {
    private val modifiers = ConcurrentHashMap<String, EnvironmentModifier>()
    var checkInterval = 40

    fun register(modifier: EnvironmentModifier) {
        modifiers[modifier.id] = modifier
    }

    fun unregister(id: String) {
        modifiers.remove(id)
    }

    fun getAll(): Collection<EnvironmentModifier> = modifiers.values

    fun clear() {
        modifiers.clear()
    }

    /**
     * 评估实体当前环境，返回生效的属性修改器。
     */
    fun evaluateModifiers(entity: LivingEntity): List<AttributeModifier> {
        val result = mutableListOf<AttributeModifier>()
        val activeIds = mutableSetOf<String>()

        for (mod in modifiers.values) {
            if (evaluateCondition(entity, mod)) {
                activeIds.add(mod.id)
                for ((attrId, effect) in mod.attributes) {
                    val op = if (effect.operation.uppercase() == "PERCENT") Operation.PERCENT else Operation.FLAT
                    result.add(AttributeModifier(attrId, op, effect.value, "env:${mod.id}"))
                }
            }
        }

        // 更新玩家的活跃环境修正器列表
        if (entity is Player) {
            PlayerDataManager.getData(entity.uniqueId)?.runtime?.let { runtime ->
                runtime.activeEnvironmentModifiers.clear()
                runtime.activeEnvironmentModifiers.addAll(activeIds)
            }
        }

        return result
    }

    fun getActiveModifiers(player: Player): Set<String> {
        return PlayerDataManager.getData(player.uniqueId)?.runtime?.activeEnvironmentModifiers ?: emptySet()
    }

    /**
     * 检查玩家环境是否发生变化（与上次记录的活跃修正器对比）。
     * 返回 true 表示环境变化，需要重算属性。
     */
    fun hasEnvironmentChanged(player: Player): Boolean {
        val previousActive = PlayerDataManager.getData(player.uniqueId)?.runtime?.activeEnvironmentModifiers ?: emptySet()
        val currentActive = mutableSetOf<String>()
        for (mod in modifiers.values) {
            if (evaluateCondition(player, mod)) {
                currentActive.add(mod.id)
            }
        }
        return currentActive != previousActive
    }

    private fun evaluateCondition(entity: LivingEntity, mod: EnvironmentModifier): Boolean {
        // 优先使用脚本条件
        val callbackId = "environment:${mod.id}:condition"
        if (AriaCallbackManager.has(callbackId)) {
            return AriaCallbackManager.invokeCondition(callbackId, entity)
        }

        // fallback 到硬编码默认逻辑
        val world = entity.world
        val loc = entity.location

        return when (mod.type) {
            EnvironmentType.DIMENSION -> {
                when (mod.id) {
                    "nether_fire_boost" -> world.environment == World.Environment.NETHER
                    "end_void" -> world.environment == World.Environment.THE_END
                    else -> false
                }
            }
            EnvironmentType.IN_WATER -> entity.isInWater
            EnvironmentType.WEATHER -> {
                when (mod.id) {
                    "thunderstorm_lightning" -> world.isThundering && isOutdoor(entity)
                    "rain_water" -> world.hasStorm() && isOutdoor(entity)
                    else -> false
                }
            }
            EnvironmentType.TIME -> {
                val time = world.time
                when (mod.id) {
                    "night_shadow_boost" -> time in 13000..23000
                    "day_holy_boost" -> time in 0..12000
                    else -> false
                }
            }
            EnvironmentType.ALTITUDE -> {
                when (mod.id) {
                    "high_altitude" -> loc.y > 200
                    "deep_underground" -> loc.y < 0
                    else -> false
                }
            }
            else -> false
        }
    }

    private fun isOutdoor(entity: LivingEntity): Boolean {
        val loc = entity.location
        return (loc.world?.getHighestBlockYAt(loc) ?: 0) <= loc.blockY
    }

    fun registerDefaults() {
        register(EnvironmentModifier("nether_fire_boost", "地狱灼热", EnvironmentType.DIMENSION,
            mapOf(
                "fire_damage" to EnvAttributeEffect("PERCENT", 0.25),
                "fire_resistance" to EnvAttributeEffect("FLAT", -0.15),
                "ice_damage" to EnvAttributeEffect("PERCENT", -0.30)
            ), "地狱维度：火焰伤害 +25%，火焰抗性 -15%，冰霜伤害 -30%"))

        register(EnvironmentModifier("ocean_water_boost", "深海之力", EnvironmentType.IN_WATER,
            mapOf(
                "lightning_damage" to EnvAttributeEffect("PERCENT", 0.20),
                "fire_damage" to EnvAttributeEffect("PERCENT", -0.50),
                "movement_speed" to EnvAttributeEffect("PERCENT", -0.20)
            )))

        register(EnvironmentModifier("night_shadow_boost", "暗夜之力", EnvironmentType.TIME,
            mapOf(
                "dark_damage" to EnvAttributeEffect("PERCENT", 0.20),
                "dodge" to EnvAttributeEffect("FLAT", 0.05),
                "holy_damage" to EnvAttributeEffect("PERCENT", -0.15)
            )))

        register(EnvironmentModifier("thunderstorm_lightning", "雷暴增幅", EnvironmentType.WEATHER,
            mapOf(
                "lightning_damage" to EnvAttributeEffect("PERCENT", 0.50),
                "lightning_resistance" to EnvAttributeEffect("FLAT", -0.10)
            )))

        register(EnvironmentModifier("high_altitude", "高空稀薄", EnvironmentType.ALTITUDE,
            mapOf(
                "movement_speed" to EnvAttributeEffect("PERCENT", 0.10)
            )))
    }
}
