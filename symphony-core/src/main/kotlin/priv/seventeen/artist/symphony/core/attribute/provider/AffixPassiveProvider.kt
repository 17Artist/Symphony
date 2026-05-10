package priv.seventeen.artist.symphony.core.attribute.provider

import org.bukkit.entity.LivingEntity
import priv.seventeen.artist.symphony.api.attribute.AttributeModifier
import priv.seventeen.artist.symphony.api.attribute.IAttributeProvider
import priv.seventeen.artist.symphony.api.attribute.Operation
import priv.seventeen.artist.symphony.core.affix.AffixManagerImpl

class AffixPassiveProvider : IAttributeProvider {
    override val id = "affix_passive"
    override val priority = 600

    override fun provide(entity: LivingEntity): List<AttributeModifier> {
        val affixes = AffixManagerImpl.collectEntityAffixes(entity)
        val modifiers = mutableListOf<AttributeModifier>()

        for (affix in affixes) {
            val def = AffixManagerImpl.getDefinition(affix.affixId) ?: continue
            for ((attrId, passive) in def.passiveAttributes) {
                val op = if (passive.operation.uppercase() == "PERCENT") Operation.PERCENT else Operation.FLAT
                // 解析参数引用
                val valueStr = passive.value
                val value = resolveValue(valueStr, affix.parameters)
                modifiers.add(AttributeModifier(attrId, op, value, "affix:${affix.affixId}:${affix.uuid}"))
            }
        }

        return modifiers
    }

    private fun resolveValue(raw: String, params: Map<String, Any>): Double {
        // 快速路径：纯参数引用 "{param}"
        if (raw.startsWith("{") && raw.endsWith("}") && raw.indexOf('{', 1) == -1) {
            val key = raw.substring(1, raw.length - 1)
            val value = params[key] ?: return 0.0
            return when (value) {
                is Number -> value.toDouble()
                else -> value.toString().toDoubleOrNull() ?: 0.0
            }
        }
        // 通用路径：多参数替换
        var str = raw
        val regex = Regex("\\{(\\w+)}")
        for (match in regex.findAll(raw)) {
            val key = match.groupValues[1]
            val value = params[key]?.toString() ?: return 0.0
            str = str.replace(match.value, value)
        }
        return str.toDoubleOrNull() ?: 0.0
    }
}
