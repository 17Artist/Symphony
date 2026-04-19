package priv.seventeen.artist.symphony.core.affix.action

import priv.seventeen.artist.blink.BlinkLog
import priv.seventeen.artist.blink.script.AriaScriptManager
import priv.seventeen.artist.symphony.api.affix.AffixInstance
import priv.seventeen.artist.symphony.api.trigger.ITriggerContext

class ScriptActionHandler : ActionHandler {

    override fun execute(params: Map<String, Any>, context: ITriggerContext, affix: AffixInstance) {
        val code = resolveParam(params["code"], affix.parameters)
        val file = params["file"]?.toString()

        if (code.isBlank() && file == null) return

        if (!AriaScriptManager.isAvailable) {
            BlinkLog.warn("Aria 不可用，跳过脚本 Action")
            return
        }

        try {
            // 构建上下文变量
            val vars = mutableMapOf<String, Any>(
                "trigger_entity" to context.entity,
                "trigger_type" to context.triggerType.id,
                "trigger_location" to context.location,
                "affix_id" to affix.affixId,
                "affix_level" to affix.level
            )
            context.target?.let { vars["trigger_victim"] = it }
            context.damage?.let { vars["trigger_damage"] = it }
            affix.parameters.forEach { (k, v) -> vars[k] = v }

            val scriptCode = if (file != null) {
                val fileObj = java.io.File(file)
                // 安全检查：脚本文件必须在 plugins 目录下
                val canonical = fileObj.canonicalPath
                val pluginsDir = java.io.File("plugins").canonicalPath
                if (!canonical.startsWith(pluginsDir)) {
                    BlinkLog.warn("脚本路径越界被阻止: $file")
                    return
                }
                if (fileObj.exists()) fileObj.readText() else code
            } else code

            AriaScriptManager.eval(scriptCode, vars)
        } catch (e: Exception) {
            BlinkLog.warn("脚本 Action 执行失败: ${e.message}")
        }
    }
}
