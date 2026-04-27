package priv.seventeen.artist.symphony.core.script

import priv.seventeen.artist.aria.Aria
import priv.seventeen.artist.aria.api.AriaCompiledRoutine
import priv.seventeen.artist.blink.BlinkLog
import java.util.concurrent.ConcurrentHashMap

/**
 * 公式引擎 — 预编译 Aria 脚本公式，高频调用时直接执行编译产物。
 */
class FormulaEngine {
    private val compiled = ConcurrentHashMap<String, AriaCompiledRoutine>()

    fun register(name: String, code: String) {
        try {
            compiled[name] = Aria.compile("formula:$name", code)
        } catch (e: Exception) {
            BlinkLog.warn("公式编译失败 $name: ${e.message}")
        }
    }

    fun has(name: String): Boolean = compiled.containsKey(name)

    fun get(name: String): AriaCompiledRoutine? = compiled[name]

    fun clear() {
        compiled.clear()
    }
}
