package priv.seventeen.artist.symphony.core.skill.builtin

import org.bukkit.Bukkit
import org.bukkit.entity.LivingEntity
import org.bukkit.entity.Player
import org.bukkit.event.EventPriority
import org.bukkit.event.Listener
import org.bukkit.plugin.EventExecutor
import org.bukkit.plugin.Plugin
import priv.seventeen.artist.blink.BlinkLog
import priv.seventeen.artist.symphony.api.attribute.Operation
import priv.seventeen.artist.symphony.core.attribute.AttributeCalculator
import priv.seventeen.artist.symphony.core.data.ActiveBuff
import priv.seventeen.artist.symphony.core.storage.PlayerDataManager
import java.lang.reflect.Proxy
import java.util.UUID

/**
 * MythicMobs Mechanic 注册器。
 *
 * 通过反射 + 动态代理实现，无需 MM 的编译期依赖。当 MM 存在时，监听
 * `MythicMechanicLoadEvent` 并为 Symphony 自定义 mechanic 返回代理实现的
 * `SkillMechanic` 实例；MM 缺失或 API 不兼容时整体静默降级。
 *
 * 当前注册的 mechanic（与 MM 4.x~5.x 兼容）：
 * - `symphony_damage{amount=10;attribute=physical_damage}` — 走 Symphony 伤害事件
 * - `symphony_heal{amount=5}`
 * - `symphony_buff{id=physical_damage;op=flat;value=10;duration=100}` — 临时 Buff
 */
object MythicMobsMechanicRegistrar {

    private var registered = false

    private val mechanicNames = setOf(
        "symphony_damage", "symphony_heal", "symphony_buff"
    )

    fun register(plugin: Plugin) {
        if (registered) return
        if (Bukkit.getPluginManager().getPlugin("MythicMobs") == null) return

        val eventClass = runCatching {
            Class.forName("io.lumine.mythic.bukkit.events.MythicMechanicLoadEvent")
        }.getOrNull() ?: run {
            BlinkLog.warn("MythicMobs 已安装但 MythicMechanicLoadEvent 不可用，mechanic 注册跳过")
            return
        }

        val listener = object : Listener {}
        val executor = EventExecutor { _, event -> handleLoad(event) }

        try {
            @Suppress("UNCHECKED_CAST")
            Bukkit.getPluginManager().registerEvent(
                eventClass as Class<out org.bukkit.event.Event>,
                listener,
                EventPriority.NORMAL,
                executor,
                plugin
            )
            registered = true
            BlinkLog.info("MythicMobs mechanic 注册已挂载: ${mechanicNames.joinToString()}")
        } catch (e: Exception) {
            BlinkLog.error("MythicMobs mechanic 注册挂载失败: ${e.message}")
        }
    }

    private fun handleLoad(event: Any) {
        val name = runCatching {
            event.javaClass.getMethod("getMechanicName").invoke(event) as? String
        }.getOrNull()?.lowercase() ?: return
        if (name !in mechanicNames) return

        val config = runCatching {
            event.javaClass.getMethod("getConfig").invoke(event)
        }.getOrNull() ?: return

        val registerMethod = event.javaClass.methods.firstOrNull {
            it.name == "register" && it.parameterCount == 1
        } ?: return

        val mechanic = buildProxy(name, config) ?: return
        try {
            registerMethod.invoke(event, mechanic)
        } catch (e: Exception) {
            BlinkLog.warn("注册 MM mechanic $name 失败: ${e.message}")
        }
    }

