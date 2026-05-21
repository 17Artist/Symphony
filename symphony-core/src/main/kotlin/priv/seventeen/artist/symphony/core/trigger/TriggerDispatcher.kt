package priv.seventeen.artist.symphony.core.trigger

import org.bukkit.Bukkit
import org.bukkit.entity.LivingEntity
import org.bukkit.entity.Player
import priv.seventeen.artist.symphony.api.affix.AffixInstance
import priv.seventeen.artist.symphony.api.event.AffixTriggerEvent
import priv.seventeen.artist.symphony.api.event.TriggerDispatchEvent
import priv.seventeen.artist.symphony.api.trigger.TriggerType
import priv.seventeen.artist.symphony.core.affix.AffixManagerImpl
import priv.seventeen.artist.symphony.core.affix.AffixProcessor
import priv.seventeen.artist.symphony.core.growth.rune.RuneRegistry
import priv.seventeen.artist.symphony.core.growth.set.SetManager
import priv.seventeen.artist.symphony.core.storage.PlayerDataManager
import java.util.UUID

/**
 * 触发器分发器 — 监听 Bukkit 事件并分发到词条系统。
 */
object TriggerDispatcher {

    fun dispatch(
        triggerType: TriggerType,
        entity: LivingEntity,
        contextBuilder: TriggerContext.Builder.() -> Unit
    ) {
        val context = TriggerContext.Builder(triggerType, entity)
            .apply(contextBuilder)
            .build()

        // 全局派发前事件（允许其他插件取消整批）
        val dispatchEvent = TriggerDispatchEvent(triggerType, entity, emptyMap())
        Bukkit.getPluginManager().callEvent(dispatchEvent)
        if (dispatchEvent.isCancelled) return

        // 获取实体所有装备上的词条
        val affixes = AffixManagerImpl.collectEntityAffixes(entity)

        for (affix in affixes) {
            val definition = AffixManagerImpl.getDefinition(affix.affixId) ?: continue
            val triggers = definition.triggersByType[triggerType.id] ?: continue

            for (trigger in triggers) {
                // 评估条件
                if (!ConditionEvaluator.evaluate(trigger.conditions, context, affix.parameters)) continue

                // 发布事件（允许取消）
                val event = AffixTriggerEvent(entity, affix, triggerType, context)
                Bukkit.getPluginManager().callEvent(event)
                if (event.isCancelled) continue

                // 执行动作
                AffixProcessor.executeActions(trigger.actions, context, affix)
            }
        }

        dispatchRuneTriggers(triggerType, entity, context)
        dispatchSetTriggers(triggerType, entity, context)
    }

    private fun dispatchRuneTriggers(
        triggerType: TriggerType,
        entity: LivingEntity,
        context: TriggerContext
    ) {
        if (entity !is Player) return
        val data = PlayerDataManager.getData(entity.uniqueId) ?: return
        for ((runeId, runeData) in data.persistent.runes) {
            if (!runeData.active) continue
            val def = RuneRegistry.get(runeId) ?: continue
            val triggers = def.triggers.filter { it["type"]?.toString() == triggerType.id }
            if (triggers.isEmpty()) continue
            val params = mapOf<String, Any>("level" to runeData.level)
            val fakeAffix = AffixInstance(UUID.randomUUID(), "rune:$runeId", runeData.level, params)
            for (trigger in triggers) {
                @Suppress("UNCHECKED_CAST")
                val conditions = (trigger["conditions"] as? List<Map<String, Any>>) ?: emptyList()
                if (!ConditionEvaluator.evaluate(conditions, context, params)) continue
                @Suppress("UNCHECKED_CAST")
                val actions = (trigger["actions"] as? List<Map<String, Any>>) ?: continue
                AffixProcessor.executeActions(actions, context, fakeAffix)
            }
        }
    }

    // ── 套装触发器分发 ──

    var setManager: SetManager? = null

    private fun dispatchSetTriggers(
        triggerType: TriggerType,
        entity: LivingEntity,
        context: TriggerContext
    ) {
        if (entity !is Player) return
        val sm = setManager ?: return
        val activeSets = sm.detectSets(entity)
        for ((setId, pieceCount) in activeSets) {
            val bonuses = sm.getActiveBonuses(setId, pieceCount)
            for (bonus in bonuses) {
                val triggers = bonus.triggers.filter {
                    it["type"]?.toString()?.uppercase() == triggerType.id
                }
                for (trigger in triggers) {
                    @Suppress("UNCHECKED_CAST")
                    val conditions = (trigger["conditions"] as? List<Map<String, Any>>) ?: emptyList()
                    val params = mapOf<String, Any>("set_id" to setId, "pieces" to pieceCount)
                    if (!ConditionEvaluator.evaluate(conditions, context, params)) continue
                    @Suppress("UNCHECKED_CAST")
                    val actions = (trigger["actions"] as? List<Map<String, Any>>) ?: continue
                    val fakeAffix = AffixInstance(UUID.randomUUID(), "set:$setId", pieceCount, params)
                    AffixProcessor.executeActions(actions, context, fakeAffix)
                }
            }
        }
    }
}
