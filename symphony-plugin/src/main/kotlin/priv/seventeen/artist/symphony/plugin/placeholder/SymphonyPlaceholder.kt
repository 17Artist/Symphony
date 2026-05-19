package priv.seventeen.artist.symphony.plugin.placeholder

import org.bukkit.entity.Player
import priv.seventeen.artist.symphony.core.attribute.AttributeCalculator
import priv.seventeen.artist.symphony.core.attribute.AttributeRegistry
import priv.seventeen.artist.symphony.core.storage.PlayerDataManager
import priv.seventeen.artist.symphony.nms.SymphonyItemData
import priv.seventeen.artist.symphony.plugin.SymphonyPlugin

/**
 * PlaceholderAPI 占位符解析。
 *
 * 支持格式（%symphony_xxx%）：
 * - attribute_<id>          格式化后的属性最终值
 * - attribute_<id>_base     属性基础值（仅 BaseProvider 贡献）
 * - attribute_<id>_raw      原始数值（不做 format）
 * - level                   玩家等级
 * - exp                     当前经验
 * - exp_required            升级所需经验
 * - exp_percent             经验百分比
 * - exp_bar                 经验进度条
 * - mana                    当前法力
 * - mana_max                最大法力
 * - mana_percent            法力百分比
 * - enhance_level           主手强化等级
 * - enhance_level_<slot>    指定槽位强化等级
 * - rune_<id>_level         符文等级
 * - rune_<id>_active        符文是否激活 (true/false)
 * - rune_<id>_fragments     符文碎片数量
 * - set_<id>_pieces         套装已激活件数
 * - combat_power            战斗力
 * - stat_<key>              统计数据
 */
object SymphonyPlaceholder {

    private val levelManager get() = SymphonyPlugin.growthManager.levelManager

    private const val BAR_LENGTH = 10
    private const val BAR_FILLED = '█'
    private const val BAR_EMPTY = '░'

    fun onRequest(player: Player, params: String): String? {
        // 优先匹配固定前缀
        return when {
            // --- 属性 ---
            params.startsWith("attribute_") -> handleAttribute(player, params.removePrefix("attribute_"))

            // --- 等级 / 经验 ---
            params == "level" -> getLevel(player).toString()
            params == "exp" -> getExp(player).toString()
            params == "exp_required" -> {
                val level = getLevel(player)
                levelManager.getRequiredExp(level).toString()
            }
            params == "exp_percent" -> {
                val data = PlayerDataManager.getData(player.uniqueId) ?: return "0%"
                val required = levelManager.getRequiredExp(data.persistent.level)
                if (required > 0) "${"%.1f".format(data.persistent.exp.toDouble() / required * 100)}%" else "0%"
            }
            params == "exp_bar" -> {
                val data = PlayerDataManager.getData(player.uniqueId) ?: return buildBar(0.0)
                val required = levelManager.getRequiredExp(data.persistent.level)
                val ratio = if (required > 0) data.persistent.exp.toDouble() / required else 0.0
                buildBar(ratio)
            }

            // --- 法力 ---
            params == "mana" -> {
                PlayerDataManager.getData(player.uniqueId)?.runtime?.currentMana?.let { "%.0f".format(it) } ?: "0"
            }
            params == "mana_max" -> "%.0f".format(AttributeCalculator.getValue(player, "max_mana"))
            params == "mana_percent" -> {
                val current = PlayerDataManager.getData(player.uniqueId)?.runtime?.currentMana ?: 0.0
                val max = AttributeCalculator.getValue(player, "max_mana")
                if (max > 0) "${"%.1f".format(current / max * 100)}%" else "0%"
            }

            // --- 强化等级 ---
            params == "enhance_level" -> {
                SymphonyItemData.getInt(player.inventory.itemInMainHand, "enhance_level")?.toString() ?: "0"
            }
            params.startsWith("enhance_level_") -> {
                val slot = params.removePrefix("enhance_level_")
                getEnhanceLevelBySlot(player, slot)
            }

            // --- 符文 ---
            params.startsWith("rune_") -> handleRune(player, params.removePrefix("rune_"))

            // --- 套装 ---
            params.startsWith("set_") && params.endsWith("_pieces") -> {
                val setId = params.removePrefix("set_").removeSuffix("_pieces")
                val sets = SymphonyPlugin.growthManager.setManager.detectSets(player)
                (sets[setId] ?: 0).toString()
            }

            // --- 战斗力 ---
            params == "combat_power" -> AttributeCalculator.getValue(player, "combat_power").toInt().toString()

            // --- 统计 ---
            params.startsWith("stat_") -> handleStat(player, params.removePrefix("stat_"))

            else -> null
        }
    }

