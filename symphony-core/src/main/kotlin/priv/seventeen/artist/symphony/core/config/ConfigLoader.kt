package priv.seventeen.artist.symphony.core.config

import org.bukkit.configuration.file.YamlConfiguration
import priv.seventeen.artist.blink.BlinkLog
import priv.seventeen.artist.symphony.api.affix.AffixRarity
import priv.seventeen.artist.symphony.core.affix.*
import priv.seventeen.artist.symphony.core.growth.set.SetManager
import priv.seventeen.artist.symphony.core.growth.rune.*
import priv.seventeen.artist.symphony.core.skill.builtin.AriaSkillProvider
import priv.seventeen.artist.symphony.core.skill.builtin.SymphonySkillProvider
import priv.seventeen.artist.symphony.core.advanced.resonance.*
import java.io.File
import java.util.UUID

/**
 * 统一 YAML 配置加载器 — 从 plugins/Symphony/ 目录加载所有 YAML 数据文件。
 */
object ConfigLoader {

    fun loadAll(dataFolder: File, affixManager: AffixManagerImpl, skillProvider: SymphonySkillProvider, setManager: SetManager, ariaProvider: AriaSkillProvider? = null) {
        loadAffixes(File(dataFolder, "affixes"), affixManager)
        loadAffixPools(File(dataFolder, "affix-pools"), affixManager)
        loadSkills(File(dataFolder, "skills"), skillProvider, ariaProvider)
        loadSets(File(dataFolder, "sets"), setManager)
        loadResonances(File(dataFolder, "resonances"))
        loadRunes(File(dataFolder, "runes"))
    }

    fun loadAffixes(dir: File, manager: AffixManagerImpl) {
        if (!dir.exists()) { dir.mkdirs(); return }
        val files = dir.listFiles { f -> f.extension == "yml" } ?: return
        var count = 0
        for (file in files) {
            try {
                val config = YamlConfiguration.loadConfiguration(file)
                val id = config.getString("id") ?: continue
                val levels = mutableMapOf<Int, Map<String, Any>>()
                config.getConfigurationSection("levels")?.let { sec ->
                    for (key in sec.getKeys(false)) {
                        val lvl = key.toIntOrNull() ?: continue
                        val params = mutableMapOf<String, Any>()
                        sec.getConfigurationSection(key)?.getKeys(false)?.forEach { k ->
                            params[k] = sec.get("$key.$k") ?: return@forEach
                        }
                        levels[lvl] = params
                    }
                }
                val triggers = mutableListOf<TriggerBinding>()
                config.getMapList("triggers").forEach { triggerMap ->
                    @Suppress("UNCHECKED_CAST")
                    val tMap = triggerMap as Map<String, Any>
                    triggers.add(TriggerBinding(
                        type = tMap["type"]?.toString() ?: return@forEach,
                        conditions = (tMap["conditions"] as? List<Map<String, Any>>) ?: emptyList(),
                        actions = (tMap["actions"] as? List<Map<String, Any>>) ?: emptyList()
                    ))
                }
                val passiveAttrs = mutableMapOf<String, PassiveAttribute>()
                config.getConfigurationSection("passive_attributes")?.let { sec ->
                    for (key in sec.getKeys(false)) {
                        val sub = sec.getConfigurationSection(key) ?: continue
                        passiveAttrs[key] = PassiveAttribute(
                            operation = sub.getString("operation") ?: "FLAT",
                            value = sub.getString("value") ?: "0"
                        )
                    }
                }
                val def = AffixDefinition(
                    id = id,
                    displayName = config.getString("display_name") ?: id,
                    description = config.getStringList("description"),
                    maxLevel = config.getInt("max_level", 1),
                    rarity = try { AffixRarity.valueOf(config.getString("rarity")?.uppercase() ?: "COMMON") } catch (e: Exception) { AffixRarity.COMMON },
                    category = config.getString("category") ?: "any",
                    exclusiveGroup = config.getString("exclusive_group"),
                    tags = config.getStringList("tags"),
                    levels = levels,
                    triggers = triggers,
                    passiveAttributes = passiveAttrs
                )
                manager.registerAffix(def)
                count++
            } catch (e: Exception) {
                BlinkLog.warn("加载词条文件失败 ${file.name}: ${e.message}")
            }
        }
        if (count > 0) BlinkLog.info("已加载 $count 个词条定义")
    }

