package priv.seventeen.artist.symphony.core.storage

import org.bukkit.Bukkit
import org.bukkit.plugin.Plugin
import priv.seventeen.artist.symphony.api.attribute.Operation
import priv.seventeen.artist.symphony.core.attribute.AttributeCache
import priv.seventeen.artist.symphony.core.data.*
import java.util.UUID
import java.util.concurrent.ConcurrentHashMap

object PlayerDataManager {
    private val cache = ConcurrentHashMap<UUID, SymphonyPlayerData>()
    private val dirtySet = ConcurrentHashMap.newKeySet<UUID>()
    lateinit var storageProvider: StorageProvider
    lateinit var plugin: Plugin

    fun initialize(provider: StorageProvider, plugin: Plugin) {
        this.storageProvider = provider
        this.plugin = plugin
        provider.initialize()
    }

    fun getData(uuid: UUID): SymphonyPlayerData? {
        return cache[uuid]
    }

    fun getOrCreate(uuid: UUID): SymphonyPlayerData {
        return cache.getOrPut(uuid) {
            val persistent = storageProvider.loadPlayer(uuid) ?: PersistentData()
            SymphonyPlayerData(uuid).also { copyPersistent(it.persistent, persistent); restoreRuntime(it) }
        }
    }

    fun markDirty(uuid: UUID) {
        dirtySet.add(uuid)
    }

    fun saveAllDirty() {
        val snapshot = mutableMapOf<UUID, PersistentData>()
        // 收集 dirtySet 中的
        val iter = dirtySet.iterator()
        while (iter.hasNext()) {
            val uuid = iter.next()
            iter.remove()
            cache[uuid]?.let { snapshot[uuid] = deepCopyPersistent(it.persistent) }
        }
        // 也收集 data.dirty=true 但不在 dirtySet 中的（双轨统一）
        for ((uuid, data) in cache) {
            if (data.dirty && uuid !in snapshot) {
                snapshot[uuid] = deepCopyPersistent(data.persistent)
            }
            data.dirty = false
        }
        if (snapshot.isNotEmpty()) {
            Bukkit.getScheduler().runTaskAsynchronously(plugin, Runnable {
                storageProvider.saveAll(snapshot)
            })
        }
    }

    private fun deepCopyPersistent(source: PersistentData): PersistentData {
        val copy = PersistentData()
        copy.level = source.level
        copy.exp = source.exp
        copy.freePoints = source.freePoints
        copy.runes.putAll(source.runes.mapValues { it.value.copy() })
        copy.runeFragments.putAll(source.runeFragments)
        copy.unlockedTalents.addAll(source.unlockedTalents)
        copy.selectedTalents.putAll(source.selectedTalents)
        copy.virtualSets.putAll(source.virtualSets.mapValues { it.value.copy() })
        copy.resonanceOverrides.putAll(source.resonanceOverrides)
        copy.allocatedPoints.putAll(source.allocatedPoints)
        copy.statistics.totalDamageDealt = source.statistics.totalDamageDealt
        copy.statistics.totalDamageTaken = source.statistics.totalDamageTaken
        copy.statistics.totalKills = source.statistics.totalKills
        copy.statistics.totalDeaths = source.statistics.totalDeaths
        copy.statistics.highestCombo = source.statistics.highestCombo
        copy.statistics.totalPlayTime = source.statistics.totalPlayTime
        copy.statistics.totalReactionsTriggered = source.statistics.totalReactionsTriggered
        copy.statistics.totalAffixesTriggered = source.statistics.totalAffixesTriggered
        copy.statistics.highestDamageDealt = source.statistics.highestDamageDealt
        copy.preferences.showDamageNumbers = source.preferences.showDamageNumbers
        copy.preferences.showStatusBars = source.preferences.showStatusBars
        copy.preferences.showEnvironmentIndicator = source.preferences.showEnvironmentIndicator
        copy.preferences.showResonanceProgress = source.preferences.showResonanceProgress
        copy.preferences.combatLogEnabled = source.preferences.combatLogEnabled
        copy.savedBuffs.addAll(source.savedBuffs.map { it.copy() })
        copy.savedTempAffixes.addAll(source.savedTempAffixes.map { it.copy() })
        return copy
    }

    fun onPlayerJoin(uuid: UUID) {
        Bukkit.getScheduler().runTaskAsynchronously(plugin, Runnable {
            val persistent = storageProvider.loadPlayer(uuid) ?: PersistentData()
            Bukkit.getScheduler().runTask(plugin, Runnable {
                val data = SymphonyPlayerData(uuid)
                copyPersistent(data.persistent, persistent)
                restoreRuntime(data)
                cache[uuid] = data
                AttributeCache.markDirty(uuid)
            })
        })
    }

