package priv.seventeen.artist.symphony.plugin

import org.bukkit.Bukkit
import org.bukkit.attribute.Attribute
import org.bukkit.entity.Player
import org.bukkit.scheduler.BukkitRunnable
import priv.seventeen.artist.symphony.core.attribute.AttributeCache
import priv.seventeen.artist.symphony.core.attribute.AttributeCalculator
import priv.seventeen.artist.symphony.core.attribute.AsyncRecalcScheduler
import priv.seventeen.artist.symphony.core.attribute.VanillaAttributeBridge
import priv.seventeen.artist.symphony.core.advanced.element.ElementAuraSystem
import priv.seventeen.artist.symphony.core.advanced.environment.EnvironmentSystem
import priv.seventeen.artist.symphony.core.advanced.interaction.InteractionNetwork
import priv.seventeen.artist.symphony.core.advanced.resonance.ResonanceManager
import priv.seventeen.artist.symphony.core.advanced.status.StatusLayerSystem
import priv.seventeen.artist.symphony.core.advanced.talent.TalentManager
import priv.seventeen.artist.symphony.core.data.EntityRuntimeCache
import priv.seventeen.artist.symphony.core.storage.PlayerDataManager
import priv.seventeen.artist.symphony.core.trigger.TriggerDispatcher
import priv.seventeen.artist.symphony.api.trigger.TriggerType
import priv.seventeen.artist.symphony.api.event.BuffExpireEvent
import priv.seventeen.artist.symphony.plugin.listener.EpicFightAnimationListener

class RuntimeTickTask : BukkitRunnable() {
    private var tickCount = 0L

    override fun run() {
        tickCount++
        val now = System.currentTimeMillis()

        for (player in Bukkit.getOnlinePlayers()) {
            if (tickCount % 20 == 0L) {
                EpicFightAnimationListener.tickPlayer(player)
            }
            val data = PlayerDataManager.getData(player.uniqueId) ?: continue
            var attributeDirty = false

            // 每 tick: 战斗状态检查
            if (data.runtime.inCombat) {
                val timeout = SymphonyPlugin.config.combatTimeout
                if (now - data.runtime.lastCombatActionTime > timeout) {
                    data.runtime.inCombat = false
                    TriggerDispatcher.dispatch(TriggerType.ON_LEAVE_COMBAT, player) {
                        set("combatDuration", now - data.runtime.combatStartTime)
                    }
                }
            }

            // 每 tick: 连击超时
            val comboTimeout = SymphonyPlugin.config.comboTimeout
            if (data.runtime.comboCount > 0 && now - data.runtime.lastComboTime > comboTimeout) {
                data.runtime.comboCount = 0
            }

            // 每 20 tick (1秒)
            if (tickCount % 20 == 0L) {
                // Buff 过期 — iterator 原地删除避免临时 List
                val buffIter = data.runtime.activeBuffs.iterator()
                while (buffIter.hasNext()) {
                    val buff = buffIter.next()
                    if (buff.isExpired()) {
                        buffIter.remove()
                        Bukkit.getPluginManager().callEvent(
                            BuffExpireEvent(player, buff.id, buff.attribute, BuffExpireEvent.ExpireReason.TIMEOUT)
                        )
                        attributeDirty = true
                    }
                }

                // 临时词条过期
                val affixIter = data.runtime.tempAffixes.iterator()
                while (affixIter.hasNext()) {
                    if (affixIter.next().isExpired()) {
                        affixIter.remove()
                        attributeDirty = true
                    }
                }

                // 护盾过期
                data.runtime.activeShields.removeIf { it.isExpired() || it.amount <= 0 }

                // 法力恢复
                val maxMana = AttributeCache.get(player.uniqueId, "max_mana") ?: 100.0
                val manaRegen = AttributeCache.get(player.uniqueId, "mana_regen") ?: 0.0
                val manaBeforeRegen = data.runtime.currentMana
                data.runtime.currentMana = minOf(maxMana, data.runtime.currentMana + manaRegen)

                // ON_MANA_FULL — 法力恢复到满时触发
                if (manaBeforeRegen < maxMana && data.runtime.currentMana >= maxMana) {
                    TriggerDispatcher.dispatch(TriggerType.ON_MANA_FULL, player) {
                        set("maxMana", maxMana)
                    }
                }

                // 生命恢复
                val healthRegen = AttributeCache.get(player.uniqueId, "health_regen") ?: 0.0
                if (healthRegen > 0 && player.health < (player.getAttribute(Attribute.GENERIC_MAX_HEALTH)?.value ?: 20.0)) {
                    val maxHp = player.getAttribute(Attribute.GENERIC_MAX_HEALTH)?.value ?: 20.0
                    player.health = minOf(player.health + healthRegen, maxHp)
                }

                // 状态层 tick 在玩家循环外执行
            }

            // 每 40 tick (2秒): 高级系统检查
            if (tickCount % 40 == 0L) {
                // 词条共鸣检查
                if (SymphonyPlugin.config.resonanceEnabled) {
                    ResonanceManager.checkResonances(player)
                }

                // 天赋门检查
                if (SymphonyPlugin.config.talentEnabled) {
                    TalentManager.checkTalents(player)
                }

                // 环境系统刷新 — 检查环境变化并更新缓存
                if (SymphonyPlugin.config.environmentEnabled) {
                    if (EnvironmentSystem.checkAndUpdateEnvironment(player)) {
                        AttributeCache.markDirty(player.uniqueId)
                    }
                }
            }

            if (attributeDirty) {
                AttributeCache.markDirty(player.uniqueId)
            }

            // 属性重算 + 交互网络 + 原版同步（Epic Fight 动画中延迟，避免换世界/Buff 等触发时卡动作）
            if (AttributeCache.isDirty(player.uniqueId) ||
                EpicFightAnimationListener.hasPendingRecalc(player)
            ) {
                val afterRecalc: () -> Unit = {
                    if (SymphonyPlugin.config.interactionEnabled) {
                        InteractionNetwork.process(player)
                    }
                    EpicFightAnimationListener.syncVanillaWhenSafe(player)
                }
                if (SymphonyPlugin.config.asyncRecalc && !EpicFightAnimationListener.shouldDeferRecalc(player)) {
                    AsyncRecalcScheduler.recalculateAsync(player, afterRecalc)
                } else {
                    EpicFightAnimationListener.scheduleSafeRecalc(player, afterRecalc)
                }
            }
        }

        // 每 20 tick: 状态层 tick（在玩家循环外，避免重复调用）
        if (tickCount % 20 == 0L && SymphonyPlugin.config.statusEnabled) {
            StatusLayerSystem.tick()
        }

        // 每 200 tick: 清理非玩家实体缓存
        if (tickCount % 200 == 0L) {
            EntityRuntimeCache.cleanup()
        }

        // 每 100 tick: 清理已离线的玩家动画状态
        if (tickCount % 100 == 0L) {
            EpicFightAnimationListener.cleanupOfflinePlayers()
        }
    }
}
