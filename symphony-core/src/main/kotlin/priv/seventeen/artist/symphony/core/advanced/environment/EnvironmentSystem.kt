package priv.seventeen.artist.symphony.core.advanced.environment

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

        // 通用类型匹配
        val world = entity.world
        val loc = entity.location

        return when (mod.type) {
            EnvironmentType.DIMENSION -> {
                val target = mod.dimension?.uppercase() ?: return false
                world.environment.name.equals(target, ignoreCase = true)
            }
            EnvironmentType.BIOME -> {
                if (mod.biomes.isEmpty()) return false
                val biomeName = loc.block.biome.name
                mod.biomes.any { it.equals(biomeName, ignoreCase = true) }
            }
            EnvironmentType.IN_WATER -> entity.isInWater
            EnvironmentType.WEATHER -> {
                val outdoorOk = !mod.requireOutdoor || isOutdoor(entity)
                if (!outdoorOk) return false
                when (mod.weather?.uppercase()) {
                    "THUNDER" -> world.isThundering
                    "RAIN" -> world.hasStorm() && !world.isThundering
                    "STORM" -> world.hasStorm()
                    "CLEAR" -> !world.hasStorm()
                    else -> false
                }
            }
            EnvironmentType.TIME -> {
                val outdoorOk = !mod.requireOutdoor || isOutdoor(entity)
                if (!outdoorOk) return false
                if (mod.timeStart < 0 || mod.timeEnd < 0) return false
                val time = world.time
                if (mod.timeStart <= mod.timeEnd) {
                    time in mod.timeStart..mod.timeEnd
                } else {
                    // 跨越 24000 边界（如 23000 → 1000）
                    time >= mod.timeStart || time <= mod.timeEnd
                }
            }
            EnvironmentType.ALTITUDE -> {
                if (mod.minY == Double.NEGATIVE_INFINITY && mod.maxY == Double.POSITIVE_INFINITY) {
                    return false
                }
                loc.y in mod.minY..mod.maxY
            }
            EnvironmentType.COMBAT_TARGET -> false
        }
    }

    private fun isOutdoor(entity: LivingEntity): Boolean {
        val loc = entity.location
        return (loc.world?.getHighestBlockYAt(loc) ?: 0) <= loc.blockY
    }

    fun registerDefaults() {
        register(EnvironmentModifier(
            id = "nether_fire_boost",
            displayName = "地狱灼热",
            type = EnvironmentType.DIMENSION,
            dimension = "NETHER",
            attributes = mapOf(
                "fire_damage" to EnvAttributeEffect("PERCENT", 0.25),
                "fire_resistance" to EnvAttributeEffect("FLAT", -0.15),
                "ice_damage" to EnvAttributeEffect("PERCENT", -0.30)
            ),
            description = "地狱维度：火焰伤害 +25%，火焰抗性 -15%，冰霜伤害 -30%"
        ))

        register(EnvironmentModifier(
            id = "ocean_water_boost",
            displayName = "深海之力",
            type = EnvironmentType.IN_WATER,
            attributes = mapOf(
                "lightning_damage" to EnvAttributeEffect("PERCENT", 0.20),
                "fire_damage" to EnvAttributeEffect("PERCENT", -0.50),
                "movement_speed" to EnvAttributeEffect("PERCENT", -0.20)
            ),
            description = "水下：闪电伤害 +20%，火焰伤害 -50%，移动速度 -20%"
        ))

        register(EnvironmentModifier(
            id = "night_shadow_boost",
            displayName = "暗夜之力",
            type = EnvironmentType.TIME,
            timeStart = 13000,
            timeEnd = 23000,
            attributes = mapOf(
                "dark_damage" to EnvAttributeEffect("PERCENT", 0.20),
                "dodge" to EnvAttributeEffect("FLAT", 0.05),
                "holy_damage" to EnvAttributeEffect("PERCENT", -0.15)
            ),
            description = "夜晚（13000-23000 tick）：暗影伤害 +20%，闪避 +5%，神圣伤害 -15%"
        ))

        register(EnvironmentModifier(
            id = "thunderstorm_lightning",
            displayName = "雷暴增幅",
            type = EnvironmentType.WEATHER,
            weather = "THUNDER",
            requireOutdoor = true,
            attributes = mapOf(
                "lightning_damage" to EnvAttributeEffect("PERCENT", 0.50),
                "lightning_resistance" to EnvAttributeEffect("FLAT", -0.10)
            ),
            description = "户外雷暴：闪电伤害 +50%，闪电抗性 -10%"
        ))

        register(EnvironmentModifier(
            id = "high_altitude",
            displayName = "高空稀薄",
            type = EnvironmentType.ALTITUDE,
            minY = 200.0,
            attributes = mapOf(
                "movement_speed" to EnvAttributeEffect("PERCENT", 0.10)
            ),
            description = "高空（Y > 200）：移动速度 +10%"
        ))
    }
}
