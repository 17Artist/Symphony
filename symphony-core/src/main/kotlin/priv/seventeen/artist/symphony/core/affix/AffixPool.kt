package priv.seventeen.artist.symphony.core.affix

import priv.seventeen.artist.symphony.api.affix.AffixInstance
import priv.seventeen.artist.symphony.api.affix.AffixRarity
import java.util.UUID
import java.util.concurrent.ThreadLocalRandom

data class AffixPoolEntry(
    val affixId: String,
    val weight: Int,
    val levelRange: IntRange
)

data class AffixPoolGeneration(
    val minAffixes: Int = 1,
    val maxAffixes: Int = 4,
    val rarityWeights: Map<AffixRarity, IntRange> = emptyMap(),
    val allowDuplicates: Boolean = false,
    val luckInfluence: Double = 0.1
)

class AffixPool(
    val id: String,
    val displayName: String,
    val entries: List<AffixPoolEntry>,
    val generation: AffixPoolGeneration = AffixPoolGeneration()
) {
    fun generate(rarity: AffixRarity, luck: Double): List<AffixInstance> {
        val random = ThreadLocalRandom.current()
        val range = generation.rarityWeights[rarity]
            ?: IntRange(generation.minAffixes, generation.maxAffixes)
        val count = random.nextInt(range.first, range.last + 1)

        val candidates = entries.toMutableList()
        val result = mutableListOf<AffixInstance>()

        repeat(count) {
            if (candidates.isEmpty()) return@repeat

            // 加权随机
            val totalWeight = candidates.sumOf { (it.weight * (1.0 + luck * generation.luckInfluence)).toInt() }
            if (totalWeight <= 0) return@repeat

            var roll = random.nextInt(totalWeight)
            var selected: AffixPoolEntry? = null
            for (entry in candidates) {
                val effectiveWeight = (entry.weight * (1.0 + luck * generation.luckInfluence)).toInt()
                roll -= effectiveWeight
                if (roll < 0) {
                    selected = entry
                    break
                }
            }

            selected?.let { entry ->
                val level = random.nextInt(entry.levelRange.first, entry.levelRange.last + 1)
                val definition = AffixManagerImpl.getDefinition(entry.affixId)
                val params = definition?.getLevelParams(level) ?: emptyMap()

                result.add(AffixInstance(
                    uuid = UUID.randomUUID(),
                    affixId = entry.affixId,
                    level = level,
                    parameters = params
                ))

                // 互斥组检查
                definition?.exclusiveGroup?.let { group ->
                    candidates.removeIf { e ->
                        AffixManagerImpl.getDefinition(e.affixId)?.exclusiveGroup == group
                    }
                }

                if (!generation.allowDuplicates) {
                    candidates.remove(entry)
                }
            }
        }

        return result
    }
}