    /**
     * 为 MM 的 `SkillMechanic` 接口创建动态代理。
     * 需要识别的接口方法：`cast(SkillMetadata): SkillResult`。
     */
    private fun buildProxy(name: String, config: Any): Any? {
        val skillMechanic = runCatching {
            Class.forName("io.lumine.mythic.api.skills.SkillMechanic")
        }.getOrNull() ?: return null

        val targetedEntity = runCatching {
            Class.forName("io.lumine.mythic.api.skills.ITargetedEntitySkill")
        }.getOrNull()

        val skillResultClass = runCatching {
            Class.forName("io.lumine.mythic.api.skills.SkillResult")
        }.getOrNull() ?: return null
        val successResult = skillResultClass.getField("SUCCESS").get(null)
        val errorResult = runCatching { skillResultClass.getField("ERROR").get(null) }.getOrNull() ?: successResult

        // 读配置项
        val readStr = { key: String, default: String ->
            runCatching {
                config.javaClass.getMethod("getString", String::class.java, String::class.java)
                    .invoke(config, key, default) as String
            }.getOrDefault(default)
        }
        val readDouble = { key: String, default: Double ->
            runCatching {
                config.javaClass.getMethod("getDouble", String::class.java, Double::class.javaPrimitiveType!!)
                    .invoke(config, key, default) as Double
            }.getOrDefault(default)
        }
        val readInt = { key: String, default: Int ->
            runCatching {
                config.javaClass.getMethod("getInteger", String::class.java, Int::class.javaPrimitiveType!!)
                    .invoke(config, key, default) as Int
            }.getOrDefault(default)
        }

        val handler = java.lang.reflect.InvocationHandler { _, method, args ->
            when (method.name) {
                "cast", "castAtEntity" -> {
                    val meta = args?.getOrNull(0)
                    val caster = extractCaster(meta)
                    val target: LivingEntity? = args?.getOrNull(1)?.let { extractEntity(it) } ?: caster
                    try {
                        when (name) {
                            "symphony_damage" -> {
                                val amount = readDouble("amount", 1.0)
                                val victim = target ?: return@InvocationHandler errorResult
                                victim.damage(amount, caster)
                            }
                            "symphony_heal" -> {
                                val amount = readDouble("amount", 1.0)
                                val victim = (target ?: caster) ?: return@InvocationHandler errorResult
                                val max = victim.getAttribute(org.bukkit.attribute.Attribute.GENERIC_MAX_HEALTH)?.value ?: victim.health
                                victim.health = (victim.health + amount).coerceIn(0.0, max)
                            }
                            "symphony_buff" -> {
                                val attrId = readStr("id", "")
                                if (attrId.isEmpty()) return@InvocationHandler errorResult
                                val op = if (readStr("op", "flat").equals("percent", true)) Operation.PERCENT else Operation.FLAT
                                val value = readDouble("value", 0.0)
                                val duration = readInt("duration", 100)
                                val victim = (target ?: caster) as? Player ?: return@InvocationHandler errorResult
                                val data = PlayerDataManager.getOrCreate(victim.uniqueId)
                                data.runtime.activeBuffs.add(ActiveBuff(
                                    id = "mm:$attrId:${UUID.randomUUID()}",
                                    attribute = attrId,
                                    operation = op,
                                    value = value,
                                    expireTime = System.currentTimeMillis() + duration * 50L,
                                    source = "mythic"
                                ))
                                AttributeCalculator.markDirty(victim)
                            }
                        }
                        successResult
                    } catch (e: Exception) {
                        BlinkLog.warn("MM mechanic $name 执行异常: ${e.message}")
                        errorResult
                    }
                }
                "isAsyncSafe" -> false
                "getThreadSafetyLevel" -> 0
                "hashCode" -> System.identityHashCode(this)
                "equals" -> args?.getOrNull(0) === this
                "toString" -> "SymphonyProxy($name)"
                else -> null
            }
        }

        val interfaces = mutableListOf<Class<*>>(skillMechanic)
        targetedEntity?.let { interfaces += it }

        return Proxy.newProxyInstance(
            MythicMobsMechanicRegistrar::class.java.classLoader,
            interfaces.toTypedArray(),
            handler
        )
    }

    private fun extractCaster(meta: Any?): LivingEntity? {
        if (meta == null) return null
        return try {
            val casterObj = meta.javaClass.getMethod("getCaster").invoke(meta)
            extractEntity(casterObj)
        } catch (_: Exception) { null }
    }

    private fun extractEntity(obj: Any?): LivingEntity? {
        if (obj == null) return null
        // AbstractEntity.getBukkitEntity() 或 caster.getEntity().getBukkitEntity()
        return try {
            val bukkit = runCatching {
                obj.javaClass.getMethod("getBukkitEntity").invoke(obj)
            }.getOrNull() ?: runCatching {
                val inner = obj.javaClass.getMethod("getEntity").invoke(obj)
                inner.javaClass.getMethod("getBukkitEntity").invoke(inner)
            }.getOrNull()
            bukkit as? LivingEntity
        } catch (_: Exception) { null }
    }
}
