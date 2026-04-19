package priv.seventeen.artist.symphony.api.trigger

import java.util.concurrent.ConcurrentHashMap

class TriggerType private constructor(val id: String) {
    companion object {
        private val registry = ConcurrentHashMap<String, TriggerType>()

        fun of(id: String): TriggerType = registry[id]
            ?: error("Unknown trigger type: $id")

        fun register(id: String): TriggerType {
            val type = TriggerType(id)
            registry[id] = type
            return type
        }

        fun custom(id: String) = register(id)
        fun getAll(): Collection<TriggerType> = registry.values

        val ON_ATTACK = register("ON_ATTACK")
        val ON_ATTACK_CRITICAL = register("ON_ATTACK_CRITICAL")
        val ON_KILL = register("ON_KILL")
        val ON_DAMAGE = register("ON_DAMAGE")
        val ON_MELEE_ATTACK = register("ON_MELEE_ATTACK")
        val ON_RANGED_ATTACK = register("ON_RANGED_ATTACK")
        val ON_DEFEND = register("ON_DEFEND")
        val ON_DAMAGED = register("ON_DAMAGED")
        val ON_BLOCK = register("ON_BLOCK")
        val ON_DODGE = register("ON_DODGE")
        val ON_DEATH = register("ON_DEATH")
        val ON_LOW_HEALTH = register("ON_LOW_HEALTH")
        val ON_MOVE = register("ON_MOVE")
        val ON_JUMP = register("ON_JUMP")
        val ON_SNEAK = register("ON_SNEAK")
        val ON_SPRINT = register("ON_SPRINT")
        val ON_INTERACT = register("ON_INTERACT")
        val ON_RIGHT_CLICK = register("ON_RIGHT_CLICK")
        val ON_LEFT_CLICK = register("ON_LEFT_CLICK")
        val ON_EQUIP = register("ON_EQUIP")
        val ON_UNEQUIP = register("ON_UNEQUIP")
        val ON_HOLD = register("ON_HOLD")
        val ON_CONSUME = register("ON_CONSUME")
        val ON_BREAK_ITEM = register("ON_BREAK_ITEM")
        val ON_TIMER = register("ON_TIMER")
        val ON_ENTER_COMBAT = register("ON_ENTER_COMBAT")
        val ON_LEAVE_COMBAT = register("ON_LEAVE_COMBAT")
        val ON_LEVEL_UP = register("ON_LEVEL_UP")
        val ON_RESPAWN = register("ON_RESPAWN")
        val ON_JOIN = register("ON_JOIN")
        val ON_QUIT = register("ON_QUIT")
        val ON_SKILL_CAST = register("ON_SKILL_CAST")
        val ON_SKILL_HIT = register("ON_SKILL_HIT")
        val ON_MANA_USE = register("ON_MANA_USE")
        val ON_MANA_FULL = register("ON_MANA_FULL")
    }

    override fun equals(other: Any?) = other is TriggerType && other.id == id
    override fun hashCode() = id.hashCode()
    override fun toString() = "TriggerType($id)"
}
