package priv.seventeen.artist.symphony.core.attribute.provider

import org.bukkit.entity.LivingEntity
import org.bukkit.entity.Player
import priv.seventeen.artist.symphony.api.attribute.AttributeModifier
import priv.seventeen.artist.symphony.api.attribute.IAttributeProvider
import priv.seventeen.artist.symphony.api.attribute.Operation
import priv.seventeen.artist.symphony.core.growth.enhance.EnhanceManager
import priv.seventeen.artist.symphony.nms.SymphonyItemData
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken

class EnhanceProvider(private val enhanceManager: EnhanceManager) : IAttributeProvider {
    override val id = "enhance"
    override val priority = 500
    override fun appliesTo(entity: LivingEntity): Boolean = entity is Player

    companion object {
        private val gson = Gson()
        private val listType = object : TypeToken<List<Map<String, Any>>>() {}.type
    }

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
                            val attrs: List<Map<String, Any>> = gson.fromJson(attrsJson, listType)
                            for (attr in attrs) {
                                val attrId = attr["id"]?.toString() ?: continue
                                modifiers.add(AttributeModifier(attrId, Operation.PERCENT, percent, "enhance:slot$index"))
                            }
                        } catch (_: Exception) {}
                    }
                    // 兜底：如果物品没有 attributes 数据，至少强化物伤和物防
                    if (attrsJson == null) {
                        modifiers.add(AttributeModifier("physical_damage", Operation.PERCENT, percent, "enhance:slot$index"))
                        modifiers.add(AttributeModifier("physical_defense", Operation.PERCENT, percent, "enhance:slot$index"))
                    }
                }
            }
        }

        return modifiers
    }
}
