package priv.seventeen.artist.symphony.core.attribute.provider

import org.bukkit.entity.LivingEntity
import org.bukkit.entity.Player
import priv.seventeen.artist.symphony.api.attribute.AttributeModifier
import priv.seventeen.artist.symphony.api.attribute.IAttributeProvider
import priv.seventeen.artist.symphony.api.attribute.Operation
import priv.seventeen.artist.symphony.core.growth.gem.GemManager

class GemProvider(private val gemManager: GemManager) : IAttributeProvider {
    override val id = "gem"
    override val priority = 300
    override fun appliesTo(entity: LivingEntity): Boolean = entity is Player

    // gemId -> level -> attributes
    private val gemAttributes = mutableMapOf<String, Map<Int, Map<String, GemAttr>>>()
    // gemId -> 元数据（material / display_name 等，配置层读取，物品生成 / GUI 用）
    private val gemMeta = mutableMapOf<String, GemMeta>()

    data class GemAttr(val operation: String, val value: Double)

    /** 配置中给宝石定义的展示元数据。宝石物品生成 / GUI 显示时读取。 */
    data class GemMeta(
        val displayName: String,
        val description: List<String>,
        val maxLevel: Int,
        val material: String?,
        val customModelData: Int?
    )

    fun registerGem(gemId: String, levels: Map<Int, Map<String, GemAttr>>, meta: GemMeta? = null) {
        gemAttributes[gemId] = levels
        if (meta != null) gemMeta[gemId] = meta
    }

    fun getMeta(gemId: String): GemMeta? = gemMeta[gemId]
    fun getAllMeta(): Map<String, GemMeta> = gemMeta

    /** Reload 时清空所有宝石定义，由 ConfigLoader 重新填充。 */
    fun clear() {
        gemAttributes.clear()
        gemMeta.clear()
    }

    override fun provide(entity: LivingEntity): List<AttributeModifier> {
        if (entity !is Player) return emptyList()
        val equipment = entity.equipment ?: return emptyList()
        val modifiers = mutableListOf<AttributeModifier>()

        listOfNotNull(
            equipment.itemInMainHand, equipment.itemInOffHand,
            equipment.helmet, equipment.chestplate, equipment.leggings, equipment.boots
        ).forEach { item ->
            gemManager.getGemSlots(item).forEach { slot ->
                val gemId = slot.gemId ?: return@forEach
                val attrs = gemAttributes[gemId]?.get(slot.gemLevel) ?: return@forEach
                attrs.forEach { (attrId, attr) ->
                    val op = if (attr.operation.uppercase() == "PERCENT") Operation.PERCENT else Operation.FLAT
                    modifiers.add(AttributeModifier(attrId, op, attr.value, "gem:$gemId:${slot.index}"))
                }
            }
        }

        return modifiers
    }
}
