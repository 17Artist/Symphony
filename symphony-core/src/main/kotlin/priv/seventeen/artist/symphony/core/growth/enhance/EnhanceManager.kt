package priv.seventeen.artist.symphony.core.growth.enhance

import org.bukkit.Bukkit
import org.bukkit.entity.Player
import org.bukkit.inventory.ItemStack
import priv.seventeen.artist.symphony.api.event.EnhanceEvent
import priv.seventeen.artist.symphony.api.growth.EnhanceResult
import priv.seventeen.artist.symphony.core.attribute.AttributeCalculator
import priv.seventeen.artist.symphony.nms.SymphonyItemData
import java.util.concurrent.ThreadLocalRandom

class EnhanceManager {
    var maxLevel = 15

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

    fun getEnhanceLevel(item: ItemStack): Int {
        return SymphonyItemData.getInt(item, "enhance_level") ?: 0
    }

    fun setEnhanceLevel(item: ItemStack, level: Int) {
        SymphonyItemData.setInt(item, "enhance_level", level)
    }

    fun getMultiplier(level: Int): Double {
        return levelConfigs[level]?.multiplier ?: 1.0
    }

    fun enhance(player: Player, item: ItemStack, protections: List<ItemStack>): EnhanceResult {
        val currentLevel = getEnhanceLevel(item)
        if (currentLevel >= maxLevel) return EnhanceResult.MAX_LEVEL

        val config = levelConfigs[currentLevel + 1] ?: return EnhanceResult.MAX_LEVEL
        val random = ThreadLocalRandom.current().nextDouble()

        val result = when {
            random < config.successRate -> {
                setEnhanceLevel(item, currentLevel + 1)
                AttributeCalculator.markDirty(player)
                EnhanceResult.SUCCESS
            }
            random < config.successRate + config.destroyRate -> {
                EnhanceResult.DESTROYED
            }
            else -> {
                val newLevel = maxOf(0, currentLevel - 1)
                setEnhanceLevel(item, newLevel)
                AttributeCalculator.markDirty(player)
                EnhanceResult.FAILURE
            }
        }
        val newLevel = getEnhanceLevel(item)
        Bukkit.getPluginManager().callEvent(
            EnhanceEvent(player, item, currentLevel, newLevel, result)
        )
        return result
    }
}
