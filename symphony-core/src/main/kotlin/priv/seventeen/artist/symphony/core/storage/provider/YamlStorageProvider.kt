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
            data.statistics.totalPlayTime = section.getLong("total_play_time")
        }

        // 加载偏好
        config.getConfigurationSection("preferences")?.let { section ->
            data.preferences.showDamageNumbers = section.getBoolean("show_damage_numbers", true)
            data.preferences.showStatusBars = section.getBoolean("show_status_bars", true)
            data.preferences.showEnvironmentIndicator = section.getBoolean("show_environment_indicator", true)
            data.preferences.showResonanceProgress = section.getBoolean("show_resonance_progress", true)
            data.preferences.combatLogEnabled = section.getBoolean("combat_log_enabled", false)
        }

        // 加载保存的 Buff
        config.getMapList("saved_buffs").forEach { map ->
            @Suppress("UNCHECKED_CAST")
            val m = map as Map<String, Any>
            data.savedBuffs.add(SavedBuffData(
                id = m["id"]?.toString() ?: return@forEach,
                attribute = m["attribute"]?.toString() ?: return@forEach,
                operation = m["operation"]?.toString() ?: "FLAT",
                value = (m["value"] as? Number)?.toDouble() ?: 0.0,
                expireTime = (m["expire_time"] as? Number)?.toLong() ?: 0L,
                source = m["source"]?.toString() ?: "",
                remainingDuration = (m["remaining_duration"] as? Number)?.toLong() ?: 0L
            ))
        }

        // 加载保存的临时词条
        config.getMapList("saved_temp_affixes").forEach { map ->
            @Suppress("UNCHECKED_CAST")
            val m = map as Map<String, Any>
            @Suppress("UNCHECKED_CAST")
            data.savedTempAffixes.add(SavedTempAffixData(
                uuid = m["uuid"]?.toString() ?: return@forEach,
                affixId = m["affix_id"]?.toString() ?: return@forEach,
                level = (m["level"] as? Number)?.toInt() ?: 1,
                params = (m["params"] as? Map<String, Any>) ?: emptyMap(),
                source = m["source"]?.toString() ?: "",
                remainingDuration = (m["remaining_duration"] as? Number)?.toLong() ?: 0L
            ))
        }

        // 加载虚拟套装
        config.getConfigurationSection("virtual_sets")?.let { section ->
            for (key in section.getKeys(false)) {
                val sub = section.getConfigurationSection(key) ?: continue
                data.virtualSets[key] = VirtualSetData(
                    setId = key,
                    pieces = sub.getInt("pieces", 0),
                    source = sub.getString("source") ?: "",
                    expireTime = sub.getLong("expire_time", 0L)
                )
            }
        }

        // 加载共鸣覆盖
        config.getConfigurationSection("resonance_overrides")?.let { section ->
            for (key in section.getKeys(false)) {
                data.resonanceOverrides[key] = section.getBoolean(key)
            }
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
        config.set("statistics.total_play_time", data.statistics.totalPlayTime)

        config.set("preferences.show_damage_numbers", data.preferences.showDamageNumbers)
        config.set("preferences.show_status_bars", data.preferences.showStatusBars)
        config.set("preferences.show_environment_indicator", data.preferences.showEnvironmentIndicator)
        config.set("preferences.show_resonance_progress", data.preferences.showResonanceProgress)
        config.set("preferences.combat_log_enabled", data.preferences.combatLogEnabled)

        // 保存 Buff
        val buffList = data.savedBuffs.map { buff ->
            mapOf(
                "id" to buff.id,
                "attribute" to buff.attribute,
                "operation" to buff.operation,
                "value" to buff.value,
                "expire_time" to buff.expireTime,
                "source" to buff.source,
                "remaining_duration" to buff.remainingDuration
            )
        }
        config.set("saved_buffs", buffList)

        // 保存临时词条
        val affixList = data.savedTempAffixes.map { affix ->
            mapOf(
                "uuid" to affix.uuid,
                "affix_id" to affix.affixId,
                "level" to affix.level,
                "params" to affix.params,
                "source" to affix.source,
                "remaining_duration" to affix.remainingDuration
            )
        }
        config.set("saved_temp_affixes", affixList)

        // 保存虚拟套装
        data.virtualSets.forEach { (k, v) ->
            config.set("virtual_sets.$k.pieces", v.pieces)
            config.set("virtual_sets.$k.source", v.source)
            config.set("virtual_sets.$k.expire_time", v.expireTime)
        }

        // 保存共鸣覆盖
        data.resonanceOverrides.forEach { (k, v) -> config.set("resonance_overrides.$k", v) }

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
