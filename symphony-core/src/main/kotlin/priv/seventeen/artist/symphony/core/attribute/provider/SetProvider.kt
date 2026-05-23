package priv.seventeen.artist.symphony.core.attribute.provider

import org.bukkit.entity.LivingEntity
import org.bukkit.entity.Player
import priv.seventeen.artist.symphony.api.attribute.AttributeModifier
import priv.seventeen.artist.symphony.api.attribute.IAttributeProvider
import priv.seventeen.artist.symphony.api.attribute.Operation
import priv.seventeen.artist.symphony.core.growth.set.SetManager
import priv.seventeen.artist.symphony.core.storage.PlayerDataManager

class SetProvider(private val setManager: SetManager) : IAttributeProvider {
    override val id = "set"
    override val priority = 550
    override fun appliesTo(entity: LivingEntity): Boolean = entity is Player

    override fun provide(entity: LivingEntity): List<AttributeModifier> {
        if (entity !is Player) return emptyList()
        val modifiers = mutableListOf<AttributeModifier>()

        val activeSets = setManager.detectSets(entity).toMutableMap()

        // 虚拟套装件数叠加
        val data = PlayerDataManager.getData(entity.uniqueId)
        if (data != null) {
            for ((setId, runtime) in data.runtime.activeVirtualSets) {
                activeSets[setId] = (activeSets[setId] ?: 0) + runtime.totalPieces
            }
        }

        for ((setId, pieceCount) in activeSets) {
            val bonuses = setManager.getActiveBonuses(setId, pieceCount)
            for (bonus in bonuses) {
                for ((attrId, attrBonus) in bonus.attributes) {
                    val op = Operation.parse(attrBonus.operation)
                    modifiers.add(AttributeModifier(attrId, op, attrBonus.value, "set:$setId"))
                }
            }
        }

        return modifiers
    }
}
