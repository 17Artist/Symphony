package priv.seventeen.artist.symphony.api.attribute

import org.bukkit.entity.LivingEntity

interface IAttributeProvider {
    val id: String
    val priority: Int

    /** 该 Provider 是否对此实体有意义；返回 false 时计算引擎跳过 [provide] 调用。 */
    fun appliesTo(entity: LivingEntity): Boolean = true

    /**
     * 是否线程安全 / 可在异步线程跑。
     * - true：仅依赖 PlayerDataManager 等纯数据
     * - false（默认）：会触碰 Bukkit API（equipment / location / inventory 等）
     *
     * 异步开关启用时，true 的 Provider 进入异步采集池；false 始终主线程。
     */
    val isAsync: Boolean get() = false

    fun provide(entity: LivingEntity): List<AttributeModifier>
    fun shouldUpdate(entity: LivingEntity, event: Any?): Boolean = true
}
