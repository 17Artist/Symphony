package priv.seventeen.artist.symphony.plugin

import org.bukkit.entity.LivingEntity
import org.bukkit.entity.Player
import priv.seventeen.artist.symphony.api.IAttributeExplain
import priv.seventeen.artist.symphony.api.IEntityMetadata
import priv.seventeen.artist.symphony.api.ISymphonyExtensions
import priv.seventeen.artist.symphony.api.SymphonyAPI
import priv.seventeen.artist.symphony.api.affix.IAffixManager
import priv.seventeen.artist.symphony.api.attribute.IAttributeManager
import priv.seventeen.artist.symphony.api.attribute.Operation
import priv.seventeen.artist.symphony.api.entity.IPlayerData
import priv.seventeen.artist.symphony.api.event.BuffExpireEvent
import priv.seventeen.artist.symphony.api.growth.IGrowthManager
import priv.seventeen.artist.symphony.api.skill.ISkillProviderManager
import priv.seventeen.artist.symphony.api.trigger.ITriggerManager
import priv.seventeen.artist.symphony.core.advanced.element.ReactionDefinition
import priv.seventeen.artist.symphony.core.advanced.element.ReactionSystem
import priv.seventeen.artist.symphony.core.advanced.element.ReactionType
import priv.seventeen.artist.symphony.core.affix.AffixManagerImpl
import priv.seventeen.artist.symphony.core.attribute.AttributeCache
import priv.seventeen.artist.symphony.core.attribute.AttributeCalculator
import priv.seventeen.artist.symphony.core.attribute.AttributeManagerImpl
import priv.seventeen.artist.symphony.core.data.ActiveBuff
import priv.seventeen.artist.symphony.core.data.BuffOps
import priv.seventeen.artist.symphony.core.data.SymphonyPlayerData
import priv.seventeen.artist.symphony.core.runtime.EntityMetadataImpl
import priv.seventeen.artist.symphony.core.storage.PlayerDataManager

class SymphonyAPIImpl : SymphonyAPI {
    private val attributeManager = AttributeManagerImpl()
    val affixManagerInstance = AffixManagerImpl()
    private val affixManager: IAffixManager = affixManagerInstance

    override fun getAttributeManager(): IAttributeManager = attributeManager
    override fun getAffixManager(): IAffixManager = affixManagerInstance
    override fun getTriggerManager(): ITriggerManager = SymphonyPlugin.triggerManager
    override fun getSkillProviderManager(): ISkillProviderManager = SymphonyPlugin.skillProviderManager
    override fun getGrowthManager(): IGrowthManager = SymphonyPlugin.growthManager

    override fun getPlayerData(player: Player): IPlayerData {
        val data = PlayerDataManager.getData(player.uniqueId)
            ?: PlayerDataManager.getOrCreate(player.uniqueId)
        return PlayerDataProxy(data)
    }

    override fun getAttribute(entity: LivingEntity, attributeId: String): Double =
        AttributeCalculator.getValue(entity, attributeId)

    override fun setAttribute(entity: LivingEntity, attributeId: String, operation: Operation, value: Double, source: String) {
        val data = PlayerDataManager.getData(entity.uniqueId)
        if (data != null) {
            // 玩家：走 buff 体系，旧同 source 同 attribute 视为替换
            BuffOps.removeWhere(entity, data.runtime, BuffExpireEvent.ExpireReason.REPLACED) {
                it.source == source && it.attribute == attributeId
            }
            data.runtime.activeBuffs.add(ActiveBuff(
                id = "api:$source:$attributeId",
                attribute = attributeId,
                operation = operation,
                value = value,
                expireTime = -1L,
                source = source
            ))
            AttributeCalculator.markDirty(entity)
            return
        }
        // 非玩家（如 MM 怪物）：走 MythicMobDataStore 的 modifier 列表
        val existing = priv.seventeen.artist.symphony.core.skill.builtin.MythicMobDataStore.get(entity.uniqueId)
        if (existing != null) {
            val newModifier = priv.seventeen.artist.symphony.api.attribute.AttributeModifier(
                attributeId, operation, value, source
            )
            // 同 source+attribute 视为替换
            val newMods = existing.modifiers.filterNot {
                it.source == source && it.attributeId == attributeId
            } + newModifier
            priv.seventeen.artist.symphony.core.skill.builtin.MythicMobDataStore.put(
                entity.uniqueId,
                existing.copy(modifiers = newMods)
            )
            AttributeCalculator.markDirty(entity)
        }
    }

    override fun removeAttribute(entity: LivingEntity, source: String) {
        val data = PlayerDataManager.getData(entity.uniqueId)
        if (data != null) {
            BuffOps.removeWhere(entity, data.runtime, BuffExpireEvent.ExpireReason.MANUAL_REMOVE) {
                it.source == source
            }
            AttributeCalculator.markDirty(entity)
            return
        }
        // 非玩家：从 MM data store 移除指定 source 的 modifier
        val existing = priv.seventeen.artist.symphony.core.skill.builtin.MythicMobDataStore.get(entity.uniqueId)
        if (existing != null) {
            val newMods = existing.modifiers.filterNot { it.source == source }
            if (newMods.size != existing.modifiers.size) {
                priv.seventeen.artist.symphony.core.skill.builtin.MythicMobDataStore.put(
                    entity.uniqueId,
                    existing.copy(modifiers = newMods)
                )
                AttributeCalculator.markDirty(entity)
            }
        }
    }

    override fun recalculate(entity: LivingEntity) {
        AttributeCalculator.recalculate(entity)
    }

    override fun snapshot(entity: LivingEntity): Map<String, Double> {
        AttributeCalculator.ensureCalculated(entity)
        return AttributeCache.getAll(entity.uniqueId).toMap()
    }

    override fun registerReaction(
        id: String,
        displayName: String,
        trigger: String,
        aura: String,
        reactionType: String,
        multiplier: Double,
        gaugeConsume: Double,
        particle: String?,
        sound: String?,
        radius: Double,
        ticks: Int,
        interval: Long
    ) {
        val type = runCatching { ReactionType.valueOf(reactionType.uppercase()) }.getOrElse { ReactionType.AMPLIFY }
        ReactionSystem.register(ReactionDefinition(
            id = id, displayName = displayName, trigger = trigger, aura = aura,
            type = type, multiplier = multiplier, gaugeConsume = gaugeConsume,
            particle = particle, sound = sound, radius = radius, ticks = ticks, interval = interval
        ))
    }

    override fun getMetadata(): IEntityMetadata = EntityMetadataImpl

    override fun explain(entity: LivingEntity, attributeId: String): IAttributeExplain? =
        AttributeCalculator.explain(entity, attributeId)

    override fun getExtensions(): ISymphonyExtensions = SymphonyExtensionsImpl
}

private class PlayerDataProxy(private val data: SymphonyPlayerData) : IPlayerData {
    override val uuid = data.uuid
    override var level: Int
        get() = data.persistent.level
        set(value) { data.persistent.level = value; data.dirty = true }
    override var exp: Long
        get() = data.persistent.exp
        set(value) { data.persistent.exp = value; data.dirty = true }
    override var freePoints: Int
        get() = data.persistent.freePoints
        set(value) { data.persistent.freePoints = value; data.dirty = true }
    override fun isDirty() = data.dirty
    override fun markDirty() { data.dirty = true }
}
