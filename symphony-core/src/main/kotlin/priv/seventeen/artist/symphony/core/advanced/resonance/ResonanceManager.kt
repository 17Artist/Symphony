package priv.seventeen.artist.symphony.core.advanced.resonance

import org.bukkit.entity.Player
import priv.seventeen.artist.symphony.api.affix.AffixInstance
import priv.seventeen.artist.symphony.api.attribute.AttributeModifier
import priv.seventeen.artist.symphony.api.attribute.Operation
import priv.seventeen.artist.symphony.core.affix.AffixManagerImpl
import priv.seventeen.artist.symphony.core.attribute.AttributeCache
import priv.seventeen.artist.symphony.core.storage.PlayerDataManager
import java.util.concurrent.ConcurrentHashMap

/**
 * 词条共鸣系统 — 词条组合激活质变效果。
 */
object ResonanceManager {
    private val definitions = ConcurrentHashMap<String, ResonanceDefinition>()
    var maxActive = 3

    fun register(resonance: ResonanceDefinition) {
        definitions[resonance.id] = resonance
    }

    fun unregister(id: String) {
        definitions.remove(id)
    }

    fun getAll(): Collection<ResonanceDefinition> = definitions.values

    fun clear() {
        definitions.clear()
    }

    /**
     * 检查并更新玩家的共鸣状态。
     * 在装备变更/词条变更时调用。
     */
    fun checkResonances(player: Player) {
        val data = PlayerDataManager.getData(player.uniqueId) ?: return
        val affixes = AffixManagerImpl.collectEntityAffixes(player)
        val oldActive = data.runtime.activeResonances.toSet()
        val newActive = mutableSetOf<String>()

        for (def in definitions.values) {
            if (evaluateCondition(def.condition, affixes)) {
                newActive.add(def.id)
                if (newActive.size >= maxActive) break
            }
        }

        // 更新激活状态
        data.runtime.activeResonances.clear()
        data.runtime.activeResonances.addAll(newActive)

        // 如果状态变化，标记属性重算
        if (oldActive != newActive) {
            AttributeCache.markDirty(player.uniqueId)
        }
    }

    /**
     * 获取玩家当前激活的共鸣提供的属性修改器。
     */
    fun getResonanceModifiers(player: Player): List<AttributeModifier> {
        val data = PlayerDataManager.getData(player.uniqueId) ?: return emptyList()
        val modifiers = mutableListOf<AttributeModifier>()

        for (resonanceId in data.runtime.activeResonances) {
            val def = definitions[resonanceId] ?: continue
            for ((attrId, effect) in def.effects.attributes) {
                val op = if (effect.operation.uppercase() == "PERCENT") Operation.PERCENT else Operation.FLAT
                modifiers.add(AttributeModifier(attrId, op, effect.value, "resonance:$resonanceId"))
            }
        }

        return modifiers
    }

    fun getActiveResonances(player: Player): Set<String> {
        return PlayerDataManager.getData(player.uniqueId)?.runtime?.activeResonances ?: emptySet()
    }

    fun getProgress(player: Player, resonanceId: String): Pair<Int, Int>? {
        val def = definitions[resonanceId] ?: return null
        val affixes = AffixManagerImpl.collectEntityAffixes(player)
        return when (def.condition.type) {
            ResonanceConditionType.AFFIX_TAG_COUNT -> {
                val tag = def.condition.tag ?: return null
                val current = countAffixesWithTag(affixes, tag)
                Pair(current, def.condition.count)
            }
            ResonanceConditionType.AFFIX_ID_SET -> {
                val matched = def.condition.affixIds.count { id -> affixes.any { it.affixId == id } }
                Pair(matched, def.condition.affixIds.size)
            }
            else -> null
        }
    }

    private fun evaluateCondition(condition: ResonanceCondition, affixes: List<AffixInstance>): Boolean {
        return when (condition.type) {
            ResonanceConditionType.AFFIX_TAG_COUNT -> {
                val tag = condition.tag ?: return false
                countAffixesWithTag(affixes, tag) >= condition.count
            }
            ResonanceConditionType.AFFIX_ID_SET -> {
                condition.affixIds.all { id -> affixes.any { it.affixId == id } }
            }
            ResonanceConditionType.AFFIX_RARITY_COUNT -> {
                val rarity = condition.rarity ?: return false
                affixes.count { AffixManagerImpl.getDefinition(it.affixId)?.rarity?.name == rarity } >= condition.count
            }
            ResonanceConditionType.AFFIX_LEVEL_SUM -> {
                affixes.sumOf { it.level } >= condition.minSum
            }
            ResonanceConditionType.MULTI_TAG -> {
                if (condition.mode == "DISTINCT") {
                    condition.tags.all { (tag, count) ->
                        countAffixesWithTag(affixes, tag) >= count
                    }
                } else {
                    condition.tags.all { (tag, count) ->
                        countAffixesWithTag(affixes, tag) >= count
                    }
                }
            }
            else -> false
        }
    }

    private fun countAffixesWithTag(affixes: List<AffixInstance>, tag: String): Int {
        return affixes.count { instance ->
            val def = AffixManagerImpl.getDefinition(instance.affixId)
            def != null && tag in def.tags
        }
    }
}
