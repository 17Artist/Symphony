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
            val level = enhanceManager.getEnhanceLevel(item)
            if (level > 0) {
                val multiplier = enhanceManager.getMultiplier(level)
                if (multiplier > 1.0) {
                    val percent = multiplier - 1.0
                    // 读取物品上的 Symphony 属性，对每个属性应用强化倍率
                    val attrsJson = SymphonyItemData.getString(item, "attributes")
                    if (attrsJson != null) {
                        try {
                            val type = object : TypeToken<ModifierListData>() {}.type
                            val data: ModifierListData = gson.fromJson(attrsJson, type)
                            for (mod in data.modifiers) {
                                modifiers.add(AttributeModifier(mod.attr, Operation.PERCENT, percent, "enhance:slot$index"))
                            }
                        } catch (_: Exception) {
                            // 解析失败时兜底
                            modifiers.add(AttributeModifier("physical_damage", Operation.PERCENT, percent, "enhance:slot$index"))
                            modifiers.add(AttributeModifier("physical_defense", Operation.PERCENT, percent, "enhance:slot$index"))
                        }
                    } else {
                        // 无属性数据时兜底
                        modifiers.add(AttributeModifier("physical_damage", Operation.PERCENT, percent, "enhance:slot$index"))
                        modifiers.add(AttributeModifier("physical_defense", Operation.PERCENT, percent, "enhance:slot$index"))
                    }
                }
            }
        }

        return modifiers
    }
}
