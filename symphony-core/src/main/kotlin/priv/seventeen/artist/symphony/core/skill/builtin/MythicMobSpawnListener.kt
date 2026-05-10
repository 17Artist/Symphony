package priv.seventeen.artist.symphony.core.skill.builtin

import io.lumine.mythic.bukkit.events.MythicMobDeathEvent
import io.lumine.mythic.bukkit.events.MythicMobSpawnEvent
import io.lumine.mythic.core.mobs.ActiveMob
import org.bukkit.Bukkit
import org.bukkit.configuration.ConfigurationSection
import org.bukkit.entity.LivingEntity
import org.bukkit.event.EventHandler
import org.bukkit.event.EventPriority
import org.bukkit.event.Listener
import org.bukkit.plugin.Plugin
import priv.seventeen.artist.blink.BlinkLog
import priv.seventeen.artist.symphony.api.affix.AffixInstance
import priv.seventeen.artist.symphony.api.trigger.TriggerType
import priv.seventeen.artist.symphony.core.attribute.AttributeCalculator
import priv.seventeen.artist.symphony.core.trigger.TriggerDispatcher
import java.lang.invoke.MethodHandle
import java.lang.invoke.MethodHandles
import java.lang.invoke.MethodType
import java.util.UUID
import java.util.concurrent.ConcurrentHashMap

/**
 * MythicMobs Mob spawn / death 监听器 — 读取 mob 配置中 `Symphony:` 段，
 * 把属性和词条挂到对应实体上。
 *
 * mob YAML 示例：
 * ```yaml
 * BanditBoss:
 *   Type: ZOMBIE
 *   Health: 200
 *   Symphony:
 *     attributes:
 *       physical_damage: 40
 *       physical_defense: 20
 *       critical_chance: 15%
 *     affixes:
 *       - bleed_on_hit
 *       - { id: fire_aura, level: 2 }
 * ```
 */
object MythicMobSpawnListener : Listener {

    private var registered = false
    private val mobAffixes = ConcurrentHashMap<UUID, List<AffixInstance>>()

    /**
     * 延迟初始化的 MethodHandle，用于从 MythicConfig 中获取底层 FileConfiguration。
     * 只在首次 spawn 时探测一次，后续直接复用，性能等同于直接调用。
     */
    private var configAccessor: MethodHandle? = null
    private var accessorResolved = false

    fun register(plugin: Plugin) {
        if (registered) return
        if (Bukkit.getPluginManager().getPlugin("MythicMobs") == null) return

        try {
            Bukkit.getPluginManager().registerEvents(this, plugin)
            registered = true
            BlinkLog.info("MythicMobs 怪物属性集成已启用")
        } catch (e: Exception) {
            BlinkLog.error("MythicMobs 监听挂载失败: ${e.message}")
        }
    }

    fun getMobAffixes(uuid: UUID): List<AffixInstance>? = mobAffixes[uuid]

    @EventHandler(priority = EventPriority.MONITOR)
    fun onSpawn(event: MythicMobSpawnEvent) {
        val entity = event.entity as? LivingEntity ?: return
        val mob = event.mob
        val mobId = mob.type.internalName

        val symphonySection = extractSymphonySection(mob) ?: return

        // 属性
        val attrMap = symphonySection.getConfigurationSection("attributes")?.let { section ->
            section.getKeys(false).associateWith { section.get(it) as Any }
        } ?: emptyMap()
        val modifiers = MythicMobDataStore.parseAttributes(attrMap, mobId)

        // 词条
        @Suppress("UNCHECKED_CAST")
        val affixRaw = (symphonySection.getList("affixes") as? List<Any>) ?: emptyList()
        val affixes = MythicMobDataStore.parseAffixes(affixRaw)

        if (modifiers.isEmpty() && affixes.isEmpty()) return

        MythicMobDataStore.put(entity.uniqueId, MythicMobDataStore.MobData(modifiers, affixes))
        if (affixes.isNotEmpty()) mobAffixes[entity.uniqueId] = affixes

        AttributeCalculator.markDirty(entity)
    }

    @EventHandler(priority = EventPriority.MONITOR)
    fun onDeath(event: MythicMobDeathEvent) {
        val entity = event.entity as? LivingEntity ?: return
        val uuid = entity.uniqueId

        // 桥接 ON_KILL 触发器 — 确保玩家击杀 MM 怪物时词条触发
        val killer = entity.killer
        if (killer != null) {
            TriggerDispatcher.dispatch(TriggerType.ON_KILL, killer) {
                target(entity)
                set("victim", entity.type.name)
                set("mythicMob", true)
                set("mobId", event.mob.type.internalName)
            }
        }

        MythicMobDataStore.remove(uuid)
        mobAffixes.remove(uuid)
    }

    private fun extractSymphonySection(mob: ActiveMob): ConfigurationSection? {
        val config = mob.type.config ?: return null
        val key = config.key
        val fc = getFileConfiguration(config) ?: return null
        return fc.getConfigurationSection("$key.Symphony")
            ?: fc.getConfigurationSection("Symphony")
    }

    /**
     * 通过缓存的 MethodHandle 从 MythicConfig 获取底层 FileConfiguration。
     * 首次调用时探测可用的方法/字段，之后每次调用开销等同于直接方法调用。
     */
    private fun getFileConfiguration(config: Any): ConfigurationSection? {
        if (!accessorResolved) {
            accessorResolved = true
            configAccessor = resolveAccessor(config.javaClass)
            if (configAccessor == null) {
                BlinkLog.warn("MythicConfig 无法找到 FileConfiguration 访问路径，Symphony 段解析不可用")
            }
        }
        val handle = configAccessor ?: return null
        return try {
            handle.invoke(config) as? ConfigurationSection
        } catch (_: Throwable) {
            null
        }
    }

    /**
     * 探测 MythicConfig 类上可用的 FileConfiguration 访问方式，返回 MethodHandle。
     * 优先级：getFileConfiguration() > getConfig() > 字段扫描
     */
    private fun resolveAccessor(clazz: Class<*>): MethodHandle? {
        val lookup = MethodHandles.lookup()

        // 尝试公开方法
        val methodNames = arrayOf("getFileConfiguration", "getConfig")
        for (name in methodNames) {
            try {
                val method = clazz.getMethod(name)
                if (ConfigurationSection::class.java.isAssignableFrom(method.returnType)) {
                    return lookup.unreflect(method)
                }
            } catch (_: NoSuchMethodException) {}
        }

        // 扫描字段（含父类）
        var current: Class<*>? = clazz
        while (current != null && current != Any::class.java) {
            for (field in current.declaredFields) {
                if (ConfigurationSection::class.java.isAssignableFrom(field.type)) {
                    field.isAccessible = true
                    return MethodHandles.lookup().unreflectGetter(field)
                }
            }
            current = current.superclass
        }

        return null
    }
}
