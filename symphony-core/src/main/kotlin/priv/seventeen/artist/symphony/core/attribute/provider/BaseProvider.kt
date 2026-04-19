package priv.seventeen.artist.symphony.core.attribute.provider

import org.bukkit.entity.LivingEntity
import org.bukkit.entity.Player
import priv.seventeen.artist.symphony.api.attribute.AttributeModifier
import priv.seventeen.artist.symphony.api.attribute.IAttributeProvider
import priv.seventeen.artist.symphony.api.attribute.Operation
import priv.seventeen.artist.symphony.core.storage.PlayerDataManager

class BaseProvider : IAttributeProvider {
    override val id = "base"
    override val priority = 100
    override val isAsync = true
    override fun appliesTo(entity: LivingEntity): Boolean = entity is Player

    // 每级属性成长配置：attributeId -> perLevel
    private val growthConfig = mutableMapOf(
        "max_health" to 2.0,
        "physical_damage" to 0.5,
        "physical_defense" to 0.3,
        "max_mana" to 5.0,
        "mana_regen" to 0.1
    )

    fun setGrowth(attributeId: String, perLevel: Double) {
        growthConfig[attributeId] = perLevel
    }

    override fun provide(entity: LivingEntity): List<AttributeModifier> {
        if (entity !is Player) return emptyList()
        val data = PlayerDataManager.getData(entity.uniqueId) ?: return emptyList()
        val level = data.persistent.level

        return growthConfig.map { (attrId, perLevel) ->
            AttributeModifier(attrId, Operation.FLAT, perLevel * (level - 1), "base:level_growth")
        }
    }
}
