package priv.seventeen.artist.symphony.api.trigger

interface ITriggerCondition {
    val type: String
    fun evaluate(context: ITriggerContext, params: Map<String, Any>): Boolean
}
