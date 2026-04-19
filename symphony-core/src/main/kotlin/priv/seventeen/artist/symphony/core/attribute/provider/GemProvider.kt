package priv.seventeen.artist.symphony.core.attribute.provider

import org.bukkit.entity.LivingEntity
import org.bukkit.entity.Player
import priv.seventeen.artist.symphony.api.attribute.AttributeModifier
import priv.seventeen.artist.symphony.api.attribute.IAttributeProvider
import priv.seventeen.artist.symphony.api.attribute.Operation
import priv.seventeen.artist.symphony.core.growth.gem.GemManager

class GemProvider(private val gemManager: GemManager) : IAttributeProvider {
    override val id = "gem"
    override val priority = 300
    override fun appliesTo(entity: LivingEntity): Boolean = entity is Player

    // gemId -> level -> attributes
    private val gemAttributes = mutableMapOf<String, Map<Int, Map<String, GemAttr>>>()

    data class GemAttr(val operation: String, val value: Double)

    fun registerGem(gemId: String, levels: Map<Int, Map<String, GemAttr>>) {
        gemAttributes[gemId] = levels
    }

    override fun provide(entity: LivingEntity): List<AttributeModifier> {
        if (entity !is Player) return emptyList()
        val equipment = entity.equipment ?: return emptyList()
        val modifiers = mutableListOf<AttributeModifier>()

        listOfNotNull(
            equipment.itemInMainHand, equipment.itemInOffHand,
            equipment.helmet, equipment.chestplate, equipment.leggings, equipment.boots
        ).forEach { item ->
            gemManager.getGemSlots(item).forEach { slot ->
                val gemId = slot.gemId ?: return@forEach
                val attrs = gemAttributes[gemId]?.get(slot.gemLevel) ?: return@forEach
                attrs.forEach { (attrId, attr) ->
                    val op = if (attr.operation.uppercase() == "PERCENT") Operation.PERCENT else Operation.FLAT
                    modifiers.add(AttributeModifier(attrId, op, attr.value, "gem:$gemId:${slot.index}"))
                }
            }
        }

        return modifiers
    }
}