    fun loadAffixPools(dir: File, manager: AffixManagerImpl) {
        if (!dir.exists()) { dir.mkdirs(); return }
        val files = dir.listFiles { f -> f.extension == "yml" } ?: return
        var count = 0
        for (file in files) {
            try {
                val config = YamlConfiguration.loadConfiguration(file)
                val id = config.getString("id") ?: continue
                val entries = mutableListOf<AffixPoolEntry>()
                config.getMapList("entries").forEach { entryMap ->
                    @Suppress("UNCHECKED_CAST")
                    val eMap = entryMap as Map<String, Any>
                    val levelRange = (eMap["level_range"] as? List<*>)?.let {
                        val min = (it.getOrNull(0) as? Number)?.toInt() ?: 1
                        val max = (it.getOrNull(1) as? Number)?.toInt() ?: 1
                        min..max
                    } ?: 1..1
                    entries.add(AffixPoolEntry(
                        affixId = eMap["affix"]?.toString() ?: return@forEach,
                        weight = (eMap["weight"] as? Number)?.toInt() ?: 100,
                        levelRange = levelRange
                    ))
                }
                val genSec = config.getConfigurationSection("generation")
                val rarityWeights = mutableMapOf<AffixRarity, IntRange>()
                genSec?.getConfigurationSection("rarity_weights")?.let { rw ->
                    for (key in rw.getKeys(false)) {
                        val rarity = try { AffixRarity.valueOf(key.uppercase()) } catch (e: Exception) { continue }
                        val sub = rw.getConfigurationSection(key) ?: continue
                        rarityWeights[rarity] = sub.getInt("min", 1)..sub.getInt("max", 4)
                    }
                }
                val pool = AffixPool(
                    id = id,
                    displayName = config.getString("display_name") ?: id,
                    entries = entries,
                    generation = AffixPoolGeneration(
                        minAffixes = genSec?.getInt("min_affixes", 1) ?: 1,
                        maxAffixes = genSec?.getInt("max_affixes", 4) ?: 4,
                        rarityWeights = rarityWeights,
                        allowDuplicates = genSec?.getBoolean("allow_duplicates", false) ?: false,
                        luckInfluence = genSec?.getDouble("luck_influence", 0.1) ?: 0.1
                    )
                )
                manager.registerPool(pool)
                count++
            } catch (e: Exception) {
                BlinkLog.warn("加载词条池文件失败 ${file.name}: ${e.message}")
            }
        }
        if (count > 0) BlinkLog.info("已加载 $count 个词条池")
    }

