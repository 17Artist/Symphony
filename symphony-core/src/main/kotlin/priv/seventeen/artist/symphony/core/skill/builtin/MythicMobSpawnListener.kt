package priv.seventeen.artist.symphony.core.skill.builtin

import org.bukkit.Bukkit
import org.bukkit.configuration.ConfigurationSection
import org.bukkit.entity.LivingEntity
import org.bukkit.event.EventPriority
import org.bukkit.event.Listener
import org.bukkit.plugin.EventExecutor
import org.bukkit.plugin.Plugin
import priv.seventeen.artist.blink.BlinkLog
import priv.seventeen.artist.symphony.api.affix.AffixInstance
import priv.seventeen.artist.symphony.core.affix.AffixManagerImpl
import priv.seventeen.artist.symphony.core.attribute.AttributeCalculator
import java.util.UUID

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
 *
 * 全反射，MM 缺失时整体静默降级。
 */
object MythicMobSpawnListener {

    private var registered = false
    // mob 实体 → 虚拟装备（用于保存 affixes 供 AffixPassiveProvider 读取）
    private val mobAffixes = java.util.concurrent.ConcurrentHashMap<UUID, List<AffixInstance>>()

    fun register(plugin: Plugin) {
        if (registered) return
        if (Bukkit.getPluginManager().getPlugin("MythicMobs") == null) return

        val spawnClass = runCatching {
            Class.forName("io.lumine.mythic.bukkit.events.MythicMobSpawnEvent")
        }.getOrNull()

        val deathClass = runCatching {
            Class.forName("io.lumine.mythic.bukkit.events.MythicMobDeathEvent")
        }.getOrNull()

        if (spawnClass == null) {
            BlinkLog.warn("MythicMobs 已安装但 MythicMobSpawnEvent 不可用，mob 属性集成跳过")
            return
        }

        val listener = object : Listener {}

        try {
            @Suppress("UNCHECKED_CAST")
            Bukkit.getPluginManager().registerEvent(
                spawnClass as Class<out org.bukkit.event.Event>,
                listener, EventPriority.MONITOR,
                EventExecutor { _, e -> handleSpawn(e) }, plugin
            )
            if (deathClass != null) {
                @Suppress("UNCHECKED_CAST")
                Bukkit.getPluginManager().registerEvent(
                    deathClass as Class<out org.bukkit.event.Event>,
                    listener, EventPriority.MONITOR,
                    EventExecutor { _, e -> handleDeath(e) }, plugin
                )
            }
            registered = true
            BlinkLog.info("MythicMobs 怪物属性集成已启用")
        } catch (e: Exception) {
            BlinkLog.error("MythicMobs 监听挂载失败: ${e.message}")
        }
    }

    fun getMobAffixes(uuid: UUID): List<AffixInstance>? = mobAffixes[uuid]

    //  内部：事件处理 
    private fun handleSpawn(event: Any) {
        val entity = runCatching {
            val mob = event.javaClass.getMethod("getMob").invoke(event)
            val inner = mob.javaClass.getMethod("getEntity").invoke(mob)
            inner.javaClass.getMethod("getBukkitEntity").invoke(inner) as? LivingEntity
        }.getOrNull() ?: return

        val mobId = runCatching {
            val mob = event.javaClass.getMethod("getMob").invoke(event)
            val type = mob.javaClass.getMethod("getType").invoke(mob)
            type.javaClass.getMethod("getInternalName").invoke(type) as? String
        }.getOrNull() ?: "unknown"

        val symphonySection = extractSymphonySection(event) ?: return

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

    private fun handleDeath(event: Any) {
        val uuid = runCatching {
            val mob = event.javaClass.getMethod("getMob").invoke(event)
            val inner = mob.javaClass.getMethod("getEntity").invoke(mob)
            val bukkit = inner.javaClass.getMethod("getBukkitEntity").invoke(inner) as? LivingEntity
            bukkit?.uniqueId
        }.getOrNull() ?: return
        MythicMobDataStore.remove(uuid)
        mobAffixes.remove(uuid)
    }

    /**
     * 从 MM 事件对象挖出 mob 配置的 `Symphony:` 段，并归一化为 Bukkit [ConfigurationSection]。
     * 兼容 MythicConfig / 纯 YAML / ConfigurationSection 几种路径。
     */
    private fun extractSymphonySection(event: Any): ConfigurationSection? {
        val mob = runCatching { event.javaClass.getMethod("getMob").invoke(event) }.getOrNull() ?: return null
        val type = runCatching { mob.javaClass.getMethod("getType").invoke(mob) }.getOrNull() ?: return null
        val config = runCatching { type.javaClass.getMethod("getConfig").invoke(type) }.getOrNull() ?: return null

        // 路径 1：config 本身就是 ConfigurationSection
        if (config is ConfigurationSection) {
            return config.getConfigurationSection("Symphony")
        }
        // 路径 2：MythicConfig 封装，常见方法 getConfig() / getFileConfiguration() / toSection()
        val fc = runCatching { config.javaClass.getMethod("getFileConfiguration").invoke(config) as? ConfigurationSection }.getOrNull()
            ?: runCatching { config.javaClass.getMethod("toSection").invoke(config) as? ConfigurationSection }.getOrNull()
            ?: runCatching { config.javaClass.getMethod("getConfig").invoke(config) as? ConfigurationSection }.getOrNull()
        if (fc != null) return fc.getConfigurationSection("Symphony")

        // 路径 3：config 是 map-like
        if (config is Map<*, *>) {
            val s = config["Symphony"]
            if (s is ConfigurationSection) return s
        }

        BlinkLog.detail("MythicMob 配置未识别类型: ${config.javaClass.name}，跳过 Symphony 段解析")
        return null
    }
}
