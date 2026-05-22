package priv.seventeen.artist.symphony.core.advanced.status

import org.bukkit.Bukkit
import org.bukkit.Particle
import org.bukkit.entity.LivingEntity
import org.bukkit.entity.Player
import priv.seventeen.artist.symphony.api.attribute.AttributeModifier
import priv.seventeen.artist.symphony.api.attribute.Operation
import priv.seventeen.artist.symphony.api.event.StatusLayerChangeEvent
import priv.seventeen.artist.symphony.core.attribute.AttributeCache
import priv.seventeen.artist.symphony.core.attribute.AttributeCalculator
import priv.seventeen.artist.symphony.core.data.EntityRuntimeCache
import priv.seventeen.artist.symphony.core.data.StatusStackData
import priv.seventeen.artist.symphony.core.storage.PlayerDataManager
import priv.seventeen.artist.symphony.core.script.AriaCallbackManager
import priv.seventeen.artist.symphony.core.trigger.CooldownManager
import java.util.UUID
import java.util.concurrent.ConcurrentHashMap

/**
 * 状态层系统 — 持续攻击叠加层数，满层触发爆发效果。
 */
object StatusLayerSystem {
    private val definitions = ConcurrentHashMap<String, StatusDefinition>()
    var maxStatusTypes = 5

    fun register(status: StatusDefinition) {
        definitions[status.id] = status
    }

    fun unregister(id: String): Boolean = definitions.remove(id) != null

    fun getAll(): Collection<StatusDefinition> = definitions.values

    fun get(id: String): StatusDefinition? = definitions[id]

    fun clear() {
        definitions.clear()
    }

    fun addStacks(entity: LivingEntity, statusId: String, stacks: Int, appliedBy: UUID? = null) {
        val def = definitions[statusId] ?: return
        val statusMap = getStatusMap(entity)

        // 免疫检查
        val immuneKey = "status_immune:$statusId"
        if (CooldownManager.isOnCooldown(entity.uniqueId, immuneKey)) return

        // 最大状态类型检查
        if (!statusMap.containsKey(statusId) && statusMap.size >= maxStatusTypes) return

        val now = System.currentTimeMillis()
        val existing = statusMap[statusId]
        val oldStacks = existing?.stacks ?: 0

        if (existing != null) {
            val newStacks = minOf(existing.stacks + stacks, def.maxStacks)
            when (def.decayMode) {
                DecayMode.INDIVIDUAL -> {
                    repeat(stacks) { existing.stackTimestamps.add(now + def.stackDuration) }
                }
                DecayMode.REFRESH -> {
                    existing.lastRefreshTime = now
                    existing.stackTimestamps.clear()
                    repeat(newStacks) { existing.stackTimestamps.add(now + def.stackDuration) }
                }
            }
            existing.stacks = newStacks

            // 满层爆发
            if (newStacks >= def.maxStacks) {
                onMaxStacks(entity, def, appliedBy)
            }
        } else {
            val timestamps = mutableListOf<Long>()
            repeat(stacks) { timestamps.add(now + def.stackDuration) }
            statusMap[statusId] = StatusStackData(statusId, stacks, timestamps, now, appliedBy)
        }

        val finalStacks = statusMap[statusId]?.stacks ?: 0
        if (finalStacks != oldStacks) {
            Bukkit.getPluginManager().callEvent(
                StatusLayerChangeEvent(
                    entity, statusId, oldStacks, finalStacks,
                    StatusLayerChangeEvent.Reason.STACK
                )
            )
        }

        // 标记属性重算（状态层可能影响属性）
        AttributeCache.markDirty(entity.uniqueId)
    }

    fun getStacks(entity: LivingEntity, statusId: String): Int {
        return getStatusMap(entity)[statusId]?.stacks ?: 0
    }

    fun clearStacks(entity: LivingEntity, statusId: String) {
        getStatusMap(entity).remove(statusId)
        AttributeCache.markDirty(entity.uniqueId)
    }

    fun setImmune(entity: LivingEntity, statusId: String, duration: Long) {
        CooldownManager.setCooldown(entity.uniqueId, "status_immune:$statusId", duration)
    }

    fun getAllStatus(entity: LivingEntity): Map<String, StatusStackData> {
        return getStatusMap(entity).toMap()
    }

    /**
     * 获取状态层提供的属性修改器。
     */
    fun getStatusModifiers(entity: LivingEntity): List<AttributeModifier> {
        val modifiers = mutableListOf<AttributeModifier>()
        for ((statusId, stackData) in getStatusMap(entity)) {
            val def = definitions[statusId] ?: continue
            for ((attrId, attr) in def.perStackAttributes) {
                val op = if (attr.operation.uppercase() == "PERCENT") Operation.PERCENT else Operation.FLAT
                modifiers.add(AttributeModifier(attrId, op, attr.value * stackData.stacks, "status:$statusId"))
            }
        }
        return modifiers
    }

