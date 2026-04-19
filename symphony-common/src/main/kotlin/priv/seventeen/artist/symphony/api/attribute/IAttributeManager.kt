package priv.seventeen.artist.symphony.api.attribute

import org.bukkit.entity.LivingEntity

interface IAttributeManager {
    fun registerAttribute(attribute: IAttribute)
    fun unregisterAttribute(id: String)
    fun getAttribute(id: String): IAttribute?
    fun getAllAttributes(): Collection<IAttribute>
    fun getAttributesByCategory(category: String): Collection<IAttribute>

    fun registerProvider(provider: IAttributeProvider)
    fun unregisterProvider(id: String)
    fun getProviders(): List<IAttributeProvider>

    fun getValue(entity: LivingEntity, attributeId: String): Double
    fun getValues(entity: LivingEntity): Map<String, Double>
    fun getModifiers(entity: LivingEntity, attributeId: String): List<AttributeModifier>

    fun addModifier(entity: LivingEntity, modifier: AttributeModifier)
    fun removeModifier(entity: LivingEntity, source: String)

    fun markDirty(entity: LivingEntity)
    fun recalculate(entity: LivingEntity)

    /** 编程式订阅属性变更（非 Bukkit 事件，按 id 过滤，低开销）。 */
    fun addListener(listener: AttributeListener)
    fun removeListener(listener: AttributeListener)
}