    fun onPlayerQuit(uuid: UUID) {
        val data = cache.remove(uuid) ?: return
        dirtySet.remove(uuid)
        persistRuntime(data)
        Bukkit.getScheduler().runTaskAsynchronously(plugin, Runnable {
            storageProvider.savePlayer(uuid, data.persistent)
        })
        AttributeCache.invalidate(uuid)
    }

    fun shutdown() {
        // 保存所有在线玩家数据
        for ((uuid, data) in cache) {
            persistRuntime(data)
            storageProvider.savePlayer(uuid, data.persistent)
        }
        cache.clear()
        dirtySet.clear()
        storageProvider.shutdown()
    }

    private fun copyPersistent(target: PersistentData, source: PersistentData) {
        target.level = source.level
        target.exp = source.exp
        target.freePoints = source.freePoints
        target.runes.putAll(source.runes)
        target.runeFragments.putAll(source.runeFragments)
        target.unlockedTalents.addAll(source.unlockedTalents)
        target.selectedTalents.putAll(source.selectedTalents)
        target.virtualSets.putAll(source.virtualSets)
        target.resonanceOverrides.putAll(source.resonanceOverrides)
        target.allocatedPoints.putAll(source.allocatedPoints)
        // Statistics
        target.statistics.totalDamageDealt = source.statistics.totalDamageDealt
        target.statistics.totalDamageTaken = source.statistics.totalDamageTaken
        target.statistics.totalKills = source.statistics.totalKills
        target.statistics.totalDeaths = source.statistics.totalDeaths
        target.statistics.highestCombo = source.statistics.highestCombo
        target.statistics.totalPlayTime = source.statistics.totalPlayTime
        target.statistics.totalReactionsTriggered = source.statistics.totalReactionsTriggered
        target.statistics.totalAffixesTriggered = source.statistics.totalAffixesTriggered
        target.statistics.highestDamageDealt = source.statistics.highestDamageDealt
        // Preferences
        target.preferences.showDamageNumbers = source.preferences.showDamageNumbers
        target.preferences.showStatusBars = source.preferences.showStatusBars
        target.preferences.showEnvironmentIndicator = source.preferences.showEnvironmentIndicator
        target.preferences.showResonanceProgress = source.preferences.showResonanceProgress
        target.preferences.combatLogEnabled = source.preferences.combatLogEnabled
        // Saved runtime data
        target.savedBuffs.addAll(source.savedBuffs)
        target.savedTempAffixes.addAll(source.savedTempAffixes)
    }

    private fun restoreRuntime(data: SymphonyPlayerData) {
        val now = System.currentTimeMillis()
        for (saved in data.persistent.savedBuffs) {
            // 永久 Buff（remainingDuration 极大或 expireTime=-1）直接设为 -1
            val expireTime = if (saved.remainingDuration >= Long.MAX_VALUE / 2 || saved.expireTime == -1L) {
                -1L
            } else {
                now + saved.remainingDuration
            }
            if (expireTime == -1L || expireTime > now) {
                data.runtime.activeBuffs.add(ActiveBuff(
                    id = saved.id, attribute = saved.attribute,
                    operation = Operation.valueOf(saved.operation),
                    value = saved.value, expireTime = expireTime,
                    source = saved.source
                ))
            }
        }
        data.persistent.savedBuffs.clear()

        for (saved in data.persistent.savedTempAffixes) {
            val expireTime = if (saved.remainingDuration >= Long.MAX_VALUE / 2) {
                -1L
            } else {
                now + saved.remainingDuration
            }
            if (expireTime == -1L || expireTime > now) {
                data.runtime.tempAffixes.add(TempAffix(
                    uuid = UUID.fromString(saved.uuid), affixId = saved.affixId,
                    level = saved.level, params = saved.params,
                    source = saved.source, expireTime = expireTime,
                    slot = TempAffixSlot.PLAYER
                ))
            }
        }
        data.persistent.savedTempAffixes.clear()
    }

    private fun persistRuntime(data: SymphonyPlayerData) {
        val now = System.currentTimeMillis()
        data.persistent.savedBuffs.clear()
        for (buff in data.runtime.activeBuffs) {
            val remaining = buff.remainingMs()
            if (remaining > 30000) {
                data.persistent.savedBuffs.add(SavedBuffData(
                    id = buff.id, attribute = buff.attribute,
                    operation = buff.operation.name, value = buff.value,
                    expireTime = buff.expireTime, source = buff.source,
                    remainingDuration = remaining
                ))
            }
        }

        data.persistent.savedTempAffixes.clear()
        for (affix in data.runtime.tempAffixes) {
            val remaining = if (affix.expireTime == -1L) Long.MAX_VALUE else affix.expireTime - now
            if (remaining > 60000) {
                data.persistent.savedTempAffixes.add(SavedTempAffixData(
                    uuid = affix.uuid.toString(), affixId = affix.affixId,
                    level = affix.level, params = affix.params,
                    source = affix.source, remainingDuration = remaining
                ))
            }
        }
    }
}
