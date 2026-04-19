package priv.seventeen.artist.symphony.core.attribute.provider

import org.bukkit.entity.LivingEntity
import priv.seventeen.artist.symphony.api.attribute.AttributeModifier
import priv.seventeen.artist.symphony.api.attribute.IAttributeProvider
import priv.seventeen.artist.symphony.core.advanced.environment.EnvironmentSystem

/**
 * 环境修正属性来源 — 优先级 750。
 */
class EnvironmentProvider : IAttributeProvider {
    override val id = "environment"
    override val priority = 750

    override fun provide(entity: LivingEntity): List<AttributeModifier> {
        return EnvironmentSystem.evaluateModifiers(entity)
    }
}
