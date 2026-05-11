package priv.seventeen.artist.symphony.core.attribute

import org.bukkit.entity.LivingEntity
import priv.seventeen.artist.symphony.nms.NMSAdapterFactory
import kotlin.math.abs

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

            val modKey = "symphony:${attr.id}"
            val symphonyValue = AttributeCache.get(entity.uniqueId, attr.id)

            if (symphonyValue == null) {
                // 缓存中无值 → 移除旧 modifier，不干预原版
                bridge.removeModifier(entity, binding, modKey)
                continue
            }

            // 先移除 Symphony 自身的 modifier，获取"不含 Symphony 影响"的原版最终值
            bridge.removeModifier(entity, binding, modKey)
            val vanillaWithoutSymphony = bridge.getFinalValue(entity, binding)
            val diff = symphonyValue - vanillaWithoutSymphony

            if (abs(diff) > 0.0001) {
                bridge.setModifier(entity, binding, modKey, diff, 0)
            }
        }
    }

    fun removeAll(entity: LivingEntity) {
        if (!NMSAdapterFactory.isInitialized()) return
        NMSAdapterFactory.get().getAttributeBridge().removeAllSymphonyModifiers(entity)
    }
}
