package priv.seventeen.artist.symphony.api.trigger

import org.bukkit.Location
import org.bukkit.entity.LivingEntity
import org.bukkit.inventory.ItemStack

interface ITriggerContext {
    val triggerType: TriggerType
    val entity: LivingEntity
    val target: LivingEntity?
    val timestamp: Long
    val location: Location

    fun <T> get(key: String): T?
    fun set(key: String, value: Any)
    fun has(key: String): Boolean

    val damage: Double? get() = get("damage")
    val item: ItemStack? get() = get("item")
    val isCritical: Boolean get() = get("isCritical") ?: false
}
