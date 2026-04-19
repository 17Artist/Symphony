package priv.seventeen.artist.symphony.api

import org.bukkit.entity.LivingEntity
import org.bukkit.entity.Player
import priv.seventeen.artist.symphony.api.attribute.IAttributeManager
import priv.seventeen.artist.symphony.api.attribute.Operation
import priv.seventeen.artist.symphony.api.affix.IAffixManager
import priv.seventeen.artist.symphony.api.trigger.ITriggerManager
import priv.seventeen.artist.symphony.api.skill.ISkillProviderManager
import priv.seventeen.artist.symphony.api.growth.IGrowthManager
import priv.seventeen.artist.symphony.api.entity.IPlayerData

interface SymphonyAPI {
    fun getAttributeManager(): IAttributeManager
    fun getAffixManager(): IAffixManager
    fun getTriggerManager(): ITriggerManager
    fun getSkillProviderManager(): ISkillProviderManager
    fun getGrowthManager(): IGrowthManager
    fun getPlayerData(player: Player): IPlayerData

    fun getAttribute(entity: LivingEntity, attributeId: String): Double
    fun setAttribute(entity: LivingEntity, attributeId: String, operation: Operation, value: Double, source: String)
    fun removeAttribute(entity: LivingEntity, source: String)
    fun recalculate(entity: LivingEntity)

    /**
     * 对当前计算结果做一份不可变快照。
     * 调用前会确保属性已重算（等价于先 [recalculate] 再取缓存）。
     */
    fun snapshot(entity: LivingEntity): Map<String, Double>

    /**
     * 比较两次快照，返回有差异的属性 id → (old, new)。
     * old 或 new 缺失某 id 时视为 0.0。
     */
    fun diff(old: Map<String, Double>, new: Map<String, Double>): Map<String, Pair<Double, Double>> {
        val ids = old.keys + new.keys
        val result = linkedMapOf<String, Pair<Double, Double>>()
        for (id in ids) {
            val o = old[id] ?: 0.0
            val n = new[id] ?: 0.0
            if (o != n) result[id] = o to n
        }
        return result
    }

    /**
     * 注册自定义元素反应。参数 [reactionType] 支持：AMPLIFY / DEBUFF / DOT_AOE / CONTROL / SPREAD。
     * 调用者不需要引用 core 模块的数据类。
     */
    fun registerReaction(
        id: String,
        displayName: String,
        trigger: String,
        aura: String,
        reactionType: String,
        multiplier: Double = 1.0,
        gaugeConsume: Double = 0.5,
        particle: String? = null,
        sound: String? = null,
        radius: Double = 0.0,
        ticks: Int = 0,
        interval: Long = 1000
    )

    /** 实体跨插件元数据存储（强引用，实体下线时自动回收）。 */
    fun getMetadata(): IEntityMetadata

    /** 对外暴露属性流水线 explain（用途：UI / 聊天调试 / 外部诊断工具）。 */
    fun explain(entity: LivingEntity, attributeId: String): IAttributeExplain?

    /** 高级子系统扩展入口（反应 / 状态层 / 环境 / 共鸣 / 天赋 / 交互 / 套装）。 */
    fun getExtensions(): ISymphonyExtensions

    companion object {
        @Volatile
        private var instance: SymphonyAPI? = null

        fun getInstance(): SymphonyAPI = instance ?: error("Symphony not loaded")

        fun setInstance(api: SymphonyAPI) {
            instance = api
        }
    }
}
