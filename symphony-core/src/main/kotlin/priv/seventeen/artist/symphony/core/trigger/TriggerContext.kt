package priv.seventeen.artist.symphony.core.trigger

import org.bukkit.Location
import org.bukkit.entity.LivingEntity
import priv.seventeen.artist.symphony.api.trigger.ITriggerContext
import priv.seventeen.artist.symphony.api.trigger.TriggerType

class TriggerContext private constructor(
    override val triggerType: TriggerType,
    override val entity: LivingEntity,
    override val target: LivingEntity?,
    override val timestamp: Long,
    override val location: Location,
    private val data: MutableMap<String, Any>
) : ITriggerContext {

    @Suppress("UNCHECKED_CAST")
    override fun <T> get(key: String): T? = data[key] as? T

    override fun set(key: String, value: Any) { data[key] = value }

    override fun has(key: String): Boolean = key in data

    class Builder(private val type: TriggerType, private val entity: LivingEntity) {
        private var target: LivingEntity? = null
        private val data = mutableMapOf<String, Any>()

        fun target(t: LivingEntity?) = apply { target = t }
        fun set(key: String, value: Any) = apply { data[key] = value }

        fun build() = TriggerContext(
            triggerType = type,
            entity = entity,
            target = target,
            timestamp = System.currentTimeMillis(),
            location = entity.location,
            data = data
        )
    }
}
