package priv.seventeen.artist.symphony.api.event

import org.bukkit.entity.LivingEntity
import org.bukkit.event.Cancellable
import org.bukkit.event.Event
import org.bukkit.event.HandlerList
import priv.seventeen.artist.symphony.api.skill.SkillContext

class SkillCastEvent(
    val caster: LivingEntity,
    val providerId: String,
    val skillId: String,
    val level: Int,
    val context: SkillContext
) : Event(), Cancellable {
    private var cancelled = false
    override fun isCancelled() = cancelled
    override fun setCancelled(cancel: Boolean) { cancelled = cancel }
    override fun getHandlers() = handlerList
    companion object {
        @JvmStatic val handlerList = HandlerList()
    }
}
