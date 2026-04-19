package priv.seventeen.artist.symphony.core.attribute

import org.bukkit.entity.LivingEntity
import priv.seventeen.artist.symphony.api.attribute.*

class AttributeManagerImpl : IAttributeManager {

    override fun registerAttribute(attribute: IAttribute) {
        if (attribute is AttributeDefinition) {
            AttributeRegistry.register(attribute)
        } else {
            AttributeRegistry.register(AttributeDefinition(
                id = attribute.id,
                displayName = attribute.displayName,
                description = attribute.description,
                category = attribute.category,
                defaultValue = attribute.defaultValue,
                minValue = attribute.minValue,
                maxValue = attribute.maxValue,
                format = attribute.format,
                priority = attribute.priority,
                vanillaBinding = attribute.vanillaBinding,
                readonly = attribute.readonly,
                tags = attribute.tags
            ))
        }
    }

    override fun unregisterAttribute(id: String) {
        AttributeRegistry.unregister(id)
    }

    override fun getAttribute(id: String): IAttribute? = AttributeRegistry.get(id)

    override fun getAllAttributes(): Collection<IAttribute> = AttributeRegistry.getAll()

    override fun getAttributesByCategory(category: String): Collection<IAttribute> {
        return AttributeRegistry.getByCategory(category)
    }

    override fun registerProvider(provider: IAttributeProvider) {
        AttributeProviderRegistry.register(provider)
    }

    override fun unregisterProvider(id: String) {
        AttributeProviderRegistry.unregister(id)
    }

    override fun getProviders(): List<IAttributeProvider> = AttributeProviderRegistry.getAll()

    override fun getValue(entity: LivingEntity, attributeId: String): Double {
        return AttributeCalculator.getValue(entity, attributeId)
    }

    override fun getValues(entity: LivingEntity): Map<String, Double> {
        return AttributeCalculator.getValues(entity)
    }

    override fun getModifiers(entity: LivingEntity, attributeId: String): List<AttributeModifier> {
        val all = mutableListOf<AttributeModifier>()
        for (provider in AttributeProviderRegistry.getAll()) {
            all.addAll(provider.provide(entity).filter { it.attributeId == attributeId })
        }
        return all
    }

    override fun addModifier(entity: LivingEntity, modifier: AttributeModifier) {
        // 通过 ScriptProvider 添加动态修改器
        markDirty(entity)
    }

    override fun removeModifier(entity: LivingEntity, source: String) {
        markDirty(entity)
    }

    override fun markDirty(entity: LivingEntity) {
        AttributeCalculator.markDirty(entity)
    }

    override fun recalculate(entity: LivingEntity) {
        AttributeCalculator.recalculate(entity)
    }

    override fun addListener(listener: AttributeListener) {
        AttributeListenerRegistry.add(listener)
    }

    override fun removeListener(listener: AttributeListener) {
        AttributeListenerRegistry.remove(listener)
    }
}
