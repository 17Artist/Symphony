package priv.seventeen.artist.symphony.core.growth.set

import org.bukkit.entity.Player
import org.bukkit.inventory.EquipmentSlot
import org.bukkit.inventory.ItemStack
import priv.seventeen.artist.symphony.nms.SymphonyItemData
import java.util.concurrent.ConcurrentHashMap

class SetManager {
    private val setDefinitions = ConcurrentHashMap<String, SetDefinition>()

    data class SetDefinition(
        val id: String,
        val displayName: String,
        val bonuses: Map<Int, SetBonus>
    )

    data class SetBonus(
        val display: String,
        val attributes: Map<String, AttributeBonus>,
        val triggers: List<Map<String, Any>> = emptyList()
    )

    data class AttributeBonus(val operation: String, val value: Double)

    fun clear() {
        setDefinitions.clear()
    }

    fun unregister(id: String): Boolean = setDefinitions.remove(id) != null

    fun registerSet(set: SetDefinition) {
        setDefinitions[set.id] = set
    }

    fun getSetId(item: ItemStack): String? {
        return SymphonyItemData.getString(item, "set_id")
    }

    fun detectSets(player: Player): Map<String, Int> {
        val setPieces = mutableMapOf<String, Int>()
        val equipment = player.equipment ?: return setPieces

        listOfNotNull(
            equipment.itemInMainHand,
            equipment.itemInOffHand,
            equipment.helmet,
            equipment.chestplate,
            equipment.leggings,
            equipment.boots
        ).forEach { item ->
            val setId = getSetId(item) ?: return@forEach
            setPieces[setId] = (setPieces[setId] ?: 0) + 1
        }

        return setPieces
    }

    fun getActiveBonuses(setId: String, pieceCount: Int): List<SetBonus> {
        val definition = setDefinitions[setId] ?: return emptyList()
        return definition.bonuses.filter { it.key <= pieceCount }.values.toList()
    }

    fun getDefinition(id: String): SetDefinition? = setDefinitions[id]
    fun getAllDefinitions(): Collection<SetDefinition> = setDefinitions.values
}
