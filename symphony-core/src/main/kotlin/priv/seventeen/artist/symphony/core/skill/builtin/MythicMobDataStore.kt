package priv.seventeen.artist.symphony.core.skill.builtin

import priv.seventeen.artist.symphony.api.affix.AffixInstance
import priv.seventeen.artist.symphony.api.attribute.AttributeModifier
import priv.seventeen.artist.symphony.api.attribute.Operation
import priv.seventeen.artist.symphony.core.affix.AffixManagerImpl
import java.util.UUID
import java.util.concurrent.ConcurrentHashMap

/**
 * MM 怪物配置解析后的属性 / 词条数据存储。
 *
 * key = 怪物实体 UUID；在 spawn 时写入、death 时清理。
 */
object MythicMobDataStore {

    data class MobData(
        val modifiers: List<AttributeModifier>,
        val affixes: List<AffixInstance>
    )

    private val store = ConcurrentHashMap<UUID, MobData>()

    fun put(uuid: UUID, data: MobData) { store[uuid] = data }
    fun get(uuid: UUID): MobData? = store[uuid]
    fun remove(uuid: UUID) { store.remove(uuid) }
    fun clear() { store.clear() }

    /**
     * 把 MM 配置里的 `Symphony.attributes` map 转为 FLAT modifiers。
     * 百分号后缀（如 "15%"）自动识别为 PERCENT。
     */
    fun parseAttributes(raw: Map<String, Any>, mobId: String): List<AttributeModifier> {
        val out = mutableListOf<AttributeModifier>()
        for ((attrId, value) in raw) {
            val (op, num) = when (value) {
                is Number -> Operation.FLAT to value.toDouble()
                is String -> {
                    val trimmed = value.trim()
                    if (trimmed.endsWith("%")) {
                        Operation.PERCENT to (trimmed.dropLast(1).toDoubleOrNull() ?: 0.0) / 100.0
                    } else {
                        Operation.FLAT to (trimmed.toDoubleOrNull() ?: 0.0)
                    }
                }
                else -> continue
            }
            out += AttributeModifier(attrId, op, num, "mythic_mob:$mobId")
        }
        return out
    }

    /**
     * 把 MM 配置里的 `Symphony.affixes` 列表转为 AffixInstance 列表。
     * 支持两种项形态：
     *   - 字符串（直接视为 affixId，level=1）
     *   - map `{ id: xxx, level: N, params: {...} }`
     * 自动从 AffixDefinition 填充等级参数，自定义 params 可覆盖。
     */
    @Suppress("UNCHECKED_CAST")
    fun parseAffixes(raw: List<Any>): List<AffixInstance> {
        val out = mutableListOf<AffixInstance>()
        for (item in raw) {
            when (item) {
                is String -> {
                    val def = AffixManagerImpl.getDefinition(item)
                    val params = def?.getLevelParams(1) ?: emptyMap()
                    out += AffixInstance(UUID.randomUUID(), item, 1, params)
                }
                is Map<*, *> -> {
                    val id = item["id"]?.toString() ?: continue
                    val level = (item["level"] as? Number)?.toInt() ?: 1
                    val def = AffixManagerImpl.getDefinition(id)
                    val baseParams = def?.getLevelParams(level) ?: emptyMap()
                    val customParams = (item["params"] as? Map<String, Any>) ?: emptyMap()
                    out += AffixInstance(UUID.randomUUID(), id, level, baseParams + customParams)
                }
            }
        }
        return out
    }
}
