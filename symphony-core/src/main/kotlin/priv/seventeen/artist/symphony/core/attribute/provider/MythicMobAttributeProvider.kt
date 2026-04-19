package priv.seventeen.artist.symphony.core.attribute.provider

import org.bukkit.entity.LivingEntity
import priv.seventeen.artist.symphony.api.attribute.AttributeModifier
import priv.seventeen.artist.symphony.api.attribute.IAttributeProvider
import priv.seventeen.artist.symphony.core.skill.builtin.MythicMobDataStore

/**
 * MythicMobs 怪物属性来源 — 从 `MythicMobDataStore` 读取 spawn 时解析好的数据。
 *
 * 优先级 150（介于 base=100 和 equipment=200 之间）。
 * 仅对有对应 UUID 数据的实体生效，其他实体直接 appliesTo → false 跳过。
 */
class MythicMobAttributeProvider : IAttributeProvider {
    override val id = "mythic_mob"
    override val priority = 150
    override val isAsync = true

    override fun appliesTo(entity: LivingEntity): Boolean =
        MythicMobDataStore.get(entity.uniqueId) != null

    override fun provide(entity: LivingEntity): List<AttributeModifier> {
        return MythicMobDataStore.get(entity.uniqueId)?.modifiers ?: emptyList()
    }
}
