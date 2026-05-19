package priv.seventeen.artist.symphony.core.attribute.provider

import org.bukkit.entity.LivingEntity
import org.bukkit.entity.Player
import priv.seventeen.artist.aria.Aria
import priv.seventeen.artist.aria.callable.ICallable
import priv.seventeen.artist.aria.callable.InvocationData
import priv.seventeen.artist.aria.callable.NativeCallable
import priv.seventeen.artist.aria.value.FunctionValue
import priv.seventeen.artist.aria.value.IValue
import priv.seventeen.artist.aria.value.NumberValue
import priv.seventeen.artist.blink.BlinkLog
import priv.seventeen.artist.symphony.api.attribute.AttributeModifier
import priv.seventeen.artist.symphony.api.attribute.IAttributeProvider
import priv.seventeen.artist.symphony.api.attribute.Operation
import priv.seventeen.artist.symphony.core.growth.level.LevelManager
import priv.seventeen.artist.symphony.core.storage.PlayerDataManager
import java.util.concurrent.ConcurrentHashMap

/**
 * 等级属性成长 Provider — 从 LevelManager.attributeGrowth 配置读取每级属性加成。
 * 优先级 150，在 BaseProvider(100) 之后、EquipmentProvider(200) 之前。
 *
 * formula 执行策略：
 * 不走公共 AriaCallbackManager（避免影响环境/技能/状态等其他回调），
 * 而是在本 Provider 内部直接用 Aria API 编译执行。
 *
 * 编译方式参照 ExpressionCompiler：
 * 将用户脚本包装为 `return -> { ... }` 形式，编译后提取 ICallable，
 * 运行时通过 ICallable.invoke(InvocationData) 调用，确保 return 语句正确传递返回值。
 */
class LevelProvider(private val levelManager: LevelManager) : IAttributeProvider {
    override val id = "level_growth"
    override val priority = 150
    override fun appliesTo(entity: LivingEntity): Boolean = entity is Player

    /** 属性公式编译缓存 — key 为 attrId，value 为可直接 invoke 的 ICallable */
    private val formulaCache = ConcurrentHashMap<String, ICallable?>()

    override fun provide(entity: LivingEntity): List<AttributeModifier> {
        if (entity !is Player) return emptyList()
        val growth = levelManager.attributeGrowth
        if (growth.isEmpty()) return emptyList()
        // 使用 getOrCreate 确保玩家数据存在（兼容刚加入的玩家）
        val data = PlayerDataManager.getOrCreate(entity.uniqueId)
        val level = data.persistent.level

        return growth.mapNotNull { (attrId, entry) ->
            val value = if (entry.formula != null) {
                evaluateFormula(attrId, entry.formula, level, entry.base + entry.perLevel * (level - 1))
            } else {
                entry.base + entry.perLevel * (level - 1)
            }
            if (value != 0.0) AttributeModifier(attrId, Operation.FLAT, value, "level_growth:$attrId") else null
        }
    }

    /**
     * 执行等级属性公式，返回 Double。
     *
     * 编译策略（参照 ExpressionCompiler 的成功模式）：
     * ```aria
     * return -> {
     *     val.level = args[0]
     *     <用户脚本>
     * }
     * ```
     * 编译后 execute 返回 FunctionValue，提取 ICallable 缓存。
     * 运行时通过 ICallable.invoke(InvocationData(ctx, null, [NumberValue(level)])) 调用。
     */
    private fun evaluateFormula(attrId: String, formula: String, level: Int, fallback: Double): Double {
        return try {
            // computeIfAbsent 不能用（lambda 内不能 return），手动处理
            val fn = if (formulaCache.containsKey(attrId)) {
                formulaCache[attrId]
            } else {
                val compiled = compileFormula(attrId, formula)
                formulaCache[attrId] = compiled
                compiled
            }
            if (fn == null) return fallback

            val ctx = Aria.createContext()
            val args = arrayOf<IValue<*>>(NumberValue(level.toDouble()))
            val result = fn.invoke(InvocationData(ctx, null, args))
            result.numberValue()
        } catch (e: Exception) {
            BlinkLog.warn("等级成长公式 $attrId 执行异常: ${e.message}")
            fallback
        }
    }

    /**
     * 编译公式为 ICallable。
     *
     * 生成代码：
     * ```aria
     * return -> {
     *     val.level = args[0]
     *     <用户脚本（修正后）>
     * }
     * ```
     * execute 后得到 FunctionValue，通过 jvmValue() 提取 ICallable。
     */
    private fun compileFormula(attrId: String, formula: String): ICallable? {
        var body = formula.trim()

        // 修正用户常见误写
        body = body.replace(Regex("\\bval\\s+level\\s*="), "val.level =")
        body = body.replace(Regex("\\bvar\\s+level\\s*="), "val.level =")

        // 检测用户是否已自行绑定 level
        val alreadyBound = body.contains(Regex("val\\.level\\s*="))

        // 检测是否有 return 语句 — 如果没有，把最后一行包装为 return
        val hasReturn = body.contains(Regex("\\breturn\\b"))
        val scriptBody = if (hasReturn) body else "return ($body)"

        // 包装为 lambda 并 return 它（参照 ExpressionCompiler 的成功模式）
        val code = buildString {
            append("return -> {\n")
            if (!alreadyBound) append("    val.level = args[0]\n")
            append("    ")
            append(scriptBody.replace("\n", "\n    "))
            append("\n}")
        }

        return try {
            val routine = Aria.compile("growth_formula:$attrId", code)
            val result = routine.execute(Aria.createContext())
            if (result is FunctionValue) {
                val callable = result.jvmValue()
                if (callable is ICallable) {
                    BlinkLog.info("等级公式 $attrId 编译成功 (ICallable 模式)")
                    callable
                } else {
                    BlinkLog.warn("等级成长公式 $attrId 编译异常: FunctionValue.jvmValue() 返回 ${callable?.javaClass?.name}")
                    null
                }
            } else {
                BlinkLog.warn("等级成长公式 $attrId 编译异常: execute 返回 ${result?.javaClass?.name}，期望 FunctionValue\n  生成代码:\n$code")
                null
            }
        } catch (e: Exception) {
            BlinkLog.warn("等级成长公式 $attrId 编译失败: ${e.message}\n  生成代码:\n$code")
            null
        }
    }

    /**
     * 清除公式缓存（reload 时调用）。
     */
    fun clearCache() {
        formulaCache.clear()
    }
}
