package priv.seventeen.artist.symphony.core.trigger

import priv.seventeen.artist.symphony.api.trigger.ICustomCondition
import java.util.concurrent.ConcurrentHashMap

/** 自定义条件类型注册表。key 统一大写。 */
object CustomConditionRegistry {
    private val map = ConcurrentHashMap<String, ICustomCondition>()
    fun register(type: String, impl: ICustomCondition) { map[type.uppercase()] = impl }
    fun unregister(type: String) { map.remove(type.uppercase()) }
    fun get(type: String): ICustomCondition? = map[type.uppercase()]
    fun ids(): Set<String> = map.keys.toSet()
    fun clear() = map.clear()
}