    // ========== 属性处理 ==========

    private fun handleAttribute(player: Player, sub: String): String? {
        // 检查是否有 _base 或 _raw 后缀
        return when {
            sub.endsWith("_base") -> {
                val attrId = sub.removeSuffix("_base")
                val attr = AttributeRegistry.get(attrId) ?: return null
                formatAttr(attrId, attr.defaultValue)
            }
            sub.endsWith("_raw") -> {
                val attrId = sub.removeSuffix("_raw")
                if (AttributeRegistry.get(attrId) == null) return null
                "%.6f".format(AttributeCalculator.getValue(player, attrId))
            }
            else -> {
                val attrId = sub
                val value = AttributeCalculator.getValue(player, attrId)
                formatAttr(attrId, value)
            }
        }
    }

    // ========== 符文处理 ==========

    private fun handleRune(player: Player, sub: String): String? {
        // sub 格式: <runeId>_level / <runeId>_active / <runeId>_fragments
        val suffix = when {
            sub.endsWith("_level") -> "_level"
            sub.endsWith("_active") -> "_active"
            sub.endsWith("_fragments") -> "_fragments"
            else -> return null
        }
        val runeId = sub.removeSuffix(suffix)
        val data = PlayerDataManager.getData(player.uniqueId) ?: return "0"
        return when (suffix) {
            "_level" -> (data.persistent.runes[runeId]?.level ?: 0).toString()
            "_active" -> (data.persistent.runes[runeId]?.active ?: false).toString()
            "_fragments" -> (data.persistent.runeFragments[runeId] ?: 0).toString()
            else -> null
        }
    }

    // ========== 统计处理 ==========

    private fun handleStat(player: Player, key: String): String? {
        val stats = PlayerDataManager.getData(player.uniqueId)?.persistent?.statistics ?: return "0"
        return when (key) {
            "damage_dealt" -> "%.0f".format(stats.totalDamageDealt)
            "damage_taken" -> "%.0f".format(stats.totalDamageTaken)
            "kills" -> stats.totalKills.toString()
            "deaths" -> stats.totalDeaths.toString()
            "highest_combo" -> stats.highestCombo.toString()
            "reactions" -> stats.totalReactionsTriggered.toString()
            else -> null
        }
    }

    // ========== 工具方法 ==========

    private fun getLevel(player: Player): Int =
        PlayerDataManager.getData(player.uniqueId)?.persistent?.level ?: 1

    private fun getExp(player: Player): Long =
        PlayerDataManager.getData(player.uniqueId)?.persistent?.exp ?: 0L

    private fun getEnhanceLevelBySlot(player: Player, slot: String): String {
        val item = when (slot.uppercase()) {
            "MAIN", "MAIN_HAND", "HAND" -> player.inventory.itemInMainHand
            "OFF", "OFF_HAND" -> player.inventory.itemInOffHand
            "HELMET", "HEAD" -> player.inventory.helmet
            "CHEST", "CHESTPLATE" -> player.inventory.chestplate
            "LEGS", "LEGGINGS" -> player.inventory.leggings
            "BOOTS", "FEET" -> player.inventory.boots
            else -> return "0"
        } ?: return "0"
        return SymphonyItemData.getInt(item, "enhance_level")?.toString() ?: "0"
    }

    private fun formatAttr(attrId: String, value: Double): String {
        val attr = AttributeRegistry.get(attrId)
        return when (attr?.format) {
            "percent" -> "${"%.1f".format(value * 100)}%"
            "integer" -> value.toInt().toString()
            else -> "%.2f".format(value)
        }
    }

    private fun buildBar(ratio: Double): String {
        val filled = (ratio.coerceIn(0.0, 1.0) * BAR_LENGTH).toInt()
        return BAR_FILLED.toString().repeat(filled) + BAR_EMPTY.toString().repeat(BAR_LENGTH - filled)
    }
}