package priv.seventeen.artist.symphony.core.attribute.provider

import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import org.bukkit.entity.LivingEntity
import org.bukkit.entity.Player
import priv.seventeen.artist.symphony.api.attribute.AttributeModifier
import priv.seventeen.artist.symphony.api.attribute.IAttributeProvider
import priv.seventeen.artist.symphony.api.attribute.Operation
import priv.seventeen.artist.symphony.nms.SymphonyItemData

class EquipmentProvider : IAttributeProvider {
    override val id = "equipment"
    override val priority = 200
    override fun appliesTo(entity: LivingEntity): Boolean = entity is Player

    private val gson = Gson()

    override fun provide(entity: LivingEntity): List<AttributeModifier> {
        if (entity !is Player) return emptyList()
        val equipment = entity.equipment ?: return emptyList()
        val modifiers = mutableListOf<AttributeModifier>()

        listOfNotNull(
            equipment.itemInMainHand,
            equipment.itemInOffHand,
            equipment.helmet,
            equipment.chestplate,
            equipment.leggings,
            equipment.boots
        ).forEach { item ->
            val json = SymphonyItemData.getString(item, "attributes") ?: return@forEach
            try {
                val type = object : TypeToken<ModifierListData>() {}.type
                val data: ModifierListData = gson.fromJson(json, type)
                data.modifiers.forEach { mod ->
                    val op = if (mod.op.uppercase() == "PERCENT") Operation.PERCENT else Operation.FLAT
                    modifiers.add(AttributeModifier(mod.attr, op, mod.value, "equipment:${mod.source}"))
                }
            } catch (_: Exception) {}
        }

        return modifiers
    }

    private data class ModifierListData(val modifiers: List<ModData> = emptyList())
    private data class ModData(val attr: String, val op: String, val value: Double, val source: String = "base")
}
