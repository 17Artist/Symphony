package priv.seventeen.artist.symphony.core.affix.action

import priv.seventeen.artist.aria.Aria
import priv.seventeen.artist.aria.api.AriaCompiledRoutine
import priv.seventeen.artist.blink.BlinkLog
import priv.seventeen.artist.blink.script.AriaScriptManager
import priv.seventeen.artist.symphony.api.affix.AffixInstance
import priv.seventeen.artist.symphony.api.trigger.ITriggerContext
import priv.seventeen.artist.symphony.core.script.SymphonyScriptEngine
import java.util.concurrent.ConcurrentHashMap

class ScriptActionHandler : ActionHandler {

    companion object {
        /** code hash → 编译产物缓存 */
        private val cache = ConcurrentHashMap<Int, AriaCompiledRoutine>()

        fun clearCache() = cache.clear()
    }

    override fun execute(params: Map<String, Any>, context: ITriggerContext, affix: AffixInstance) {
        val code = resolveParam(params["code"], affix.parameters)
        val file = params["file"]?.toString()

        if (code.isBlank() && file == null) return

        if (!AriaScriptManager.isAvailable) {
            BlinkLog.warn("Aria 不可用，跳过脚本 Action")
            return
        }

        try {
            val scriptCode = if (file != null) {
                val fileObj = java.io.File(file)
                val canonical = fileObj.canonicalPath
                val pluginsDir = java.io.File("plugins").canonicalPath
                if (!canonical.startsWith(pluginsDir)) {
                    BlinkLog.warn("脚本路径越界被阻止: $file")
                    return
                }
                if (fileObj.exists()) fileObj.readText() else code
            } else code

            val routine = cache.computeIfAbsent(scriptCode.hashCode()) {
                Aria.compile("action:${affix.affixId}:${it}", scriptCode)
            }

            val ctx = Aria.createContext()
            val vars = buildMap<String, Any?> {
                put("trigger_entity", context.entity)
                put("trigger_type", context.triggerType.id)
                put("trigger_location", context.location)
                put("affix_id", affix.affixId)
                put("affix_level", affix.level)
                context.target?.let { put("trigger_victim", it) }
                context.damage?.let { put("trigger_damage", it) }
                affix.parameters.forEach { (k, v) -> put(k, v) }
            }
            SymphonyScriptEngine.injectVars(ctx, vars)
            routine.execute(ctx)
        } catch (e: Exception) {
            BlinkLog.warn("脚本 Action 执行失败: ${e.message}")
        }
    }
}
