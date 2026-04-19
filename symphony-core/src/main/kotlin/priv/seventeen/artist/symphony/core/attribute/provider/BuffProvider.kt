package priv.seventeen.artist.symphony.core.attribute.provider

import org.bukkit.entity.LivingEntity
import priv.seventeen.artist.symphony.api.attribute.AttributeModifier
import priv.seventeen.artist.symphony.api.attribute.IAttributeProvider
import priv.seventeen.artist.symphony.core.storage.PlayerDataManager

/**
 * Buff 属性来源 — 优先级 700。
 */
class BuffProvider : IAttributeProvider {
    override val id = "buff"
    override val priority = 700
    override val isAsync = true
    override fun appliesTo(entity: LivingEntity): Boolean {
        val data = PlayerDataManager.getData(entity.uniqueId) ?: return false
        return data.runtime.activeBuffs.isNotEmpty()
    }

    override fun provide(entity: LivingEntity): List<AttributeModifier> {
        val data = PlayerDataManager.getData(entity.uniqueId) ?: return emptyList()
        return data.runtime.activeBuffs
            .filter { !it.isExpired() }
            .map { buff ->
                AttributeModifier(buff.attribute, buff.operation, buff.value * buff.currentStacks, buff.source)
            }
    }
}
