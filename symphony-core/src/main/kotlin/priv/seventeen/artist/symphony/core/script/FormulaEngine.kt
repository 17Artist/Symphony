package priv.seventeen.artist.symphony.core.script

import java.util.concurrent.ConcurrentHashMap

/**
 * 公式引擎 — 预编译 Aria 脚本公式，高频调用。
 * 当 Aria 不可用时使用内置公式。
 */
class FormulaEngine {
    private val formulaCache = ConcurrentHashMap<String, String>()

    fun register(name: String, code: String) {
        formulaCache[name] = code
    }

    fun has(name: String): Boolean = formulaCache.containsKey(name)

    fun getCode(name: String): String? = formulaCache[name]

    fun clear() {
        formulaCache.clear()
    }
}
