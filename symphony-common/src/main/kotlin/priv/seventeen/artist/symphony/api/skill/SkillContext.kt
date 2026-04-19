package priv.seventeen.artist.symphony.api.skill

import org.bukkit.Location
import org.bukkit.entity.LivingEntity
import priv.seventeen.artist.symphony.api.trigger.ITriggerContext

data class SkillContext(
    val caster: LivingEntity,
    val target: LivingEntity?,
    val targets: List<LivingEntity>,
    val origin: Location,
    val triggerContext: ITriggerContext?,
    val parameters: Map<String, Any> = emptyMap()
)
