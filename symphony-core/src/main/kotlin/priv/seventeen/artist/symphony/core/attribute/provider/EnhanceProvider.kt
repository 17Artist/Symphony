package priv.seventeen.artist.symphony.core.attribute.provider

import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import org.bukkit.entity.LivingEntity
import org.bukkit.entity.Player
import priv.seventeen.artist.symphony.api.attribute.AttributeModifier
import priv.seventeen.artist.symphony.api.attribute.IAttributeProvider
import priv.seventeen.artist.symphony.api.attribute.Operation
import priv.seventeen.artist.symphony.core.growth.enhance.EnhanceManager
import priv.seventeen.artist.symphony.nms.SymphonyItemData

class EnhanceProvider(private val enhanceManager: EnhanceManager) : IAttributeProvider {
    override val id = "enhance"
    override val priority = 500
    override fun appliesTo(entity: LivingEntity): Boolean = entity is Player

    private val gson = Gson()

    private data class ModifierListData(val modifiers: List<ModData> = emptyList())
    private data class ModData(val attr: String, val op: String, val value: Double, val source: String = "base")

    override fun provide(entity: LivingEntity): List<AttributeModifier> {
        if (entity !is Player) return emptyList()
        val equipment = entity.equipment ?: return emptyList()
        val modifiers = mutableListOf<AttributeModifier>()

        listOfNotNull(
            equipment.itemInMainHand, equipment.itemInOffHand,
            equipment.helmet, equipment.chestplate, equipment.leggings, equipment.boots
        ).forEachIndexed { index, item ->
            val level = enhanceManager.getEnhanceLevel(item)  // 已 clamp 到 [0, maxLevel]
            if (level <= 0) return@forEachIndexed
            val multiplier = enhanceManager.getMultiplier(level)
            // 强化只允许正向加成；配置异常（multiplier <= 1.0）时跳过该件
            if (multiplier <= 1.0) return@forEachIndexed
            val percent = (multiplier - 1.0).coerceAtLeast(0.0)
            if (percent <= 0.0) return@forEachIndexed
            // 读取物品上的 Symphony 属性，对每个属性应用强化倍率
            val attrsJson = SymphonyItemData.getString(item, "attributes")
            if (attrsJson != null) {
                try {
                    val type = object : TypeToken<ModifierListData>() {}.type
                    val data: ModifierListData = gson.fromJson(attrsJson, type)
                    for (mod in data.modifiers) {
                        modifiers.add(AttributeModifier(mod.attr, Operation.PERCENT, percent, "enhance:slot$index:lv$level"))
                    }
                } catch (_: Exception) {
                    // 解析失败时兜底
                    modifiers.add(AttributeModifier("physical_damage", Operation.PERCENT, percent, "enhance:slot$index:lv$level"))
                    modifiers.add(AttributeModifier("physical_defense", Operation.PERCENT, percent, "enhance:slot$index:lv$level"))
                }
            } else {
                // 无属性数据时兜底
                modifiers.add(AttributeModifier("physical_damage", Operation.PERCENT, percent, "enhance:slot$index:lv$level"))
                modifiers.add(AttributeModifier("physical_defense", Operation.PERCENT, percent, "enhance:slot$index:lv$level"))
            }
        }

        return modifiers
    }
}
