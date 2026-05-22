package priv.seventeen.artist.symphony.core.affix

import priv.seventeen.artist.blink.BlinkLog
import priv.seventeen.artist.symphony.api.affix.AffixInstance
import priv.seventeen.artist.symphony.api.affix.IActionHandler
import priv.seventeen.artist.symphony.api.trigger.ITriggerContext
import priv.seventeen.artist.symphony.core.affix.action.*

/**
 * 词条效果执行器 — 根据 Action 类型分发到对应处理器。
 */
object AffixProcessor {

    private val actionHandlers = mutableMapOf<String, IActionHandler>()
    /** 内置 handler 类型，用于 reload 时区分清理范围。 */
    private val builtinTypes = mutableSetOf<String>()

    fun init() {
        actionHandlers.clear()
        builtinTypes.clear()
        registerBuiltin("DAMAGE", DamageActionHandler())
        registerBuiltin("HEAL", HealActionHandler())
        registerBuiltin("POTION", PotionActionHandler())
        registerBuiltin("PARTICLE", ParticleActionHandler())
        registerBuiltin("SOUND", SoundActionHandler())
        registerBuiltin("MESSAGE", MessageActionHandler())
        registerBuiltin("COMMAND", CommandActionHandler())
        registerBuiltin("ATTRIBUTE_BUFF", AttributeBuffActionHandler())
        registerBuiltin("SKILL", SkillActionHandler())
        registerBuiltin("STATUS_STACK", StatusStackActionHandler())
        registerBuiltin("MANA", ManaActionHandler())
        registerBuiltin("SCRIPT", ScriptActionHandler())
        registerBuiltin("ATTRIBUTE_PERMANENT", AttributePermanentActionHandler())
    }

    private fun registerBuiltin(type: String, handler: IActionHandler) {
        val key = type.uppercase()
        actionHandlers[key] = handler
        builtinTypes.add(key)
    }

    fun registerHandler(type: String, handler: IActionHandler) {
        actionHandlers[type.uppercase()] = handler
    }

    fun unregisterHandler(type: String) {
        val key = type.uppercase()
        if (key in builtinTypes) return  // 不允许注销内置 handler
        actionHandlers.remove(key)
    }

    fun getRegisteredActionTypes(): Set<String> = actionHandlers.keys.toSet()

    /**
     * Reload 时调用：保留内置 handler，仅清理第三方注册的，避免重新加载累积。
     * 第三方插件需要在 onEnable 重新调 [registerHandler]。
     */
    fun reloadCustomHandlers() {
        val toRemove = actionHandlers.keys.filter { it !in builtinTypes }
        for (key in toRemove) actionHandlers.remove(key)
    }

    fun executeActions(
        actions: List<Map<String, Any>>,
        context: ITriggerContext,
        affix: AffixInstance
    ) {
        for (action in actions) {
            val type = action["type"]?.toString()?.uppercase() ?: continue
            val handler = actionHandlers[type] ?: continue
            try {
                handler.execute(action, context, affix)
            } catch (e: Exception) {
                BlinkLog.warn("Action $type 执行失败 (词条 ${affix.affixId}): ${e.message}")
            }
        }
    }
}