    fun loadSkills(dir: File, provider: SymphonySkillProvider, ariaProvider: AriaSkillProvider? = null) {
        if (!dir.exists()) { dir.mkdirs(); return }
        val files = dir.walkTopDown().filter { it.isFile && it.extension == "yml" }.toList()
        var count = 0
        var ariaCount = 0
        for (file in files) {
            try {
                val config = YamlConfiguration.loadConfiguration(file)
                val id = config.getString("id") ?: continue
                val providerId = config.getString("provider") ?: "symphony"
                if (providerId == "aria") {
                    if (ariaProvider == null) {
                        BlinkLog.warn("发现 aria 技能 ${file.name} 但 AriaSkillProvider 未注册，跳过")
                        continue
                    }
                    val script = config.getString("script")
                    if (script.isNullOrBlank()) {
                        BlinkLog.warn("aria 技能缺少 script 字段: ${file.name}")
                        continue
                    }
                    ariaProvider.register(AriaSkillProvider.ScriptSkill(
                        id = id,
                        displayName = config.getString("display_name") ?: id,
                        description = config.getStringList("description"),
                        maxLevel = config.getInt("max_level", 1),
                        cooldown = config.getLong("cooldown", 0),
                        manaCost = config.getDouble("mana_cost", 0.0),
                        script = script
                    ))
                    ariaCount++
                    continue
                }
                val levels = mutableMapOf<Int, Map<String, Any>>()
                config.getConfigurationSection("levels")?.let { sec ->
                    for (key in sec.getKeys(false)) {
                        val lvl = key.toIntOrNull() ?: continue
                        val params = mutableMapOf<String, Any>()
                        sec.getConfigurationSection(key)?.getKeys(false)?.forEach { k ->
                            params[k] = sec.get("$key.$k") ?: return@forEach
                        }
                        levels[lvl] = params
                    }
                }
                @Suppress("UNCHECKED_CAST")
                val actions = config.getMapList("actions").map { it as Map<String, Any> }
                provider.registerSkill(SymphonySkillProvider.SkillDefinition(
                    id = id,
                    displayName = config.getString("display_name") ?: id,
                    description = config.getStringList("description"),
                    maxLevel = config.getInt("max_level", 1),
                    cooldown = config.getLong("cooldown", 0),
                    manaCost = config.getDouble("mana_cost", 0.0),
                    levels = levels,
                    actions = actions
                ))
                count++
            } catch (e: Exception) {
                BlinkLog.warn("加载技能文件失败 ${file.name}: ${e.message}")
            }
        }
        if (count > 0) BlinkLog.info("已加载 $count 个 Symphony 技能定义")
        if (ariaCount > 0) BlinkLog.info("已加载 $ariaCount 个 Aria 脚本技能")
    }

    fun loadSets(dir: File, manager: SetManager) {
        if (!dir.exists()) { dir.mkdirs(); return }
        val files = dir.listFiles { f -> f.extension == "yml" } ?: return
        var count = 0
        for (file in files) {
            try {
                val config = YamlConfiguration.loadConfiguration(file)
                val id = config.getString("id") ?: continue
                val bonuses = mutableMapOf<Int, SetManager.SetBonus>()
                config.getConfigurationSection("bonuses")?.let { sec ->
                    for (key in sec.getKeys(false)) {
                        val pieces = key.toIntOrNull() ?: continue
                        val sub = sec.getConfigurationSection(key) ?: continue
                        val attrs = mutableMapOf<String, SetManager.AttributeBonus>()
                        sub.getConfigurationSection("attributes")?.let { attrSec ->
                            for (attrKey in attrSec.getKeys(false)) {
                                val attrSub = attrSec.getConfigurationSection(attrKey) ?: continue
                                attrs[attrKey] = SetManager.AttributeBonus(
                                    operation = attrSub.getString("operation") ?: "FLAT",
                                    value = attrSub.getDouble("value", 0.0)
                                )
                            }
                        }
                        bonuses[pieces] = SetManager.SetBonus(
                            display = sub.getString("display") ?: "",
                            attributes = attrs
                        )
                    }
                }
                manager.registerSet(SetManager.SetDefinition(id, config.getString("display_name") ?: id, bonuses))
                count++
            } catch (e: Exception) {
                BlinkLog.warn("加载套装文件失败 ${file.name}: ${e.message}")
            }
        }
        if (count > 0) BlinkLog.info("已加载 $count 个套装定义")
    }

