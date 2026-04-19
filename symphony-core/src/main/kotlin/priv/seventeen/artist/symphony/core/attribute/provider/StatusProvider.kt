package priv.seventeen.artist.symphony.core.attribute.provider

import org.bukkit.entity.LivingEntity
import priv.seventeen.artist.symphony.api.attribute.AttributeModifier
import priv.seventeen.artist.symphony.api.attribute.IAttributeProvider
import priv.seventeen.artist.symphony.core.advanced.status.StatusLayerSystem

/**
 * 状态层属性来源 — 优先级 670。
 */
class StatusProvider : IAttributeProvider {
    override val id = "status"
    override val priority = 670

    override fun provide(entity: LivingEntity): List<AttributeModifier> {
        return StatusLayerSystem.getStatusModifiers(entity)
    }
}
