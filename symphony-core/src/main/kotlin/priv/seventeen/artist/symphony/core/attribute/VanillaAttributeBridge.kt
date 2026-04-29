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

        // 收集所有 Provider 实际贡献过的属性 ID
        val contributed = HashSet<String>()
        for (provider in AttributeProviderRegistry.getAll()) {
            if (!provider.appliesTo(entity)) continue
            try {
                for (m in provider.provide(entity)) {
                    contributed += m.attributeId
                }
            } catch (_: Exception) {}
        }

        for (attr in AttributeRegistry.getAll()) {
            val binding = attr.vanillaBinding ?: continue
            if (!bridge.hasAttribute(entity, binding)) continue

            val modKey = "symphony:${attr.id}"

            // 没有任何 Provider 贡献过该属性 → 不干预原版，移除旧 modifier
            if (attr.id !in contributed) {
                bridge.removeModifier(entity, binding, modKey)
                continue
            }

            val symphonyValue = AttributeCache.get(entity.uniqueId, attr.id) ?: continue

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
