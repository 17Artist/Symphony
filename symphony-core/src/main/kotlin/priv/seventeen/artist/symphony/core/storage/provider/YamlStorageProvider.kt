package priv.seventeen.artist.symphony.core.storage.provider

import org.bukkit.configuration.file.YamlConfiguration
import priv.seventeen.artist.blink.BlinkLog
import priv.seventeen.artist.symphony.core.data.PersistentData
import priv.seventeen.artist.symphony.core.data.RuneData
import priv.seventeen.artist.symphony.core.data.SavedBuffData
import priv.seventeen.artist.symphony.core.data.SavedTempAffixData
import priv.seventeen.artist.symphony.core.data.VirtualSetData
import priv.seventeen.artist.symphony.core.storage.StorageProvider
import java.io.File
import java.util.UUID

class YamlStorageProvider(private val dataFolder: File) : StorageProvider {
    private lateinit var playersDir: File

    override fun initialize() {
        playersDir = File(dataFolder, "data/players")
        if (!playersDir.exists()) playersDir.mkdirs()
        BlinkLog.info("YAML 存储已初始化: ${playersDir.absolutePath}")
    }

    override fun shutdown() {
        BlinkLog.info("YAML 存储已关闭")
    }

    override fun loadPlayer(uuid: UUID): PersistentData? {
        val file = File(playersDir, "$uuid.yml")
        if (!file.exists()) return null

        val config = YamlConfiguration.loadConfiguration(file)
        val data = PersistentData()

        data.level = config.getInt("level", 1)
        data.exp = config.getLong("exp", 0)
        data.freePoints = config.getInt("free_points", 0)

        // 加载分配的属性点
        config.getConfigurationSection("allocated_points")?.let { section ->
            for (key in section.getKeys(false)) {
                data.allocatedPoints[key] = section.getInt(key)
            }
        }

        // 加载符文
        config.getConfigurationSection("runes")?.let { section ->
            for (key in section.getKeys(false)) {
                val runeSection = section.getConfigurationSection(key) ?: continue
                data.runes[key] = RuneData(
                    runeId = key,
                    level = runeSection.getInt("level", 1),
                    active = runeSection.getBoolean("active", false)
                )
            }
        }

        // 加载符文碎片
        config.getConfigurationSection("rune_fragments")?.let { section ->
            for (key in section.getKeys(false)) {
                data.runeFragments[key] = section.getInt(key)
            }
        }

        // 加载天赋
        data.unlockedTalents.addAll(config.getStringList("unlocked_talents"))

        config.getConfigurationSection("selected_talents")?.let { section ->
            for (key in section.getKeys(false)) {
                data.selectedTalents[key] = section.getString(key) ?: continue
            }
        }

        // 加载统计
        config.getConfigurationSection("statistics")?.let { section ->
            data.statistics.totalDamageDealt = section.getDouble("total_damage_dealt")
            data.statistics.totalDamageTaken = section.getDouble("total_damage_taken")
            data.statistics.totalKills = section.getInt("total_kills")
            data.statistics.totalDeaths = section.getInt("total_deaths")
            data.statistics.highestCombo = section.getInt("highest_combo")
            data.statistics.totalReactionsTriggered = section.getInt("total_reactions_triggered")
            data.statistics.totalAffixesTriggered = section.getInt("total_affixes_triggered")
            data.statistics.highestDamageDealt = section.getDouble("highest_damage_dealt")
        }

        // 加载偏好
        config.getConfigurationSection("preferences")?.let { section ->
            data.preferences.showDamageNumbers = section.getBoolean("show_damage_numbers", true)
            data.preferences.showStatusBars = section.getBoolean("show_status_bars", true)
            data.preferences.showEnvironmentIndicator = section.getBoolean("show_environment_indicator", true)
            data.preferences.combatLogEnabled = section.getBoolean("combat_log_enabled", false)
        }

        return data
    }

    override fun savePlayer(uuid: UUID, data: PersistentData) {
        val file = File(playersDir, "$uuid.yml")
        val config = YamlConfiguration()

        config.set("uuid", uuid.toString())
        config.set("level", data.level)
        config.set("exp", data.exp)
        config.set("free_points", data.freePoints)

        data.allocatedPoints.forEach { (k, v) -> config.set("allocated_points.$k", v) }
        data.runes.forEach { (k, v) ->
            config.set("runes.$k.level", v.level)
            config.set("runes.$k.active", v.active)
        }
        data.runeFragments.forEach { (k, v) -> config.set("rune_fragments.$k", v) }
        config.set("unlocked_talents", data.unlockedTalents.toList())
        data.selectedTalents.forEach { (k, v) -> config.set("selected_talents.$k", v) }

        config.set("statistics.total_damage_dealt", data.statistics.totalDamageDealt)
        config.set("statistics.total_damage_taken", data.statistics.totalDamageTaken)
        config.set("statistics.total_kills", data.statistics.totalKills)
        config.set("statistics.total_deaths", data.statistics.totalDeaths)
        config.set("statistics.highest_combo", data.statistics.highestCombo)
        config.set("statistics.total_reactions_triggered", data.statistics.totalReactionsTriggered)
        config.set("statistics.total_affixes_triggered", data.statistics.totalAffixesTriggered)
        config.set("statistics.highest_damage_dealt", data.statistics.highestDamageDealt)

        config.set("preferences.show_damage_numbers", data.preferences.showDamageNumbers)
        config.set("preferences.show_status_bars", data.preferences.showStatusBars)
        config.set("preferences.show_environment_indicator", data.preferences.showEnvironmentIndicator)
        config.set("preferences.combat_log_enabled", data.preferences.combatLogEnabled)

        // 原子写入：先写临时文件，再重命名
        val tempFile = File(file.parentFile, "${file.name}.tmp")
        config.save(tempFile)
        if (file.exists()) file.delete()
        tempFile.renameTo(file)
    }

    override fun saveAll(dataMap: Map<UUID, PersistentData>) {
        dataMap.forEach { (uuid, data) -> savePlayer(uuid, data) }
    }

    override fun deletePlayer(uuid: UUID) {
        File(playersDir, "$uuid.yml").delete()
    }

    override fun exists(uuid: UUID): Boolean {
        return File(playersDir, "$uuid.yml").exists()
    }
}
