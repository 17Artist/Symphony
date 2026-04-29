package priv.seventeen.artist.symphony.core.attribute.provider

import org.bukkit.entity.LivingEntity
import org.bukkit.entity.Player
import priv.seventeen.artist.symphony.api.attribute.AttributeModifier
import priv.seventeen.artist.symphony.api.attribute.IAttributeProvider
import priv.seventeen.artist.symphony.api.attribute.Operation
import priv.seventeen.artist.symphony.core.growth.level.LevelManager
import priv.seventeen.artist.symphony.core.script.AriaCallbackManager
import priv.seventeen.artist.symphony.core.storage.PlayerDataManager

/**
 * 等级属性成长 Provider — 从 LevelManager.attributeGrowth 配置读取每级属性加成。
 * 优先级 150，在 BaseProvider(100) 之后、EquipmentProvider(200) 之前。
 */
class LevelProvider(private val levelManager: LevelManager) : IAttributeProvider {
    override val id = "level_growth"
    override val priority = 150
    override fun appliesTo(entity: LivingEntity): Boolean = entity is Player

    override fun provide(entity: LivingEntity): List<AttributeModifier> {
        if (entity !is Player) return emptyList()
        val growth = levelManager.attributeGrowth
        if (growth.isEmpty()) return emptyList()
        val data = PlayerDataManager.getData(entity.uniqueId) ?: return emptyList()
        val level = data.persistent.level

        return growth.mapNotNull { (attrId, entry) ->
            val value = if (entry.formula != null) {
                // Aria 公式优先
                try {
                    val callbackId = "growth_formula:$attrId"
                    if (!AriaCallbackManager.has(callbackId)) {
                        AriaCallbackManager.compile(callbackId, entry.formula)
                    }
                    (AriaCallbackManager.invoke(callbackId, level) as? Number)?.toDouble()
                        ?: (entry.base + entry.perLevel * (level - 1))
                } catch (_: Exception) {
                    entry.base + entry.perLevel * (level - 1)
                }
            } else {
                entry.base + entry.perLevel * (level - 1)
            }
            if (value != 0.0) AttributeModifier(attrId, Operation.FLAT, value, "level_growth:$attrId") else null
        }
    }
}
