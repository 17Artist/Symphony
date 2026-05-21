package priv.seventeen.artist.symphony.core.growth.enhance

import org.bukkit.Bukkit
import org.bukkit.Sound
import org.bukkit.entity.Player
import org.bukkit.inventory.ItemStack
import priv.seventeen.artist.symphony.api.event.EnhanceEvent
import priv.seventeen.artist.symphony.api.growth.EnhanceResult
import priv.seventeen.artist.symphony.core.attribute.AttributeCache
import priv.seventeen.artist.symphony.core.attribute.AttributeCalculator
import priv.seventeen.artist.symphony.nms.SymphonyItemData
import java.util.concurrent.ThreadLocalRandom

class EnhanceManager {
    var maxLevel = 15
    var preventDestroyItem: String? = null
    var preventDowngradeItem: String? = null
    var successBonusItem: String? = null
    var downgradeLevels = 1
    var successSound: String? = null
    var failureSound: String? = null
    var destroySound: String? = null

    data class LevelConfig(val multiplier: Double, val successRate: Double, val destroyRate: Double)

    private val levelConfigs = mutableMapOf<Int, LevelConfig>()

    fun loadDefaults() {
        levelConfigs[1] = LevelConfig(1.05, 0.95, 0.00)
        levelConfigs[2] = LevelConfig(1.10, 0.90, 0.00)
        levelConfigs[3] = LevelConfig(1.15, 0.85, 0.00)
        levelConfigs[4] = LevelConfig(1.20, 0.80, 0.00)
        levelConfigs[5] = LevelConfig(1.30, 0.70, 0.00)
        levelConfigs[6] = LevelConfig(1.40, 0.60, 0.02)
        levelConfigs[7] = LevelConfig(1.50, 0.50, 0.05)
        levelConfigs[8] = LevelConfig(1.65, 0.40, 0.08)
        levelConfigs[9] = LevelConfig(1.80, 0.30, 0.10)
        levelConfigs[10] = LevelConfig(2.00, 0.20, 0.15)
        levelConfigs[11] = LevelConfig(2.20, 0.15, 0.20)
        levelConfigs[12] = LevelConfig(2.50, 0.10, 0.25)
        levelConfigs[13] = LevelConfig(2.80, 0.08, 0.30)
        levelConfigs[14] = LevelConfig(3.20, 0.05, 0.35)
        levelConfigs[15] = LevelConfig(3.50, 0.03, 0.40)
    }

    fun loadConfig(configs: Map<Int, LevelConfig>) {
        levelConfigs.clear()
        levelConfigs.putAll(configs)
    }

    fun getEnhanceLevel(item: ItemStack): Int {
        val raw = SymphonyItemData.getInt(item, "enhance_level") ?: 0
        return raw.coerceIn(0, maxLevel)
    }

    fun setEnhanceLevel(item: ItemStack, level: Int) {
        SymphonyItemData.setInt(item, "enhance_level", level.coerceIn(0, maxLevel))
    }

    fun getMultiplier(level: Int): Double {
        if (level <= 0) return 1.0
        // clamp 到已配置的最高等级，防止超限后加成消失或异常
        val effectiveLevel = level.coerceAtMost(maxLevel)
        levelConfigs[effectiveLevel]?.let { return it.multiplier }
        // 配置不连续：找 ≤ effectiveLevel 的最大已配置等级
        val nearest = levelConfigs.keys.filter { it in 1..effectiveLevel }.maxOrNull()
        return if (nearest != null) levelConfigs[nearest]!!.multiplier else 1.0
    }

    fun enhance(player: Player, item: ItemStack, protections: List<ItemStack>): EnhanceResult {
        val currentLevel = getEnhanceLevel(item)
        if (currentLevel >= maxLevel) return EnhanceResult.MAX_LEVEL

        val config = levelConfigs[currentLevel + 1] ?: return EnhanceResult.MAX_LEVEL

        // 保护道具检查
        var hasPreventDestroy = false
        var hasPreventDowngrade = false
        var successBonus = 0.0
        for (protection in protections) {
            if (matchProtection(protection, preventDestroyItem)) {
                hasPreventDestroy = true
            }
            if (matchProtection(protection, preventDowngradeItem)) {
                hasPreventDowngrade = true
            }
            if (matchProtection(protection, successBonusItem)) {
                successBonus += 0.1
            }
        }

        // luck 属性参与成功率计算
        val luck = AttributeCache.get(player.uniqueId, "luck") ?: 0.0
        val effectiveSuccessRate = (config.successRate + successBonus + luck * 0.01).coerceIn(0.0, 1.0)

        val random = ThreadLocalRandom.current().nextDouble()

        val result = when {
            random < effectiveSuccessRate -> {
                setEnhanceLevel(item, currentLevel + 1)
                AttributeCalculator.markDirty(player)
                EnhanceResult.SUCCESS
            }
            random < effectiveSuccessRate + config.destroyRate -> {
                if (hasPreventDestroy) {
                    EnhanceResult.FAILURE
                } else {
                    EnhanceResult.DESTROYED
                }
            }
            else -> {
                if (hasPreventDowngrade) {
                    EnhanceResult.FAILURE
                } else {
                    val newLevel = maxOf(0, currentLevel - downgradeLevels)
                    setEnhanceLevel(item, newLevel)
                    AttributeCalculator.markDirty(player)
                    EnhanceResult.FAILURE
                }
            }
        }

        // 特效
        val sound = when (result) {
            EnhanceResult.SUCCESS -> successSound
            EnhanceResult.DESTROYED -> destroySound
            else -> failureSound
        }
        sound?.let { s ->
            try {
                val enumName = s.uppercase().replace(".", "_")
                player.playSound(player.location, Sound.valueOf(enumName), 1.0f, 1.0f)
            } catch (_: Exception) {
                try { player.playSound(player.location, s, 1.0f, 1.0f) } catch (_: Exception) {}
            }
        }

        val newLevel = getEnhanceLevel(item)
        Bukkit.getPluginManager().callEvent(
            EnhanceEvent(player, item, currentLevel, newLevel, result)
        )
        return result
    }

    /**
     * 匹配保护道具 — 支持 "MATERIAL" 或 "MATERIAL:CMD" 格式。
     * 例如 "PAPER:1001" 匹配 Material=PAPER 且 CustomModelData=1001。
     */
    private fun matchProtection(item: ItemStack, pattern: String?): Boolean {
        if (pattern == null) return false
        val parts = pattern.split(":", limit = 2)
        val materialMatch = item.type.name.equals(parts[0], ignoreCase = true)
        if (!materialMatch) return false
        if (parts.size > 1) {
            val expectedCmd = parts[1].toIntOrNull() ?: return materialMatch
            val meta = item.itemMeta ?: return false
            return meta.hasCustomModelData() && meta.customModelData == expectedCmd
        }
        return true
    }
}
