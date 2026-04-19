package priv.seventeen.artist.symphony.core.attribute.provider

import org.bukkit.entity.LivingEntity
import org.bukkit.entity.Player
import priv.seventeen.artist.symphony.api.attribute.AttributeModifier
import priv.seventeen.artist.symphony.api.attribute.IAttributeProvider
import priv.seventeen.artist.symphony.api.attribute.Operation
import priv.seventeen.artist.symphony.core.growth.rune.RuneRegistry
import priv.seventeen.artist.symphony.core.storage.PlayerDataManager

class RuneProvider : IAttributeProvider {
    override val id = "rune"
    override val priority = 600
    override fun appliesTo(entity: LivingEntity): Boolean = entity is Player

    override fun provide(entity: LivingEntity): List<AttributeModifier> {
        if (entity !is Player) return emptyList()
        val data = PlayerDataManager.getData(entity.uniqueId) ?: return emptyList()
        val modifiers = mutableListOf<AttributeModifier>()

        for ((runeId, runeData) in data.persistent.runes) {
            if (!runeData.active) continue
            val def = RuneRegistry.get(runeId) ?: continue
            val passives = def.passivesFor(runeData.level)
            for ((attrId, pass) in passives) {
                val op = if (pass.operation.uppercase() == "PERCENT") Operation.PERCENT else Operation.FLAT
                modifiers.add(AttributeModifier(attrId, op, pass.value, "rune:$runeId"))
            }
        }
        return modifiers
    }
}
