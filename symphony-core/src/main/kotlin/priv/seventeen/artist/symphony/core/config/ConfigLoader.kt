package priv.seventeen.artist.symphony.core.config

import org.bukkit.configuration.file.YamlConfiguration
import priv.seventeen.artist.blink.BlinkLog
import priv.seventeen.artist.symphony.api.affix.AffixRarity
import priv.seventeen.artist.symphony.core.affix.*
import priv.seventeen.artist.symphony.core.attribute.provider.GemProvider
import priv.seventeen.artist.symphony.core.growth.enhance.EnhanceManager
import priv.seventeen.artist.symphony.core.growth.level.LevelManager
import priv.seventeen.artist.symphony.core.growth.set.SetManager
import priv.seventeen.artist.symphony.core.growth.rune.*
import priv.seventeen.artist.symphony.core.script.AriaCallbackManager
import priv.seventeen.artist.symphony.core.skill.builtin.AriaSkillProvider
import priv.seventeen.artist.symphony.core.skill.builtin.SymphonySkillProvider
import priv.seventeen.artist.symphony.core.advanced.resonance.*
import priv.seventeen.artist.symphony.core.advanced.interaction.*
import priv.seventeen.artist.symphony.core.advanced.talent.*
import priv.seventeen.artist.symphony.core.advanced.element.*
import priv.seventeen.artist.symphony.core.advanced.status.*
import priv.seventeen.artist.symphony.core.advanced.environment.*
import java.io.File
import java.util.UUID

/**
 * 统一 YAML 配置加载器 — 从 plugins/Symphony/ 目录加载所有 YAML 数据文件。
 */
object ConfigLoader {