    /**
     * 每秒 tick — 处理状态层衰减和持续伤害。
     */
    fun tick() {
        val now = System.currentTimeMillis()

        // tick 在线玩家
        for (player in Bukkit.getOnlinePlayers()) {
            tickEntity(player, now)
        }

        // tick 非玩家实体（怪物等）— 从 EntityRuntimeCache 中找有状态层的实体
        for (entry in EntityRuntimeCache.entries()) {
            if (entry.value.statusStacks.isEmpty()) continue
            val entity = Bukkit.getEntity(entry.key) as? LivingEntity ?: continue
            if (entity is org.bukkit.entity.Player) continue // 玩家已在上面处理
            tickEntity(entity, now)
        }
    }

    private fun tickEntity(entity: LivingEntity, now: Long) {
        val statusMap = getStatusMap(entity)
        val toRemove = mutableListOf<String>()

        for ((statusId, stackData) in statusMap) {
            val def = definitions[statusId] ?: continue

            // 移除过期的层
            when (def.decayMode) {
                DecayMode.INDIVIDUAL -> {
                    stackData.stackTimestamps.removeIf { it < now }
                    stackData.stacks = stackData.stackTimestamps.size
                }
                DecayMode.REFRESH -> {
                    if (now - stackData.lastRefreshTime > def.stackDuration) {
                        stackData.stacks = 0
                    }
                }
            }

            if (stackData.stacks <= 0) {
                toRemove.add(statusId)
                continue
            }

            // 持续伤害 — 优先使用 per_stack 回调
            val perStackCallbackId = "status:$statusId:per_stack"
            if (AriaCallbackManager.has(perStackCallbackId)) {
                val attacker = stackData.appliedBy?.let { Bukkit.getEntity(it) as? LivingEntity }
                AriaCallbackManager.invoke(perStackCallbackId, entity, stackData.stacks, attacker, statusId)
            } else if (def.perStackDamageRatio > 0 && stackData.appliedBy != null) {
                val attacker = Bukkit.getEntity(stackData.appliedBy) as? LivingEntity
                if (attacker != null) {
                    val atk = AttributeCalculator.getValue(attacker, def.damageType.let {
                        if (it == "physical") "physical_damage" else "${it}_damage"
                    })
                    val damage = atk * def.perStackDamageRatio * stackData.stacks
                    StatusDamageGuard.damage(entity, damage, attacker)
                }
            }
        }

        for (id in toRemove) {
            statusMap.remove(id)
        }

        if (toRemove.isNotEmpty()) {
            AttributeCache.markDirty(entity.uniqueId)
        }
    }

    private fun onMaxStacks(entity: LivingEntity, def: StatusDefinition, appliedBy: UUID?) {
        val callbackId = "status:${def.id}:on_max_stacks"
        if (AriaCallbackManager.has(callbackId)) {
            // 使用脚本回调
            AriaCallbackManager.invoke(callbackId, entity, def.maxStacks, appliedBy?.let { Bukkit.getEntity(it) })
        } else {
            // fallback 到默认逻辑
            val attacker = appliedBy?.let { Bukkit.getEntity(it) as? LivingEntity }
            if (attacker != null) {
                val atk = AttributeCalculator.getValue(attacker, "physical_damage")
                StatusDamageGuard.damage(entity, atk * 2.0, attacker)
            }
            entity.world.spawnParticle(Particle.DAMAGE_INDICATOR, entity.location.add(0.0, 1.0, 0.0), 30, 0.5, 0.5, 0.5)
        }

        // 清除层数
        clearStacks(entity, def.id)

        // 设置免疫
        setImmune(entity, def.id, def.immuneDuration)
    }

    private fun getStatusMap(entity: LivingEntity): MutableMap<String, StatusStackData> {
        val uuid = entity.uniqueId
        val playerData = PlayerDataManager.getData(uuid)
        if (playerData != null) return playerData.runtime.statusStacks
        return EntityRuntimeCache.get(uuid).statusStacks
    }

    fun registerDefaults() {
        register(StatusDefinition("bleed", "流血", "&c\uD83E\uDE78", 10, 8000, DecayMode.INDIVIDUAL, 1000, "physical", 0.05))
        register(StatusDefinition("frostbite", "冻伤", "&b❄", 5, 6000, DecayMode.REFRESH, 1000, "ice", 0.0,
            perStackAttributes = mapOf("movement_speed" to StackAttribute("PERCENT", -0.08))))
        register(StatusDefinition("electro_charge", "电荷", "&e⚡", 8, 5000, DecayMode.INDIVIDUAL, 1000, "lightning", 0.0))
    }
}
