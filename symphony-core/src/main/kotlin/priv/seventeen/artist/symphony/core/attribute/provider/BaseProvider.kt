package priv.seventeen.artist.symphony.core.attribute.provider

import org.bukkit.entity.LivingEntity
import org.bukkit.entity.Player
import priv.seventeen.artist.symphony.api.attribute.AttributeModifier
import priv.seventeen.artist.symphony.api.attribute.IAttributeProvider

/**
 * BaseProvider — 保留为空壳。
 * 等级成长逻辑统一由 LevelProvider 从 level.yml 配置驱动，
 * 不再在此处硬编码。
 */
class BaseProvider : IAttributeProvider {
    override val id = "base"
    override val priority = 100
    override fun appliesTo(entity: LivingEntity): Boolean = entity is Player

    override fun provide(entity: LivingEntity): List<AttributeModifier> = emptyList()
}
