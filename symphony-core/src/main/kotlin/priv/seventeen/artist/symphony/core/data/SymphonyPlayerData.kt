package priv.seventeen.artist.symphony.core.data

import java.util.UUID

class SymphonyPlayerData(val uuid: UUID) {
    val persistent = PersistentData()
    val runtime = RuntimeData()
    @Volatile var dirty: Boolean = false
}

class PersistentData {
    var level: Int = 1
    var exp: Long = 0
    val runes: MutableMap<String, RuneData> = mutableMapOf()
    val runeFragments: MutableMap<String, Int> = mutableMapOf()
    val unlockedTalents: MutableSet<String> = mutableSetOf()
    val selectedTalents: MutableMap<String, String> = mutableMapOf()
    val virtualSets: MutableMap<String, VirtualSetData> = mutableMapOf()
    val resonanceOverrides: MutableMap<String, Boolean> = mutableMapOf()
    val allocatedPoints: MutableMap<String, Int> = mutableMapOf()
    var freePoints: Int = 0
    val statistics: PlayerStatistics = PlayerStatistics()
    val preferences: PlayerPreferences = PlayerPreferences()
    val savedBuffs: MutableList<SavedBuffData> = mutableListOf()
    val savedTempAffixes: MutableList<SavedTempAffixData> = mutableListOf()
}

data class RuneData(
    val runeId: String,
    var level: Int,
    var active: Boolean
)

data class VirtualSetData(
    val setId: String,
    var pieces: Int,
    val source: String,
    val expireTime: Long
)

data class PlayerStatistics(
    var totalDamageDealt: Double = 0.0,
    var totalDamageTaken: Double = 0.0,
    var totalKills: Int = 0,
    var totalDeaths: Int = 0,
    var highestCombo: Int = 0,
    var totalPlayTime: Long = 0,
    var totalReactionsTriggered: Int = 0,
    var totalAffixesTriggered: Int = 0,
    var highestDamageDealt: Double = 0.0
)

data class PlayerPreferences(
    var showDamageNumbers: Boolean = true,
    var showStatusBars: Boolean = true,
    var showEnvironmentIndicator: Boolean = true,
    var showResonanceProgress: Boolean = true,
    var combatLogEnabled: Boolean = false
)

data class SavedBuffData(
    val id: String,
    val attribute: String,
    val operation: String,
    val value: Double,
    val expireTime: Long,
    val source: String,
    val remainingDuration: Long
)

data class SavedTempAffixData(
    val uuid: String,
    val affixId: String,
    val level: Int,
    val params: Map<String, Any>,
    val source: String,
    val remainingDuration: Long
)
