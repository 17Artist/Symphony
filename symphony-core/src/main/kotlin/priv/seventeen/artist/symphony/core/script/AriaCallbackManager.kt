package priv.seventeen.artist.symphony.core.script

import priv.seventeen.artist.aria.Aria
import priv.seventeen.artist.aria.api.AriaCompiledRoutine
import priv.seventeen.artist.aria.value.NoneValue
import priv.seventeen.artist.aria.value.IValue
import priv.seventeen.artist.aria.value.NumberValue
import priv.seventeen.artist.aria.value.StringValue
import priv.seventeen.artist.aria.value.BooleanValue
import priv.seventeen.artist.aria.callable.NativeCallable
import priv.seventeen.artist.blink.BlinkLog
import java.util.concurrent.ConcurrentHashMap

/**
 * Aria 脚本回调管理器 — 统一管理 YAML 配置中的脚本回调编译和执行。
 *
 * 用法：
 * 1. 配置加载时 `compile(id, code)` 预编译
 * 2. 运行时 `invoke(id, arg0, arg1, ...)` 执行，脚本中通过 `args[N]` 访问参数
 * 3. `invokeCondition(id, ...)` 执行并返回 Boolean
 */
object AriaCallbackManager {

    private val cache = ConcurrentHashMap<String, AriaCompiledRoutine>()

    /**
     * 编译并缓存回调脚本。
     * @param id 唯一标识（如 "status:bleed:on_max_stacks"）
     * @param code Aria 脚本代码
     * @return 编译成功返回 true
     */
    fun compile(id: String, code: String): Boolean {
        return try {
            cache[id] = Aria.compile(id, code)
            true
        } catch (e: Exception) {
            BlinkLog.warn("回调脚本编译失败 [$id]: ${e.message}")
            false
        }
    }

    /**
     * 编译表达式为回调脚本。自动注入命名参数并包装 return。
     *
     * 单行表达式（无 return）：
     *   输入: "20 + level * 2"
     *   生成: val.level = args[0]
     *         return (20 + level * 2)
     *
     * 多行脚本（有 return）：
     *   输入: if (level >= 20) { return 20 + level * 2 } return 10 + level
     *   生成: val.level = args[0]
     *         <原文>
     *
     * 如果用户已经在脚本中自己绑定了参数（val.level = args[0] 或 var.level = args[0]），
     * 不再重复注入，避免变量冲突。
     *
     * 同时兼容用户写法 `val level = args[0]`（漏写点），自动修正为 `val.level`。
     *
     * @param id 唯一标识
     * @param expression 表达式或完整脚本
     * @param paramNames 参数名列表，按顺序对应 args[0], args[1], ...
     */
    fun compileExpression(id: String, expression: String, vararg paramNames: String): Boolean {
        var body = expression.trim()

        // 修正用户可能的误写：val level = args[0] → val.level = args[0]
        // Aria 要求点号语法
        for (name in paramNames) {
            body = body.replace(Regex("\\bval\\s+$name\\s*="), "val.$name =")
            body = body.replace(Regex("\\bvar\\s+$name\\s*="), "val.$name =")
        }

        // 只为用户脚本中尚未绑定的参数注入绑定
        val bindingsToAdd = paramNames.mapIndexedNotNull { index, name ->
            val alreadyBound = body.contains(Regex("val\\.$name\\s*="))
            if (alreadyBound) null else "val.$name = args[$index]"
        }.joinToString("\n")

        // 如果已经有 return 语句，不再包装
        val hasReturn = body.contains(Regex("\\breturn\\b"))
        val code = buildString {
            if (bindingsToAdd.isNotEmpty()) appendLine(bindingsToAdd)
            if (hasReturn) append(body) else append("return ($body)")
        }
        return compile(id, code)
    }

    /**
     * 检查指定 id 的回调是否已编译。
     */
    fun has(id: String): Boolean = cache.containsKey(id)

    /**
     * 执行回调，传入参数。脚本中通过 args[0], args[1], ... 访问。
     * @return 执行结果，异常时返回 null
     */
    fun invoke(id: String, vararg args: Any?): Any? {
        val routine = cache[id] ?: return null
        val ctx = Aria.createContext()
        val wrappedArgs = args.map { v ->
            when (v) {
                null -> NoneValue.NONE as IValue<*>
                is Number -> NumberValue(v.toDouble())
                is String -> StringValue(v)
                is Boolean -> BooleanValue.of(v) as IValue<*>
                else -> NativeCallable.wrapObject(v) as IValue<*>
            }
        }.toTypedArray()
        ctx.setArgs(wrappedArgs)
        return try {
            val result = routine.execute(ctx)
            if (result is IValue<*>) result.jvmValue() else result
        } catch (e: Exception) {
            BlinkLog.warn("回调脚本执行失败 [$id]: ${e.message}")
            null
        }
    }

    /**
     * 执行条件回调，返回 Boolean。
     */
    fun invokeCondition(id: String, vararg args: Any?): Boolean {
        val result = invoke(id, *args)
        return when (result) {
            is Boolean -> result
            is Number -> result.toDouble() != 0.0
            is String -> result.toBoolean()
            else -> false
        }
    }

    /**
     * 清除所有缓存（reload 时调用）。
     */
    fun clear() = cache.clear()

    /**
     * 获取已缓存的回调数量。
     */
    fun size(): Int = cache.size
}
