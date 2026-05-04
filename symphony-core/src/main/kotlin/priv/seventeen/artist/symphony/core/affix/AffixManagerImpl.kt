package priv.seventeen.artist.symphony.core.affix

import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import org.bukkit.entity.LivingEntity
import org.bukkit.entity.Player
import org.bukkit.inventory.ItemStack
import priv.seventeen.artist.symphony.api.affix.*
import priv.seventeen.artist.symphony.core.skill.builtin.MythicMobSpawnListener
import priv.seventeen.artist.symphony.nms.SymphonyItemData
import java.util.UUID
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.ThreadLocalRandom

class AffixManagerImpl : IAffixManager {
    companion object {
        private val definitions = ConcurrentHashMap<String, AffixDefinition>()
        private val pools = ConcurrentHashMap<String, AffixPool>()
        private val gson = Gson()

        fun getDefinition(id: String): AffixDefinition? = definitions[id]

        fun collectEntityAffixes(entity: LivingEntity): List<AffixInstance> {
            val result = mutableListOf<AffixInstance>()
            if (entity is Player) {
                val equipment = entity.equipment ?: return result
                listOfNotNull(
                    equipment.itemInMainHand,
                    equipment.itemInOffHand,
                    equipment.helmet,
                    equipment.chestplate,
                    equipment.leggings,
                    equipment.boots
                ).forEach { item ->
                    result.addAll(readAffixes(item))
                }
            } else {
                // 非玩家实体（如 MM 怪物）的虚拟词条
                MythicMobSpawnListener.getMobAffixes(entity.uniqueId)?.let { result += it }
            }
            return result
        }

        fun collectItemAffixes(item: ItemStack): List<AffixInstance> = readAffixes(item)

        fun definitionIds(): Set<String> = definitions.keys
        fun poolIds(): Set<String> = pools.keys

        fun clearDefinitions() {
            definitions.clear()
            pools.clear()
        }

        private fun readAffixes(item: ItemStack): List<AffixInstance> {
            val json = SymphonyItemData.getString(item, "affixes") ?: return emptyList()
            return try {
                val type = object : TypeToken<List<AffixInstanceData>>() {}.type
                val dataList: List<AffixInstanceData> = gson.fromJson(json, type)
                dataList.map { it.toInstance() }
            } catch (e: Exception) {
                emptyList()
            }
        }
    }

    override fun registerAffix(affix: IAffix) {
        if (affix is AffixDefinition) {
            definitions[affix.id] = affix
        }
    }

    override fun getAffix(id: String): IAffix? = definitions[id]

    override fun getAllAffixes(): Collection<IAffix> = definitions.values

    override fun getAffixesByRarity(rarity: AffixRarity): Collection<IAffix> {
        return definitions.values.filter { it.rarity == rarity }
    }

    override fun getAffixes(item: ItemStack): List<AffixInstance> = readAffixes(item)

    override fun addAffix(item: ItemStack, instance: AffixInstance): Boolean {
        val current = getAffixes(item).toMutableList()
        current.add(instance)
        writeAffixes(item, current)
        return true
    }

    override fun removeAffix(item: ItemStack, uuid: UUID): Boolean {
        val current = getAffixes(item).toMutableList()
        val removed = current.removeIf { it.uuid == uuid }
        if (removed) writeAffixes(item, current)
        return removed
    }

    override fun clearAffixes(item: ItemStack) {
        SymphonyItemData.remove(item, "affixes")
    }

    override fun generateAffixes(poolId: String, rarity: AffixRarity, luck: Double): List<AffixInstance> {
        val pool = pools[poolId] ?: return emptyList()
        return pool.generate(rarity, luck)
    }

    override fun applyAffixes(item: ItemStack, affixes: List<AffixInstance>) {
        writeAffixes(item, affixes)
    }

    override fun registerActionHandler(type: String, handler: IActionHandler) {
        AffixProcessor.registerHandler(type, handler)
    }

    override fun unregisterActionHandler(type: String) {
        AffixProcessor.unregisterHandler(type)
    }

    override fun getRegisteredActionTypes(): Set<String> = AffixProcessor.getRegisteredActionTypes()

    fun registerPool(pool: AffixPool) {
        pools[pool.id] = pool
    }

    private fun writeAffixes(item: ItemStack, affixes: List<AffixInstance>) {
        val dataList = affixes.map { AffixInstanceData.fromInstance(it) }
        SymphonyItemData.setString(item, "affixes", gson.toJson(dataList))
    }
}

// JSON 序列化辅助
data class AffixInstanceData(
    val uuid: String,
    val id: String,
    val level: Int,
    val params: Map<String, Any>
) {
    fun toInstance() = AffixInstance(
        uuid = UUID.fromString(uuid),
        affixId = id,
        level = level,
        parameters = params
    )

    companion object {
        fun fromInstance(inst: AffixInstance) = AffixInstanceData(
            uuid = inst.uuid.toString(),
            id = inst.affixId,
            level = inst.level,
            params = inst.parameters
        )
    }
}
