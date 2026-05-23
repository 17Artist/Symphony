package priv.seventeen.artist.symphony.core.attribute

import org.bukkit.Bukkit
import org.bukkit.entity.LivingEntity
import priv.seventeen.artist.aria.Aria
import priv.seventeen.artist.aria.callable.ICallable
import priv.seventeen.artist.aria.callable.InvocationData
import priv.seventeen.artist.aria.callable.NativeCallable
import priv.seventeen.artist.aria.value.IValue
import priv.seventeen.artist.blink.BlinkLog
import priv.seventeen.artist.symphony.api.IAttributeContribution
import priv.seventeen.artist.symphony.api.IAttributeExplain
import priv.seventeen.artist.symphony.api.IAttributeInteractionEffect
import priv.seventeen.artist.symphony.api.attribute.AttributeModifier
import priv.seventeen.artist.symphony.api.attribute.Operation
import priv.seventeen.artist.symphony.api.event.AttributeUpdateEvent
import priv.seventeen.artist.symphony.core.script.AttributeCallableRegistry

/**
 * 属性计算引擎。
 * 多层叠加：base → equipment → gem → rune → enhance → affix → buff → script
 *
 * 派生属性使用「惰性求值 + 循环检测」拓扑：
 * derive 脚本中 `getRaw('xxx')` 经 SymphonyBridge 转发到 [lookupForDerive]，
 * 若 xxx 是 readonly 且未算，则递归先算。栈上 `computing` 集合捕获环依赖。
 */
object AttributeCalculator {

    private class DeriveContext(
        val entity: LivingEntity,
        val results: MutableMap<String, Double>,
        val computing: MutableSet<String> = LinkedHashSet(),
        val done: MutableSet<String> = HashSet()
    )

    private val deriveCtx = ThreadLocal<DeriveContext?>()

    fun recalculate(entity: LivingEntity) {
        // 收集所有 Provider 的修改器（同步路径，包括异步标记的）
        val modifiers = mutableListOf<AttributeModifier>()
        for (provider in AttributeProviderRegistry.getAll()) {
            if (!provider.appliesTo(entity)) continue
            try { modifiers += provider.provide(entity) } catch (e: Exception) {
                BlinkLog.warn("Provider '${provider.id}' 异常: ${e.message}")
            }
        }
        applyModifiers(entity, modifiers)
    }

    /**
     * 给定已收集的 modifiers，执行后续聚合 / @formula / @derive / 缓存 / 事件。
     * **必须在主线程调用**（事件广播）。供 [AsyncRecalcScheduler] 使用。
     */
    fun applyModifiers(entity: LivingEntity, modifiers: List<AttributeModifier>) {
        val entityId = entity.uniqueId
        // 只读视图：仅用于读取旧值做事件 diff，applyModifiers 内部不修改它
        val previous = AttributeCache.getAllView(entityId)
        val results = mutableMapOf<String, Double>()

        // 单次扫描聚合 flat/percent，避免 groupBy + filter
        val flatSums = HashMap<String, Double>(modifiers.size / 2)
        val percentSums = HashMap<String, Double>(modifiers.size / 2)
        for (m in modifiers) {
            when (m.operation) {
                Operation.FLAT -> flatSums[m.attributeId] = (flatSums[m.attributeId] ?: 0.0) + m.value
                Operation.PERCENT -> percentSums[m.attributeId] = (percentSums[m.attributeId] ?: 0.0) + m.value
            }
        }

        // 局部脏：仅重算受影响属性，其余沿用旧值
        val dirtyOnly = AttributeCache.getDirtyAttributes(entityId)
        val mustRecompute: (String) -> Boolean = if (dirtyOnly == null) {
            { true }  // 全脏 / 全量
        } else {
            { it in dirtyOnly }
        }

        // 计算普通属性
        for (attr in AttributeRegistry.getAll()) {
            if (attr.readonly) continue

            if (!mustRecompute(attr.id)) {
                previous[attr.id]?.let { results[attr.id] = it }
                continue
            }

            // @when 门控：条件不满足直接填默认值
            if (!AttributeConditionEvaluator.allMatch(entity, attr.whenConditions)) {
                results[attr.id] = resolveDefault(attr, entity)
                continue
            }

            val flatSum = flatSums[attr.id] ?: 0.0
            val percentSum = percentSums[attr.id] ?: 0.0

            val base = resolveDefault(attr, entity)
            val value = invokeFormula(attr.id, base, flatSum, percentSum, entity)
                ?: ((base + flatSum) * (1.0 + percentSum))
            val clamped = value.coerceIn(resolveMin(attr, entity), resolveMax(attr, entity))
            results[attr.id] = clamped
        }

        // 派生属性 — 惰性递归 + 循环检测
        val ctx = DeriveContext(entity, results)
        deriveCtx.set(ctx)
        try {
            for (attr in AttributeRegistry.getAll()) {
                if (!attr.readonly) continue
                if (!mustRecompute(attr.id)) {
                    previous[attr.id]?.let { results[attr.id] = it; ctx.done += attr.id }
                    continue
                }
                if (!AttributeConditionEvaluator.allMatch(entity, attr.whenConditions)) {
                    results[attr.id] = resolveDefault(attr, entity)
                    ctx.done += attr.id
                    continue
                }
                computeDerive(attr.id, ctx)
            }
        } finally {
            deriveCtx.remove()
        }

        // 最终一次性写入缓存
        AttributeCache.setAll(entityId, results)
        AttributeCache.clearDirty(entityId)

        // 广播变更事件 + @onChange
        for ((id, newValue) in results) {
            val oldValue = previous[id]
            if (oldValue == null || oldValue != newValue) {
                Bukkit.getPluginManager().callEvent(
                    AttributeUpdateEvent(entity, id, oldValue ?: 0.0, newValue, "recalculate")
                )
                AttributeListenerRegistry.fire(entity, id, oldValue ?: 0.0, newValue)
                fireOnChange(entity, id, oldValue ?: 0.0, newValue)
            }
        }
    }

