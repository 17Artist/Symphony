package priv.seventeen.artist.symphony.core.attribute.provider

import org.bukkit.entity.LivingEntity
import org.bukkit.entity.Player
import priv.seventeen.artist.symphony.api.attribute.AttributeModifier
import priv.seventeen.artist.symphony.api.attribute.IAttributeProvider
import priv.seventeen.artist.symphony.core.advanced.talent.TalentManager

/**
 * 天赋门属性来源 — 优先级 660。
 */
class TalentProvider : IAttributeProvider {
    override val id = "talent"
    override val priority = 660
    override val isAsync = false  // 读取 MutableSet<String>，不能异步
    override fun appliesTo(entity: LivingEntity): Boolean = entity is Player

    override fun provide(entity: LivingEntity): List<AttributeModifier> {
        if (entity !is Player) return emptyList()
        return TalentManager.getTalentModifiers(entity)
    }
}
