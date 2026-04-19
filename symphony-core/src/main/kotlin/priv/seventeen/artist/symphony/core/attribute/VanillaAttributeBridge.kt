package priv.seventeen.artist.symphony.core.attribute

import org.bukkit.entity.LivingEntity
import priv.seventeen.artist.symphony.nms.NMSAdapterFactory

/**
 * 原版属性同步桥接。
 * 将 Symphony 属性同步到 Minecraft 原版属性系统。
 */
object VanillaAttributeBridge {

    fun syncToVanilla(entity: LivingEntity) {
        if (!NMSAdapterFactory.isInitialized()) return
        val bridge = NMSAdapterFactory.get().getAttributeBridge()

        for (attr in AttributeRegistry.getAll()) {
            val binding = attr.vanillaBinding ?: continue
            if (!bridge.hasAttribute(entity, binding)) continue

            val symphonyValue = AttributeCache.get(entity.uniqueId, attr.id) ?: continue
            val vanillaBase = bridge.getBaseValue(entity, binding)
            val diff = symphonyValue - vanillaBase

            bridge.setModifier(entity, binding, "symphony:${attr.id}", diff, 0)
        }
    }

    fun removeAll(entity: LivingEntity) {
        if (!NMSAdapterFactory.isInitialized()) return
        NMSAdapterFactory.get().getAttributeBridge().removeAllSymphonyModifiers(entity)
    }
}