    //  表达式覆盖（默认值 / 上下限） 
    private fun resolveDefault(attr: AttributeDefinition, entity: LivingEntity): Double =
        evalExpr(AttributeCallableRegistry.getDefaultExpr(attr.id), entity) ?: attr.defaultValue

    private fun resolveMin(attr: AttributeDefinition, entity: LivingEntity): Double =
        evalExpr(AttributeCallableRegistry.getMinExpr(attr.id), entity) ?: attr.minValue

    private fun resolveMax(attr: AttributeDefinition, entity: LivingEntity): Double =
        evalExpr(AttributeCallableRegistry.getMaxExpr(attr.id), entity) ?: attr.maxValue

    private fun evalExpr(fn: ICallable?, entity: LivingEntity): Double? {
        if (fn == null) return null
        return try {
            val ctx = Aria.createContext()
            val args = arrayOf<IValue<*>>(NativeCallable.wrapObject(entity))
            fn.invoke(InvocationData(ctx, null, args)).numberValue()
        } catch (e: Exception) {
            BlinkLog.warn("属性表达式执行失败: ${e.message}")
            null
        }
    }

    /**
     * 由 SymphonyBridge.attributeGetRaw 在 derive 上下文中调用。
     * 若 attrId 是当前实体上尚未算出的 readonly 属性，递归先算它。
     * 不在 derive 上下文中（普通调用）返回 null，由调用方走常规 getValue。
     */
    fun lookupForDerive(attrId: String): Double? {
        val ctx = deriveCtx.get() ?: return null
        // 普通属性已在 results 中
        ctx.results[attrId]?.let { if (attrId in ctx.done || !isReadonly(attrId)) return it }
        // readonly 但未算 → 递归
        if (isReadonly(attrId)) computeDerive(attrId, ctx)
        return ctx.results[attrId]
    }

    private fun isReadonly(attrId: String): Boolean =
        AttributeRegistry.get(attrId)?.readonly == true

    private fun computeDerive(attrId: String, ctx: DeriveContext): Double {
        if (attrId in ctx.done) return ctx.results[attrId] ?: 0.0
        val attr = AttributeRegistry.get(attrId) ?: return 0.0
        if (!attr.readonly) return ctx.results[attrId] ?: resolveDefault(attr, ctx.entity)
        if (attrId in ctx.computing) {
            BlinkLog.warn("派生属性循环依赖: ${ctx.computing.joinToString(" -> ")} -> $attrId，使用默认值")
            return resolveDefault(attr, ctx.entity)
        }
        ctx.computing += attrId
        val raw = invokeDerive(attrId, ctx.entity) ?: resolveDefault(attr, ctx.entity)
        val clamped = raw.coerceIn(resolveMin(attr, ctx.entity), resolveMax(attr, ctx.entity))
        ctx.computing -= attrId
        ctx.done += attrId
        ctx.results[attrId] = clamped
        return clamped
    }

