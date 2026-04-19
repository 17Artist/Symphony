package priv.seventeen.artist.symphony.plugin.placeholder

import org.bukkit.entity.Player
import priv.seventeen.artist.symphony.core.attribute.AttributeCalculator
import priv.seventeen.artist.symphony.core.attribute.AttributeConditionEvaluator
import priv.seventeen.artist.symphony.core.attribute.AttributeRegistry
import priv.seventeen.artist.symphony.core.storage.PlayerDataManager
import priv.seventeen.artist.symphony.core.growth.level.LevelManager
import priv.seventeen.artist.symphony.nms.SymphonyItemData

object SymphonyPlaceholder {

    private val levelManager = LevelManager()

    fun onRequest(player: Player, params: String): String? {
        val parts = params.split("_", limit = 2)
        if (parts.isEmpty()) return null

        return when (parts[0]) {
            "attribute" -> {
                if (parts.size < 2) return null
                val attrId = parts[1]
                val value = AttributeCalculator.getValue(player, attrId)
                val attr = AttributeRegistry.get(attrId)
                when (attr?.format) {
                    "percent" -> "${"%.1f".format(value * 100)}%"
                    "integer" -> "${value.toInt()}"
                    else -> "%.2f".format(value)
                }
            }
            // 原始数值（不做 format 处理，方便脚本使用）
            "raw" -> {
                if (parts.size < 2) return null
                "%.6f".format(AttributeCalculator.getValue(player, parts[1]))
            }
            // 按属性 format 格式化（与 attribute_ 一致，保留作语义别名）
            "fmt" -> {
                if (parts.size < 2) return null
                val attrId = parts[1]
                val value = AttributeCalculator.getValue(player, attrId)
                val attr = AttributeRegistry.get(attrId) ?: return "%.2f".format(value)
                formatValue(value, attr.format)
            }
            // 分类聚合：cat_<category>_sum / _count
            "cat" -> {
                if (parts.size < 2) return null
                val sub = parts[1].split("_")
                if (sub.size < 2) return null
                val kind = sub.last()
                val category = sub.dropLast(1).joinToString("_")
                val attrs = AttributeRegistry.getByCategory(category)
                when (kind) {
                    "sum" -> "%.2f".format(attrs.sumOf { AttributeCalculator.getValue(player, it.id) })
                    "count" -> attrs.size.toString()
                    else -> null
                }
            }
            // @when 条件状态：when_<cond> -> 0/1
            "when" -> {
                if (parts.size < 2) return null
                if (AttributeConditionEvaluator.match(player, parts[1])) "1" else "0"
            }
            "level" -> PlayerDataManager.getData(player.uniqueId)?.persistent?.level?.toString()
            "exp" -> {
                if (parts.size < 2) return PlayerDataManager.getData(player.uniqueId)?.persistent?.exp?.toString()
                when (parts[1]) {
                    "required" -> {
                        val level = PlayerDataManager.getData(player.uniqueId)?.persistent?.level ?: 1
                        levelManager.getRequiredExp(level).toString()
                    }
                    "percent" -> {
                        val data = PlayerDataManager.getData(player.uniqueId) ?: return "0"
                        val required = levelManager.getRequiredExp(data.persistent.level)
                        if (required > 0) "${"%.1f".format(data.persistent.exp.toDouble() / required * 100)}%" else "0%"
                    }
                    else -> PlayerDataManager.getData(player.uniqueId)?.persistent?.exp?.toString()
                }
            }
            "mana" -> {
                if (parts.size < 2) return PlayerDataManager.getData(player.uniqueId)?.runtime?.currentMana?.let { "%.0f".format(it) }
                when (parts[1]) {
                    "max" -> AttributeCalculator.getValue(player, "max_mana").let { "%.0f".format(it) }
                    "percent" -> {
                        val current = PlayerDataManager.getData(player.uniqueId)?.runtime?.currentMana ?: 0.0
                        val max = AttributeCalculator.getValue(player, "max_mana")
                        if (max > 0) "${"%.1f".format(current / max * 100)}%" else "0%"
                    }
                    else -> PlayerDataManager.getData(player.uniqueId)?.runtime?.currentMana?.let { "%.0f".format(it) }
                }
            }
            "combat" -> {
                if (parts.size >= 2 && parts[1] == "power") {
                    AttributeCalculator.getValue(player, "combat_power").let { "${it.toInt()}" }
                } else null
            }
            "stat" -> {
                if (parts.size < 2) return null
                val data = PlayerDataManager.getData(player.uniqueId)?.persistent?.statistics ?: return "0"
                when (parts[1]) {
                    "damage_dealt" -> "%.0f".format(data.totalDamageDealt)
                    "damage_taken" -> "%.0f".format(data.totalDamageTaken)
                    "kills" -> data.totalKills.toString()
                    "deaths" -> data.totalDeaths.toString()
                    "highest_combo" -> data.highestCombo.toString()
                    "reactions" -> data.totalReactionsTriggered.toString()
                    else -> null
                }
            }
            "enhance" -> {
                if (parts.size >= 2 && parts[1] == "level") {
                    val item = player.inventory.itemInMainHand
                    SymphonyItemData.getInt(item, "enhance_level")?.toString() ?: "0"
                } else null
            }
            else -> null
        }
    }

    private fun formatValue(value: Double, format: String): String = when (format) {
        "percent" -> "${"%.1f".format(value * 100)}%"
        "integer" -> value.toInt().toString()
        else -> "%.2f".format(value)
    }
}
