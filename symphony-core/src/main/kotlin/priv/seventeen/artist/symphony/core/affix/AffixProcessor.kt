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

    fun init() {
        registerHandler("DAMAGE", DamageActionHandler())
        registerHandler("HEAL", HealActionHandler())
        registerHandler("POTION", PotionActionHandler())
        registerHandler("PARTICLE", ParticleActionHandler())
        registerHandler("SOUND", SoundActionHandler())
        registerHandler("MESSAGE", MessageActionHandler())
        registerHandler("COMMAND", CommandActionHandler())
        registerHandler("ATTRIBUTE_BUFF", AttributeBuffActionHandler())
        registerHandler("SKILL", SkillActionHandler())
        registerHandler("STATUS_STACK", StatusStackActionHandler())
        registerHandler("MANA", ManaActionHandler())
        registerHandler("SCRIPT", ScriptActionHandler())
        registerHandler("ATTRIBUTE_PERMANENT", AttributePermanentActionHandler())
    }

    fun registerHandler(type: String, handler: IActionHandler) {
        actionHandlers[type.uppercase()] = handler
    }

    fun unregisterHandler(type: String) {
        actionHandlers.remove(type.uppercase())
    }

    fun getRegisteredActionTypes(): Set<String> = actionHandlers.keys.toSet()

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
