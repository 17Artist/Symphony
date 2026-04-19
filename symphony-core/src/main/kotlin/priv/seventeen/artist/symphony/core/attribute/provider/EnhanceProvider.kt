package priv.seventeen.artist.symphony.core.attribute.provider

import org.bukkit.entity.LivingEntity
import org.bukkit.entity.Player
import priv.seventeen.artist.symphony.api.attribute.AttributeModifier
import priv.seventeen.artist.symphony.api.attribute.IAttributeProvider
import priv.seventeen.artist.symphony.api.attribute.Operation
import priv.seventeen.artist.symphony.core.growth.enhance.EnhanceManager

class EnhanceProvider(private val enhanceManager: EnhanceManager) : IAttributeProvider {
    override val id = "enhance"
    override val priority = 500
    override fun appliesTo(entity: LivingEntity): Boolean = entity is Player

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
                    // 强化倍率作为 PERCENT 修改器应用到所有装备属性
                    modifiers.add(AttributeModifier("physical_damage", Operation.PERCENT, multiplier - 1.0, "enhance:slot$index"))
                    modifiers.add(AttributeModifier("physical_defense", Operation.PERCENT, multiplier - 1.0, "enhance:slot$index"))
                }
            }
        }

        return modifiers
    }
}
