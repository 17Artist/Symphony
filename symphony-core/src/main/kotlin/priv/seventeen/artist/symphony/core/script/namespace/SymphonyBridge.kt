package priv.seventeen.artist.symphony.core.script.namespace

import org.bukkit.Bukkit
import org.bukkit.Location
import org.bukkit.Particle
import org.bukkit.Sound
import org.bukkit.attribute.Attribute
import org.bukkit.entity.Entity
import org.bukkit.entity.LivingEntity
import priv.seventeen.artist.symphony.api.event.SymphonyHealEvent
import priv.seventeen.artist.symphony.core.attribute.AttributeCalculator
import priv.seventeen.artist.symphony.core.attribute.AttributeRegistry
import java.util.UUID

/**
 * Aria 脚本桥接类。
 * Aria 通过 use('...SymphonyBridge') 加载此类，调用其公开方法。
 *
 * 属性声明改用 @attribute 注解式脚本；运行时查询/实体操作/特效保留为桥接方法。
 */
@Suppress("unused")
class SymphonyBridge {

    // symphony.attribute.*  (只读查询 + 运行时读取)
    fun attributeUnregister(id: Any?) {
        AttributeRegistry.unregister(id?.toString() ?: return)
    }

    fun attributeList(): List<String> {
        return AttributeRegistry.ids().toList()
    }

    fun attributeExists(id: Any?): Boolean {
        return AttributeRegistry.exists(id?.toString() ?: return false)
    }

    fun attributeGetInfo(id: Any?): Map<String, Any>? {
        val attr = AttributeRegistry.get(id?.toString() ?: return null) ?: return null
        return mapOf(
            "id" to attr.id,
            "display_name" to attr.displayName,
            "category" to attr.category,
            "default_value" to attr.defaultValue,
            "format" to attr.format,
            "readonly" to attr.readonly
        )
    }

    fun attributeListByCategory(category: Any?): List<String> {
        return AttributeRegistry.getByCategory(category?.toString() ?: return emptyList()).map { it.id }
    }

    fun attributeListByTag(tag: Any?): List<String> {
        return AttributeRegistry.getByTag(tag?.toString() ?: return emptyList()).map { it.id }
    }

    fun attributeGet(entity: Any?, attrId: Any?): Double {
        val id = attrId?.toString() ?: return 0.0
        // derive 上下文中的递归请求 → 走拓扑求解
        AttributeCalculator.lookupForDerive(id)?.let { return it }
        val e = unwrapEntity(entity) as? LivingEntity
            ?: return AttributeRegistry.get(id)?.defaultValue ?: 0.0
        return AttributeCalculator.getValue(e, id)
    }

    fun attributeGetRaw(holder: Any?, attrId: Any?): Double {
        val id = attrId?.toString() ?: return 0.0
        AttributeCalculator.lookupForDerive(id)?.let { return it }
        val e = unwrapEntity(holder) as? LivingEntity
            ?: return AttributeRegistry.get(id)?.defaultValue ?: 0.0
        return AttributeCalculator.getValue(e, id)
    }

    // symphony.entity.*
    fun entityDamage(target: Any?, amount: Any?, damageType: Any?) {
        val e = unwrapEntity(target) as? LivingEntity ?: return
        val dmg = (amount as? Number)?.toDouble() ?: return
        // damageType 暂作标注用途（事件路径区分），底层仍走 Bukkit 伤害
        e.damage(dmg)
    }

    fun entityHeal(target: Any?, amount: Any?) {
        val e = unwrapEntity(target) as? LivingEntity ?: return
        val heal = (amount as? Number)?.toDouble() ?: return
        val event = SymphonyHealEvent(e, null, heal, "script")
        Bukkit.getPluginManager().callEvent(event)
        if (event.isCancelled) return
        val maxHp = e.getAttribute(Attribute.GENERIC_MAX_HEALTH)?.value ?: e.health
        e.health = (e.health + event.amount).coerceIn(0.0, maxHp)
    }

    fun entityGetHealth(entity: Any?): Double {
        return (unwrapEntity(entity) as? LivingEntity)?.health ?: 0.0
    }

    fun entityGetMaxHealth(entity: Any?): Double {
        val e = unwrapEntity(entity) as? LivingEntity ?: return 0.0
        return e.getAttribute(Attribute.GENERIC_MAX_HEALTH)?.value ?: 0.0
    }

    // symphony.effect.*
    fun effectParticle(location: Any?, particleName: Any?, count: Any?) {
        val loc = unwrapLocation(location) ?: return
        val world = loc.world ?: return
        val name = particleName?.toString()?.uppercase() ?: return
        val n = (count as? Number)?.toInt() ?: 1
        val particle = runCatching { Particle.valueOf(name) }.getOrNull() ?: return
        world.spawnParticle(particle, loc, n)
    }

    fun effectSound(location: Any?, soundName: Any?, volume: Any?, pitch: Any?) {
        val loc = unwrapLocation(location) ?: return
        val world = loc.world ?: return
        val name = soundName?.toString()?.uppercase() ?: return
        val v = (volume as? Number)?.toFloat() ?: 1f
        val p = (pitch as? Number)?.toFloat() ?: 1f
        val sound = runCatching { Sound.valueOf(name) }.getOrNull() ?: return
        world.playSound(loc, sound, v, p)
    }

    // 辅助：脚本对象 → Bukkit 类型
    private fun unwrapEntity(v: Any?): Entity? = when (v) {
        null -> null
        is Entity -> v
        is UUID -> Bukkit.getEntity(v)
        is String -> runCatching { Bukkit.getEntity(UUID.fromString(v)) }.getOrNull()
        else -> null
    }

    private fun unwrapLocation(v: Any?): Location? = when (v) {
        null -> null
        is Location -> v
        is Entity -> v.location
        else -> null
    }
}
