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
        val previous = AttributeCache.getAll(entityId)
        val grouped = modifiers.groupBy { it.attributeId }
        val results = mutableMapOf<String, Double>()

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

            val ms = grouped[attr.id] ?: emptyList()
            val flatSum = ms.filter { it.operation == Operation.FLAT }.sumOf { it.value }
            val percentSum = ms.filter { it.operation == Operation.PERCENT }.sumOf { it.value }

            val base = resolveDefault(attr, entity)
            val value = invokeFormula(attr.id, base, flatSum, percentSum, entity)
                ?: ((base + flatSum) * (1.0 + percentSum))
            val clamped = value.coerceIn(resolveMin(attr, entity), resolveMax(attr, entity))
            results[attr.id] = clamped
        }

        AttributeCache.setAll(entityId, results)

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
                    AttributeCache.set(entityId, attr.id, results[attr.id]!!)
                    continue
                }
                computeDerive(attr.id, ctx)
            }
        } finally {
            deriveCtx.remove()
        }

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
        AttributeCache.set(ctx.entity.uniqueId, attrId, clamped)
        return clamped
    }

    fun markDirty(entity: LivingEntity) {
        AttributeCache.markDirty(entity.uniqueId)
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

    data class AttributeExplain(
        override val attrId: String,
        override val displayName: String,
        override val base: Double,
        override val contributions: List<AttributeContribution>,
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
            formulaDescription = formulaDesc,
            finalValue = finalValue,
            whenActive = whenActive,
            readonly = attr.readonly
        )
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