    fun markDirty(entity: LivingEntity) {
        AttributeCache.markDirty(entity.uniqueId)
        // 同时失效装备词条缓存，确保下次 dispatch/provide 重新读取
        if (entity is org.bukkit.entity.Player) {
            priv.seventeen.artist.symphony.core.storage.PlayerDataManager
                .getData(entity.uniqueId)?.runtime?.invalidateAffixCache()
        }
    }

    fun ensureCalculated(entity: LivingEntity) {
        if (AttributeCache.isDirty(entity.uniqueId)) {
            recalculate(entity)
        }
    }

    fun getValue(entity: LivingEntity, attributeId: String): Double {
        ensureCalculated(entity)
        return AttributeCache.get(entity.uniqueId, attributeId)
            ?: AttributeRegistry.get(attributeId)?.defaultValue
            ?: 0.0
    }

    fun getValues(entity: LivingEntity): Map<String, Double> {
        ensureCalculated(entity)
        return AttributeCache.getAll(entity.uniqueId)
    }

    //  Debug / Explain 
    data class AttributeContribution(
        override val providerId: String,
        override val source: String,
        override val operation: Operation,
        override val value: Double
    ) : IAttributeContribution

    data class AttributeInteractionEffect(
        override val interactionId: String,
        override val type: String,
        override val role: String,
        override val description: String
    ) : IAttributeInteractionEffect

    data class AttributeExplain(
        override val attrId: String,
        override val displayName: String,
        override val base: Double,
        override val contributions: List<AttributeContribution>,
        override val interactions: List<AttributeInteractionEffect>,
        override val formulaDescription: String,
        override val finalValue: Double,
        override val whenActive: Boolean,
        override val readonly: Boolean
    ) : IAttributeExplain

    fun explain(entity: LivingEntity, attrId: String): AttributeExplain? {
        val attr = AttributeRegistry.get(attrId) ?: return null
        val whenActive = AttributeConditionEvaluator.allMatch(entity, attr.whenConditions)
        val base = resolveDefault(attr, entity)
        val contribs = mutableListOf<AttributeContribution>()
        if (whenActive && !attr.readonly) {
            for (provider in AttributeProviderRegistry.getAll()) {
                try {
                    for (m in provider.provide(entity)) {
                        if (m.attributeId == attrId) {
                            contribs += AttributeContribution(provider.id, m.source, m.operation, m.value)
                        }
                    }
                } catch (_: Exception) {}
            }
        }
        // 收集影响该属性的交互
        val interactions = collectInteractionEffects(attrId)
        val formulaDesc = when {
            attr.readonly -> "derive: ${attr.deriveId ?: "none"}"
            AttributeCallableRegistry.getFormula(attrId) != null -> "formula: ${attr.formulaId}"
            else -> "(base + Σflat) × (1 + Σpercent)"
        }
        ensureCalculated(entity)
        val finalValue = AttributeCache.get(entity.uniqueId, attrId) ?: base
        return AttributeExplain(
            attrId = attrId,
            displayName = attr.displayName,
            base = base,
            contributions = contribs,
            interactions = interactions,
            formulaDescription = formulaDesc,
            finalValue = finalValue,
            whenActive = whenActive,
            readonly = attr.readonly
        )
    }

