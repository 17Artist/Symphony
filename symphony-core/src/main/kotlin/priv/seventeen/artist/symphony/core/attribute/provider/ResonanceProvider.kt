package priv.seventeen.artist.symphony.core.attribute.provider

import org.bukkit.entity.LivingEntity
import org.bukkit.entity.Player
import priv.seventeen.artist.symphony.api.attribute.AttributeModifier
import priv.seventeen.artist.symphony.api.attribute.IAttributeProvider
import priv.seventeen.artist.symphony.core.advanced.resonance.ResonanceManager

/**
 * 词条共鸣属性来源 — 优先级 650。
 */
class ResonanceProvider : IAttributeProvider {
    override val id = "resonance"
    override val priority = 650
    override val isAsync = true
    override fun appliesTo(entity: LivingEntity): Boolean = entity is Player

    override fun provide(entity: LivingEntity): List<AttributeModifier> {
        if (entity !is Player) return emptyList()
        return ResonanceManager.getResonanceModifiers(entity)
    }
}
