package priv.seventeen.artist.symphony.core.script

import priv.seventeen.artist.aria.Aria
import priv.seventeen.artist.aria.callable.ICallable
import priv.seventeen.artist.aria.value.FunctionValue
import priv.seventeen.artist.blink.BlinkLog
import priv.seventeen.artist.blink.script.AriaScriptManager

/**
 * 轻量表达式编译器 — 把字符串表达式包装为 Aria lambda 并预编译。
 *
 * 表达式内部可引用以下自动绑定：
 * - `entity` — 当前实体（LivingEntity，`args[0]` 透传）
 * - 通过 `symphony.attribute.getRaw(entity, 'xxx')` 读任意属性
 *
 * 用法：`@defaultExpr("symphony.attribute.getRaw(entity, 'level') * 2 + 5")`
 */
object ExpressionCompiler {

    /**
     * 编译字符串表达式为 ICallable，失败返回 null 并打日志。
     * @param attrId 诊断用
     * @param kind default / min / max，仅用作日志区分
     */
    fun compile(attrId: String, kind: String, expr: String): ICallable? {
        if (!AriaScriptManager.isAvailable) return null
        val code = """
            return -> {
                val.entity = args[0]
                return ($expr)
            }
        """.trimIndent()
        return try {
            val routine = Aria.compile("expr:${attrId}:${kind}", code)
            val result = routine.execute(Aria.createContext())
            if (result is FunctionValue) result.jvmValue()
            else {
                BlinkLog.warn("表达式 §c${attrId}.${kind}Expr §f返回类型非 Function")
                null
            }
        } catch (e: Exception) {
            BlinkLog.warn("表达式 §c${attrId}.${kind}Expr §f编译失败: ${e.message}")
            null
        }
    }
}