    /** 收集所有"作用于该属性"的交互定义（按角色分类）。 */
    private fun collectInteractionEffects(attrId: String): List<AttributeInteractionEffect> {
        val list = mutableListOf<AttributeInteractionEffect>()
        for (def in priv.seventeen.artist.symphony.core.advanced.interaction.InteractionNetwork.getAll()) {
            val type = def.type.name
            // CONVERSION / OVERFLOW / DIMINISH / THRESHOLD: source -> target
            if (def.source == attrId) {
                val role = if (type == "THRESHOLD" || type == "DIMINISH") "source" else "source"
                val desc = describeInteraction(def, "source")
                list += AttributeInteractionEffect(def.id, type, role, desc)
            }
            if (def.target == attrId) {
                list += AttributeInteractionEffect(def.id, type, "target", describeInteraction(def, "target"))
            }
            // SYNERGY: attributes 列表
            if (attrId in def.attributes) {
                list += AttributeInteractionEffect(def.id, type, "synergy_member", describeInteraction(def, "synergy_member"))
            }
            // CONFLICT: attributeA / attributeB
            if (def.attributeA == attrId) {
                list += AttributeInteractionEffect(def.id, type, "conflict_a", describeInteraction(def, "conflict_a"))
            }
            if (def.attributeB == attrId) {
                list += AttributeInteractionEffect(def.id, type, "conflict_b", describeInteraction(def, "conflict_b"))
            }
        }
        return list
    }

    private fun describeInteraction(
        def: priv.seventeen.artist.symphony.core.advanced.interaction.InteractionDefinition,
        role: String
    ): String {
        return when (def.type.name) {
            "CONVERSION" -> if (role == "source") "${def.source} × ${def.ratio} → ${def.target}"
                else "← ${def.source} × ${def.ratio}"
            "OVERFLOW" -> if (role == "source") "${def.source} 超过 ${def.threshold} 部分 × ${def.ratio} 转入 ${def.target}（截断到阈值）"
                else "← ${def.source} 溢出 × ${def.ratio}"
            "SYNERGY" -> "全员 ≥ ${def.threshold} 时 × (1 + ${def.bonus})"
            "CONFLICT" -> if (role == "conflict_a") "≥ ${def.thresholdA} 时使 ${def.attributeB} × (1 - ${def.penaltyB})"
                else "受 ${def.attributeA} ≥ ${def.thresholdA} 衰减 × (1 - ${def.penaltyB})"
            "AMPLIFY" -> "条件满足时 × ${def.multiplier}"
            "THRESHOLD" -> "≥ ${def.threshold} 时触发 ${def.id} 效果（不直接改值）"
            "DIMINISH" -> "超过 ${def.threshold} 后递减收益（excess/(excess+100)*100）"
            else -> def.description.ifEmpty { def.id }
        }
    }

    //  Aria 脚本函数调用 
    private fun invokeDerive(attrId: String, entity: LivingEntity): Double? {
        val fn = AttributeCallableRegistry.getDerive(attrId) ?: return null
        return try {
            val ctx = Aria.createContext()
            val args = arrayOf<IValue<*>>(NativeCallable.wrapObject(entity))
            val result = fn.invoke(InvocationData(ctx, null, args))
            result.numberValue()
        } catch (e: Exception) {
            BlinkLog.warn("派生属性 '$attrId' 计算失败: ${e.message}")
            null
        }
    }

    private fun invokeFormula(
        attrId: String,
        base: Double,
        flat: Double,
        percent: Double,
        entity: LivingEntity
    ): Double? {
        val fn = AttributeCallableRegistry.getFormula(attrId) ?: return null
        return try {
            val ctx = Aria.createContext()
            val args = arrayOf<IValue<*>>(
                NativeCallable.wrapObject(base),
                NativeCallable.wrapObject(flat),
                NativeCallable.wrapObject(percent),
                NativeCallable.wrapObject(entity)
            )
            fn.invoke(InvocationData(ctx, null, args)).numberValue()
        } catch (e: Exception) {
            BlinkLog.warn("公式 '$attrId' 计算失败: ${e.message}")
            null
        }
    }

    /**
     * 对外供 Provider 层调用，用于属性值变化时触发 @onChange 回调。
     */
    fun fireOnChange(entity: LivingEntity, attrId: String, oldValue: Double, newValue: Double) {
        val fn = AttributeCallableRegistry.getOnChange(attrId) ?: return
        try {
            val ctx = Aria.createContext()
            val args = arrayOf<IValue<*>>(
                NativeCallable.wrapObject(entity),
                NativeCallable.wrapObject(oldValue),
                NativeCallable.wrapObject(newValue)
            )
            fn.invoke(InvocationData(ctx, null, args))
        } catch (e: Exception) {
            BlinkLog.warn("属性 '$attrId' onChange 回调失败: ${e.message}")
        }
    }
}