    fun loadAll(
        dataFolder: File,
        affixManager: AffixManagerImpl,
        skillProvider: SymphonySkillProvider,
        setManager: SetManager,
        ariaProvider: AriaSkillProvider? = null,
        levelManager: LevelManager? = null,
        enhanceManager: EnhanceManager? = null,
        gemProvider: GemProvider? = null
    ) {
        loadAffixes(File(dataFolder, "affixes"), affixManager)
        loadAffixPools(File(dataFolder, "affix-pools"), affixManager)
        loadSkills(File(dataFolder, "skills"), skillProvider, ariaProvider)
        loadSets(File(dataFolder, "sets"), setManager)
        loadResonances(File(dataFolder, "resonances"))
        loadRunes(File(dataFolder, "runes"))
        loadStatuses(File(dataFolder, "statuses"))
        loadTalents(File(dataFolder, "talents"))
        loadInteractions(File(dataFolder, "interactions"))
        loadEnvironments(File(dataFolder, "environments"))
        loadReactions(File(dataFolder, "reactions"))
        levelManager?.let { loadLevelConfig(File(dataFolder, "config"), it) }
        enhanceManager?.let { loadEnhanceConfig(File(dataFolder, "config"), it) }
        gemProvider?.let { loadGems(File(dataFolder, "gems"), it) }
        if (AriaCallbackManager.size() > 0) {
            BlinkLog.info("已预编译 ${AriaCallbackManager.size()} 个脚本回调")
        }
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
                        type = tMap["type"]?.toString()?.uppercase() ?: return@forEach,
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
                        val attrSec = sub.getConfigurationSection("attributes")
                            ?: sub.getConfigurationSection("passive_attributes")
                        attrSec?.let {
                            for (attrKey in it.getKeys(false)) {
                                val attrSub = it.getConfigurationSection(attrKey) ?: continue
                                attrs[attrKey] = SetManager.AttributeBonus(
                                    operation = attrSub.getString("operation") ?: "FLAT",
                                    value = attrSub.getDouble("value", 0.0)
                                )
                            }
                        }
                        @Suppress("UNCHECKED_CAST")
                        val triggers = sub.getMapList("triggers").map { it as Map<String, Any> }
                        bonuses[pieces] = SetManager.SetBonus(
                            display = sub.getString("display") ?: "",
                            attributes = attrs,
                            triggers = triggers
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

    fun loadStatuses(dir: File) {
        if (!dir.exists()) { dir.mkdirs(); return }
        val files = dir.listFiles { f -> f.extension == "yml" } ?: return
        var count = 0
        for (file in files) {
            try {
                val config = YamlConfiguration.loadConfiguration(file)
                val id = config.getString("id") ?: continue

                // 解析 per_stack_attributes
                val perStackAttrs = mutableMapOf<String, StackAttribute>()
                config.getConfigurationSection("per_stack_attributes")?.let { sec ->
                    for (attrId in sec.getKeys(false)) {
                        val sub = sec.getConfigurationSection(attrId) ?: continue
                        perStackAttrs[attrId] = StackAttribute(
                            operation = sub.getString("operation") ?: "FLAT",
                            value = sub.getDouble("value", 0.0)
                        )
                    }
                }

                val decayMode = try {
                    DecayMode.valueOf(config.getString("decay_mode")?.uppercase() ?: "INDIVIDUAL")
                } catch (e: Exception) { DecayMode.INDIVIDUAL }

                val def = StatusDefinition(
                    id = id,
                    displayName = config.getString("display_name") ?: id,
                    icon = config.getString("icon") ?: "",
                    maxStacks = config.getInt("max_stacks", 10),
                    stackDuration = config.getLong("stack_duration", 8000),
                    decayMode = decayMode,
                    tickInterval = config.getLong("tick_interval", 1000),
                    damageType = config.getString("damage_type") ?: "physical",
                    perStackDamageRatio = config.getDouble("per_stack_damage_ratio", 0.05),
                    perStackAttributes = perStackAttrs,
                    immuneDuration = config.getLong("immune_duration", 3000)
                )
                StatusLayerSystem.register(def)
                count++

                // 编译脚本回调
                config.getString("on_max_stacks")?.let { code ->
                    AriaCallbackManager.compile("status:$id:on_max_stacks", code)
                }
            } catch (e: Exception) {
                BlinkLog.warn("加载状态定义失败 ${file.name}: ${e.message}")
            }
        }
        if (count > 0) BlinkLog.info("已加载 $count 个状态定义")
    }

    fun loadTalents(dir: File) {
        if (!dir.exists()) { dir.mkdirs(); return }
        val files = dir.listFiles { f -> f.extension == "yml" } ?: return
        var count = 0
        for (file in files) {
            try {
                val config = YamlConfiguration.loadConfiguration(file)
                val id = config.getString("id") ?: continue

                // 解析 gate
                val gateSec = config.getConfigurationSection("gate")
                val conditions = mutableListOf<TalentGateCondition>()
                gateSec?.getMapList("conditions")?.forEach { raw ->
                    @Suppress("UNCHECKED_CAST")
                    val m = raw as Map<String, Any>
                    val attribute = m["attribute"]?.toString() ?: return@forEach
                    val threshold = when (val t = m["threshold"]) {
                        is Number -> t.toDouble()
                        is String -> t.toDoubleOrNull() ?: 0.0
                        else -> 0.0
                    }
                    val operator = m["operator"]?.toString() ?: ">="
                    conditions.add(TalentGateCondition(attribute, threshold, operator))
                }
                val gate = TalentGate(
                    type = gateSec?.getString("type") ?: "SINGLE",
                    attribute = gateSec?.getString("attribute"),
                    threshold = gateSec?.getDouble("threshold", 0.0) ?: 0.0,
                    operator = gateSec?.getString("operator") ?: ">=",
                    conditions = conditions
                )

                // 解析 passive_attributes
                val passiveAttrs = mutableMapOf<String, PassiveAttr>()
                config.getConfigurationSection("passive_attributes")?.let { sec ->
                    for (attrId in sec.getKeys(false)) {
                        val sub = sec.getConfigurationSection(attrId) ?: continue
                        passiveAttrs[attrId] = PassiveAttr(
                            operation = sub.getString("operation") ?: "FLAT",
                            value = sub.getDouble("value", 0.0)
                        )
                    }
                }

                val def = TalentDefinition(
                    id = id,
                    displayName = config.getString("display_name") ?: id,
                    description = config.getString("description") ?: "",
                    icon = config.getString("icon") ?: "NETHER_STAR",
                    gate = gate,
                    passiveAttributes = passiveAttrs
                )
                TalentManager.register(def)
                count++

                // 编译脚本回调
                config.getString("effect")?.let { AriaCallbackManager.compile("talent:$id:effect", it) }
                config.getString("on_deactivate")?.let { AriaCallbackManager.compile("talent:$id:on_deactivate", it) }
            } catch (e: Exception) {
                BlinkLog.warn("加载天赋定义失败 ${file.name}: ${e.message}")
            }
        }
        if (count > 0) BlinkLog.info("已加载 $count 个天赋定义")
    }

    fun loadInteractions(dir: File) {
        if (!dir.exists()) { dir.mkdirs(); return }
        val files = dir.listFiles { f -> f.extension == "yml" } ?: return
        var count = 0
        for (file in files) {
            try {
                val config = YamlConfiguration.loadConfiguration(file)
                val id = config.getString("id") ?: continue
                val type = try {
                    InteractionType.valueOf(config.getString("type")?.uppercase() ?: "CONVERSION")
                } catch (e: Exception) {
                    BlinkLog.warn("交互 ${file.name} type 无效，跳过")
                    continue
                }

                val def = InteractionDefinition(
                    id = id,
                    type = type,
                    source = config.getString("source"),
                    target = config.getString("target"),
                    attributes = config.getStringList("attributes"),
                    threshold = config.getDouble("threshold", 0.0),
                    thresholdA = config.getDouble("threshold_a", 0.0),
                    ratio = config.getDouble("ratio", 0.0),
                    bonus = config.getDouble("bonus", 0.0),
                    penaltyB = config.getDouble("penalty_b", 0.0),
                    multiplier = config.getDouble("multiplier", 1.0),
                    description = config.getString("description") ?: "",
                    attributeA = config.getString("attribute_a"),
                    attributeB = config.getString("attribute_b")
                )
                InteractionNetwork.register(def)
                count++

                // 编译脚本回调
                config.getString("condition")?.let { AriaCallbackManager.compile("interaction:$id:condition", it) }
                config.getString("effect")?.let { AriaCallbackManager.compile("interaction:$id:effect", it) }
            } catch (e: Exception) {
                BlinkLog.warn("加载交互定义失败 ${file.name}: ${e.message}")
            }
        }
        if (count > 0) BlinkLog.info("已加载 $count 个属性交互定义")
    }

    fun loadEnvironments(dir: File) {
        if (!dir.exists()) { dir.mkdirs(); return }
        val files = dir.listFiles { f -> f.extension == "yml" } ?: return
        var count = 0
        for (file in files) {
            try {
                val config = YamlConfiguration.loadConfiguration(file)
                val id = config.getString("id") ?: continue
                val type = try {
                    EnvironmentType.valueOf(config.getString("type")?.uppercase() ?: "BIOME")
                } catch (e: Exception) {
                    BlinkLog.warn("环境 ${file.name} type 无效，跳过")
                    continue
                }

                // 解析 attributes
                val attrs = mutableMapOf<String, EnvAttributeEffect>()
                config.getConfigurationSection("attributes")?.let { sec ->
                    for (attrId in sec.getKeys(false)) {
                        val sub = sec.getConfigurationSection(attrId) ?: continue
                        attrs[attrId] = EnvAttributeEffect(
                            operation = sub.getString("operation") ?: "FLAT",
                            value = sub.getDouble("value", 0.0)
                        )
                    }
                }

                val modifier = EnvironmentModifier(
                    id = id,
                    displayName = config.getString("display_name") ?: id,
                    type = type,
                    attributes = attrs,
                    description = config.getString("description") ?: ""
                )
                EnvironmentSystem.register(modifier)
                count++

                // 编译脚本条件
                config.getString("condition")?.let {
                    AriaCallbackManager.compile("environment:$id:condition", it)
                }
            } catch (e: Exception) {
                BlinkLog.warn("加载环境定义失败 ${file.name}: ${e.message}")
            }
        }
        if (count > 0) BlinkLog.info("已加载 $count 个环境定义")
    }

    fun loadReactions(dir: File) {
        if (!dir.exists()) { dir.mkdirs(); return }
        val files = dir.listFiles { f -> f.extension == "yml" } ?: return
        var count = 0
        for (file in files) {
            try {
                val config = YamlConfiguration.loadConfiguration(file)
                val id = config.getString("id") ?: continue
                val type = try {
                    ReactionType.valueOf(config.getString("type")?.uppercase() ?: "AMPLIFY")
                } catch (e: Exception) {
                    BlinkLog.warn("反应 ${file.name} type 无效，跳过")
                    continue
                }

                // 解析 reverse
                val reverseSec = config.getConfigurationSection("reverse")
                val reverse = if (reverseSec != null) {
                    ReactionReverse(
                        id = reverseSec.getString("id") ?: "${id}_reverse",
                        multiplier = reverseSec.getDouble("multiplier", 1.0)
                    )
                } else null

                val def = ReactionDefinition(
                    id = id,
                    displayName = config.getString("display_name") ?: id,
                    trigger = config.getString("trigger") ?: continue,
                    aura = config.getString("aura") ?: continue,
                    type = type,
                    multiplier = config.getDouble("multiplier", 1.0),
                    gaugeConsume = config.getDouble("gauge_consume", 0.5),
                    particle = config.getString("particle"),
                    sound = config.getString("sound"),
                    radius = config.getDouble("radius", 0.0),
                    ticks = config.getInt("ticks", 0),
                    interval = config.getLong("interval", 1000),
                    reverse = reverse
                )
                ReactionSystem.register(def)
                count++

                // 编译脚本回调
                config.getString("on_tick")?.let {
                    AriaCallbackManager.compile("reaction:$id:on_tick", it)
                }
            } catch (e: Exception) {
                BlinkLog.warn("加载反应定义失败 ${file.name}: ${e.message}")
            }
        }
        if (count > 0) BlinkLog.info("已加载 $count 个元素反应定义")
    }

    fun loadLevelConfig(configDir: File, levelManager: LevelManager) {
        val file = File(configDir, "level.yml")
        if (!file.exists()) return
        try {
            val root = YamlConfiguration.loadConfiguration(file)
            val config = root.getConfigurationSection("level") ?: root // 兼容有/无根节点
            levelManager.maxLevel = config.getInt("max_level", 100)
            config.getString("exp_formula")?.let { formula ->
                levelManager.expFormulaScript = formula
            }
            // attribute_growth
            val growth = mutableMapOf<String, LevelManager.GrowthEntry>()
            config.getConfigurationSection("attribute_growth")?.let { sec ->
                for (key in sec.getKeys(false)) {
                    val sub = sec.getConfigurationSection(key) ?: continue
                    growth[key] = LevelManager.GrowthEntry(
                        base = sub.getDouble("base", 0.0),
                        perLevel = sub.getDouble("per_level", 0.0),
                        formula = sub.getString("formula")
                    )
                }
            }
            levelManager.attributeGrowth = growth
            // effects
            config.getConfigurationSection("effects")?.let { sec ->
                levelManager.levelUpSound = sec.getString("sound")
                levelManager.levelUpParticle = sec.getString("particle")
                sec.getConfigurationSection("title")?.let { titleSec ->
                    levelManager.levelUpTitleMain = titleSec.getString("main")
                    levelManager.levelUpTitleSub = titleSec.getString("sub")
                    levelManager.levelUpTitleFadeIn = titleSec.getInt("fade_in", 10)
                    levelManager.levelUpTitleStay = titleSec.getInt("stay", 40)
                    levelManager.levelUpTitleFadeOut = titleSec.getInt("fade_out", 10)
                }
            }
            BlinkLog.info("已加载等级配置 (max_level=${levelManager.maxLevel})")
        } catch (e: Exception) {
            BlinkLog.warn("加载等级配置失败: ${e.message}")
        }
    }

    fun loadEnhanceConfig(configDir: File, enhanceManager: EnhanceManager) {
        val file = File(configDir, "enhancement.yml")
        if (!file.exists()) return
        try {
            val root = YamlConfiguration.loadConfiguration(file)
            val config = root.getConfigurationSection("enhancement") ?: root // 兼容有/无根节点
            enhanceManager.maxLevel = config.getInt("max_level", 15)
            val levels = mutableMapOf<Int, EnhanceManager.LevelConfig>()
            config.getConfigurationSection("levels")?.let { sec ->
                for (key in sec.getKeys(false)) {
                    val lvl = key.toIntOrNull() ?: continue
                    val sub = sec.getConfigurationSection(key) ?: continue
                    levels[lvl] = EnhanceManager.LevelConfig(
                        multiplier = sub.getDouble("multiplier", 1.0),
                        successRate = sub.getDouble("success_rate", 0.5),
                        destroyRate = sub.getDouble("destroy_rate", 0.0)
                    )
                }
            }
            if (levels.isNotEmpty()) {
                enhanceManager.loadConfig(levels)
            }
            // 保护道具配置
            config.getConfigurationSection("protections")?.let { sec ->
                enhanceManager.preventDestroyItem = sec.getString("prevent_destroy")
                enhanceManager.preventDowngradeItem = sec.getString("prevent_downgrade")
                enhanceManager.successBonusItem = sec.getString("success_rate_bonus")
            }
            // 失败配置
            config.getConfigurationSection("on_failure")?.let { sec ->
                enhanceManager.downgradeLevels = sec.getInt("downgrade_levels", 1)
            }
            // 特效配置
            config.getConfigurationSection("effects")?.let { sec ->
                enhanceManager.successSound = sec.getString("success_sound")
                enhanceManager.failureSound = sec.getString("failure_sound")
                enhanceManager.destroySound = sec.getString("destroy_sound")
            }
            BlinkLog.info("已加载强化配置 (max_level=${enhanceManager.maxLevel})")
        } catch (e: Exception) {
            BlinkLog.warn("加载强化配置失败: ${e.message}")
        }
    }

    fun loadGems(dir: File, gemProvider: GemProvider) {
        if (!dir.exists()) { dir.mkdirs(); return }
        val files = dir.listFiles { f -> f.extension == "yml" } ?: return
        var count = 0
        for (file in files) {
            try {
                val config = YamlConfiguration.loadConfiguration(file)
                val id = config.getString("id") ?: continue
                val levelsMap = mutableMapOf<Int, Map<String, GemProvider.GemAttr>>()
                config.getConfigurationSection("levels")?.let { levelsSec ->
                    for (lvlKey in levelsSec.getKeys(false)) {
                        val lvl = lvlKey.toIntOrNull() ?: continue
                        val lvlSec = levelsSec.getConfigurationSection(lvlKey) ?: continue
                        val attrs = mutableMapOf<String, GemProvider.GemAttr>()
                        lvlSec.getConfigurationSection("attributes")?.let { attrSec ->
                            for (attrKey in attrSec.getKeys(false)) {
                                val sub = attrSec.getConfigurationSection(attrKey) ?: continue
                                val op = sub.getString("operation") ?: "FLAT"
                                val value = sub.getDouble("value", 0.0)
                                attrs[attrKey] = GemProvider.GemAttr(op, value)
                            }
                        }
                        levelsMap[lvl] = attrs
                    }
                }
                gemProvider.registerGem(id, levelsMap)
                count++
            } catch (e: Exception) {
                BlinkLog.warn("加载宝石文件失败 ${file.name}: ${e.message}")
            }
        }
        if (count > 0) BlinkLog.info("已加载 $count 个宝石定义")
    }
}
