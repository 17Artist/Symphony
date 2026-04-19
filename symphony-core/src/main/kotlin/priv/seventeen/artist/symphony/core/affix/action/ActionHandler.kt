package priv.seventeen.artist.symphony.core.affix.action

import priv.seventeen.artist.symphony.api.affix.IActionHandler

/** 内部别名：与公开 [IActionHandler] 等价，内置处理器继续实现此 interface。 */
interface ActionHandler : IActionHandler

fun resolveParam(raw: Any?, affixParams: Map<String, Any>): String {
    if (raw == null) return ""
    val str = raw.toString()
    var result = str
    val regex = Regex("\\{(\\w+)}")
    for (match in regex.findAll(str)) {
        val key = match.groupValues[1]
        val value = affixParams[key]?.toString() ?: match.value
        result = result.replace(match.value, value)
    }
    return result
}

fun resolveDouble(raw: Any?, affixParams: Map<String, Any>): Double {
    return resolveParam(raw, affixParams).toDoubleOrNull() ?: 0.0
}

fun resolveInt(raw: Any?, affixParams: Map<String, Any>): Int {
    return resolveParam(raw, affixParams).toIntOrNull() ?: 0
}