    fun loadResonances(dir: File) {
        if (!dir.exists()) { dir.mkdirs(); return }
        val files = dir.listFiles { f -> f.extension == "yml" } ?: return
        var count = 0
        for (file in files) {
            try {
                val config = YamlConfiguration.loadConfiguration(file)
                val id = config.getString("id") ?: continue
                val condSec = config.getConfigurationSection("condition") ?: continue
                val condType = try { ResonanceConditionType.valueOf(condSec.getString("type")?.uppercase() ?: "AFFIX_TAG_COUNT") } catch (e: Exception) { continue }
                val condition = ResonanceCondition(
                    type = condType,
                    tag = condSec.getString("tag"),
                    count = condSec.getInt("count", 0),
                    affixIds = condSec.getStringList("affix_ids"),
                    rarity = condSec.getString("rarity"),
                    minSum = condSec.getInt("min_sum", 0),
                    mode = condSec.getString("mode") ?: "ANY"
                )
                val effectsSec = config.getConfigurationSection("effects")
                val attrs = mutableMapOf<String, AttributeEffect>()
                effectsSec?.getConfigurationSection("attributes")?.let { attrSec ->
                    for (key in attrSec.getKeys(false)) {
                        val sub = attrSec.getConfigurationSection(key) ?: continue
                        attrs[key] = AttributeEffect(sub.getString("operation") ?: "FLAT", sub.getDouble("value", 0.0))
                    }
                }
                ResonanceManager.register(ResonanceDefinition(
                    id = id,
                    displayName = config.getString("display_name") ?: id,
                    description = config.getStringList("description"),
                    condition = condition,
                    effects = ResonanceEffects(attributes = attrs)
                ))
                count++
            } catch (e: Exception) {
                BlinkLog.warn("加载共鸣文件失败 ${file.name}: ${e.message}")
            }
        }
        if (count > 0) BlinkLog.info("已加载 $count 个词条共鸣")
    }

    fun loadRunes(dir: File) {
        if (!dir.exists()) { dir.mkdirs(); return }
        val files = dir.listFiles { f -> f.extension == "yml" } ?: return
        var count = 0
        for (file in files) {
            try {
                val config = YamlConfiguration.loadConfiguration(file)
                val id = config.getString("id") ?: continue
                val activationSec = config.getConfigurationSection("activation")
                val fragments = mutableMapOf<Int, Int>()
                activationSec?.getConfigurationSection("fragments_required")?.let { sec ->
                    for (key in sec.getKeys(false)) {
                        val lvl = key.toIntOrNull() ?: continue
                        fragments[lvl] = sec.getInt(key)
                    }
                }
                val activation = RuneActivation(
                    type = activationSec?.getString("type") ?: "FRAGMENT",
                    fragmentsRequired = fragments
                )
                val passives = mutableMapOf<Int, Map<String, RunePassiveAttribute>>()
                config.getConfigurationSection("passive_attributes")?.let { sec ->
                    for (lvlKey in sec.getKeys(false)) {
                        val lvl = lvlKey.toIntOrNull() ?: continue
                        val lvlSec = sec.getConfigurationSection(lvlKey) ?: continue
                        val attrs = mutableMapOf<String, RunePassiveAttribute>()
                        for (attrKey in lvlSec.getKeys(false)) {
                            val sub = lvlSec.getConfigurationSection(attrKey) ?: continue
                            attrs[attrKey] = RunePassiveAttribute(
                                operation = sub.getString("operation") ?: "FLAT",
                                value = sub.getDouble("value", 0.0)
                            )
                        }
                        passives[lvl] = attrs
                    }
                }
                @Suppress("UNCHECKED_CAST")
                val triggers = config.getMapList("triggers").map { it as Map<String, Any> }
                RuneRegistry.register(RuneDefinition(
                    id = id,
                    displayName = config.getString("display_name") ?: id,
                    description = config.getStringList("description"),
                    maxLevel = config.getInt("max_level", 1),
                    category = config.getString("category") ?: "general",
                    activation = activation,
                    passiveAttributes = passives,
                    triggers = triggers
                ))
                count++
            } catch (e: Exception) {
                BlinkLog.warn("加载符文文件失败 ${file.name}: ${e.message}")
            }
        }
        if (count > 0) BlinkLog.info("已加载 $count 个符文定义")
    }
}
