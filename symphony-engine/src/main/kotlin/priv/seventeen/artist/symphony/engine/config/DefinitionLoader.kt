/*
 * Copyright 2026 17Artist
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package priv.seventeen.artist.symphony.engine.config

import priv.seventeen.artist.symphony.api.attribute.AttributeBounds
import priv.seventeen.artist.symphony.api.attribute.AttributeDefinition
import priv.seventeen.artist.symphony.api.attribute.AttributeFormat
import priv.seventeen.artist.symphony.api.attribute.AttributeKey
import priv.seventeen.artist.symphony.api.attribute.AttributeModifier
import priv.seventeen.artist.symphony.api.attribute.AttributeOperation
import priv.seventeen.artist.symphony.engine.attribute.AttributeGraph
import priv.seventeen.artist.symphony.engine.definition.*
import priv.seventeen.artist.symphony.engine.equipment.OffhandMode
import priv.seventeen.artist.symphony.engine.equipment.OffhandSettings
import priv.seventeen.artist.symphony.engine.power.PowerExpressionCompiler
import priv.seventeen.artist.symphony.engine.validation.ValidationCollector
import priv.seventeen.artist.symphony.engine.validation.ValidationReport
import java.nio.file.Files
import java.nio.file.Path
import java.math.RoundingMode
import java.text.DecimalFormat
import java.text.DecimalFormatSymbols
import java.time.Instant
import java.util.Locale

data class DefinitionLoadResult(
    val settings: SymphonySettings?,
    val snapshot: DefinitionSnapshot?,
    val report: ValidationReport
)

class DefinitionLoader(
    private val dataRoot: Path,
    private val yaml: StrictYaml = StrictYaml(),
    private val clock: () -> Instant = Instant::now
) {
    fun load(): DefinitionLoadResult {
        val issues = ValidationCollector()
        val settings = loadSettings(issues)
        val attributes = loadAttributes(issues)
        val damage = loadDamage(issues)
        val sets = loadSets(issues, attributes)
        val combatPower = loadCombatPower(issues, attributes, sets)
        val enhancement = loadEnhancement(issues)
        val affixes = loadGeneric("affixes", AFFIX_FIELDS, issues)
        val rawAffixPools = loadGeneric("affix-pools", AFFIX_POOL_FIELDS, issues)
        val skills = loadGeneric("skills", SKILL_FIELDS, issues)
        val gems = loadGeneric("items/gems", GEM_FIELDS, issues)
        val socketTools = loadSocketTools(issues)
        val socketRemoval = loadSocketRemoval(issues)
        val rawInteractions = loadGeneric("advanced/interactions", INTERACTION_FIELDS, issues)
        val reactions = loadGeneric("advanced/reactions", REACTION_FIELDS, issues)
        val resonances = loadGeneric("advanced/resonances", RESONANCE_FIELDS, issues)
        val talents = loadGeneric("advanced/talents", TALENT_FIELDS, issues)
        val statuses = loadGeneric("advanced/statuses", STATUS_FIELDS, issues)
        val environments = loadGeneric("advanced/environments", ENVIRONMENT_FIELDS, issues)

        validateGenericDefinitions(
            issues,
            attributes,
            affixes,
            skills,
            gems,
            reactions,
            resonances,
            talents,
            statuses,
            environments
        )

        val snapshot = if (attributes != null && damage != null && sets != null && combatPower != null) {
            runCatching {
                validateReferences(attributes, damage.second, sets)
                validateReactionReferences(reactions, damage.second)
                val interactions = compileInteractions(rawInteractions, attributes)
                val affixPools = compileAffixPools(rawAffixPools, affixes)
                val enrichedAttributes = addInteractionDependencies(attributes, interactions)
                AttributeGraph.build(enrichedAttributes)
                DefinitionSnapshot(
                    revision = 0,
                    createdAt = clock(),
                    attributes = enrichedAttributes,
                    armorFormula = damage.first,
                    damageChannels = damage.second,
                    sets = sets,
                    enhancement = enhancement,
                    affixes = affixes,
                    affixPools = affixPools,
                    skills = skills,
                    gems = gems,
                    socketTools = socketTools,
                    socketRemoval = socketRemoval,
                    combatPower = combatPower,
                    interactions = interactions,
                    reactions = reactions,
                    resonances = resonances,
                    talents = talents,
                    statuses = statuses,
                    environments = environments
                )
            }.onFailure { issues.error(null, "definitions", it.message ?: "定义编译失败", it) }.getOrNull()
        } else null

        val report = issues.report()
        return DefinitionLoadResult(settings, snapshot?.takeIf { report.valid }, report)
    }

    private fun loadCombatPower(
        issues: ValidationCollector,
        attributes: Map<AttributeKey, CompiledAttributeDefinition>?,
        sets: Map<String, SetDefinition>?
    ): CombatPowerDefinition? {
        val file = dataRoot.resolve("combat-power.yml")
        if (!Files.isRegularFile(file)) return CombatPowerDefinition.disabled()
        return capture(file, "combat-power", issues) {
            val root = StrictObject(yaml.load(file), "combat-power")
            root.int("schema", 0, 1..1)
            val enabled = root.boolean("enabled", true)
            val formula = root.requiredString("formula")
            val expression = PowerExpressionCompiler.compile(formula)

            val bounds = StrictObject(root.map("bounds"), "combat-power.bounds")
            val minimum = bounds.double("min", 0.0)
            val maximum = bounds.double("max", 1_000_000_000_000.0)
            require(minimum <= maximum) { "combat-power.bounds.min 不能大于 max" }
            bounds.finish()

            val output = StrictObject(root.map("output"), "combat-power.output")
            val scale = output.int("scale", 0, 0..12)
            val rounding = runCatching {
                RoundingMode.valueOf(output.string("rounding", "half_up")!!.uppercase().replace('-', '_'))
            }.getOrElse { throw IllegalArgumentException("combat-power.output.rounding 无效") }
            require(rounding != RoundingMode.UNNECESSARY) {
                "combat-power.output.rounding 不能填写 UNNECESSARY"
            }
            val format = output.string("format", "#,##0")!!.also {
                require(it.length <= 128) { "combat-power.output.format 不能超过 128 个字符" }
                DecimalFormat(it, DecimalFormatSymbols.getInstance(Locale.ROOT))
            }
            output.finish()
            root.finish()

            validateCombatPowerVariables(expression.variables, attributes.orEmpty(), sets.orEmpty())
            val zeroVariables = expression.variables.associateWith { 0.0 }
            val oneVariables = expression.variables.associateWith { 1.0 }
            expression.evaluate(zeroVariables)
            expression.evaluate(oneVariables)
            CombatPowerDefinition(enabled, formula, expression, minimum, maximum, scale, rounding, format)
        }
    }

    private fun validateCombatPowerVariables(
        variables: Set<String>,
        attributes: Map<AttributeKey, CompiledAttributeDefinition>,
        sets: Map<String, SetDefinition>
    ) {
        variables.forEach { variable ->
            when {
                variable in COMBAT_POWER_BUILT_INS -> Unit
                variable.startsWith("attribute:") -> {
                    val key = attributeKey(variable.removePrefix("attribute:"))
                    require(key in attributes) { "combat-power.formula 引用了未知属性 $key" }
                }
                variable.startsWith("set_pieces:") || variable.startsWith("set_tiers:") -> {
                    val raw = variable.substringAfter(':')
                    val id = namespacedId(raw)
                    require(id in sets) { "combat-power.formula 引用了未知套装 $id" }
                }
                else -> throw IllegalArgumentException("combat-power.formula 引用了不支持的变量 $variable")
            }
        }
    }

    private fun loadSettings(issues: ValidationCollector): SymphonySettings? {
        val file = dataRoot.resolve("config.yml")
        if (!Files.isRegularFile(file)) {
            issues.error(file, "config", "缺少 config.yml")
            return null
        }
        return capture(file, "config", issues) {
            val root = StrictObject(yaml.load(file), "config")
            val schema = root.int("schema", 0, 2..2)

            val combatNode = StrictObject(root.map("combat"), "config.combat")
            val mappedCauses = combatNode.map("environmental-causes").mapValues { (cause, channel) ->
                require(channel is String) { "config.combat.environmental-causes.$cause 必须是字符串" }
                channel
            }
            val combat = CombatSettings(
                enabled = combatNode.boolean("enabled", true),
                minimumDamage = combatNode.double("minimum-damage", 0.0).also {
                    require(it >= 0.0) { "config.combat.minimum-damage 不能为负数" }
                },
                confirmationDelayTicks = combatNode.long("confirmation-delay-ticks", 1, 1L..20L),
                maxTransactionDepth = combatNode.int("max-transaction-depth", 8, 1..64),
                mappedEnvironmentalCauses = mappedCauses
            )
            combatNode.finish()

            val scriptNode = StrictObject(root.map("scripts"), "config.scripts")
            val javaInterop = scriptNode.requiredString("java-interop")
            require(javaInterop == "unrestricted") {
                "config.scripts.java-interop 必须为 unrestricted；当前尚未实现 restricted 模式"
            }
            val scripts = ScriptSettings(
                javaInteropUnrestricted = true,
                slowCallbackWarningMillis = scriptNode.long("slow-callback-warning-ms", 5, 1L..60_000L),
                failureWindowSeconds = scriptNode.long("failure-window-seconds", 60, 1L..86_400L),
                disableAfterFailures = scriptNode.int("disable-after-failures", 5, 1..1000)
            )
            scriptNode.finish()

            val performanceNode = StrictObject(root.map("performance"), "config.performance")
            val performance = PerformanceSettings(
                equipmentCoalesceTicks = performanceNode.long("equipment-coalesce-ticks", 1, 1L..200L),
                timerBucketTicks = performanceNode.long("timer-bucket-ticks", 20, 1L..1200L),
                cacheIdleSeconds = performanceNode.long("cache-idle-seconds", 300, 30L..86_400L)
            )
            performanceNode.finish()

            val featureNode = StrictObject(root.map("features"), "config.features")
            val features = FeatureSettings(
                affixes = featureNode.boolean("affixes", true),
                skills = featureNode.boolean("skills", true),
                gems = featureNode.boolean("gems", true),
                sockets = featureNode.boolean("sockets", true),
                enhancement = featureNode.boolean("enhancement", true),
                interactions = featureNode.boolean("interactions", true),
                elements = featureNode.boolean("elements", true),
                resonances = featureNode.boolean("resonances", true),
                talents = featureNode.boolean("talents", true),
                statuses = featureNode.boolean("statuses", true),
                environments = featureNode.boolean("environments", true)
            )
            featureNode.finish()

            val equipmentNode = StrictObject(root.map("equipment"), "config.equipment")
            val offhandValues = equipmentNode.map("offhand")
            val legacyIncludeOffhand = equipmentNode.raw("include-offhand")?.also {
                require(it is Boolean) { "config.equipment.include-offhand 必须是布尔值" }
            } as? Boolean
            require(offhandValues.isEmpty() || legacyIncludeOffhand == null) {
                "config.equipment.offhand 不能与旧版 include-offhand 同时使用"
            }
            val offhand = if (legacyIncludeOffhand != null) {
                OffhandSettings(
                    if (legacyIncludeOffhand) OffhandMode.FULL else OffhandMode.DISABLED,
                    1.0
                )
            } else {
                val offhandNode = StrictObject(offhandValues, "config.equipment.offhand")
                val mode = OffhandMode.parse(offhandNode.string("mode", OffhandMode.FULL.id)!!)
                val rawScale = offhandNode.raw("attribute-scale")
                val scale = rawScale?.let {
                    StrictObject.parseNumberOrPercent(it, "config.equipment.offhand.attribute-scale")
                } ?: 0.5
                require(scale in 0.0..1.0) {
                    "config.equipment.offhand.attribute-scale 必须位于 0% 到 100% 之间"
                }
                offhandNode.finish()
                OffhandSettings(mode, scale)
            }
            val equipment = EquipmentSettings(
                offhand = offhand,
                coalesceTicks = equipmentNode.long("coalesce-ticks", performance.equipmentCoalesceTicks, 1L..200L)
            )
            equipmentNode.finish()

            val compatibilityNode = StrictObject(root.map("compatibility"), "config.compatibility")
            val epicFightNode = StrictObject(
                compatibilityNode.map("epic-fight"),
                "config.compatibility.epic-fight"
            )
            val compatibility = CompatibilitySettings(
                EpicFightCompatibilitySettings(
                    enabled = epicFightNode.boolean("enabled", false),
                    postWorldGraceMillis = epicFightNode.long("post-world-grace-ms", 4_000, 0L..60_000L),
                    stuckInactionMillis = epicFightNode.long("stuck-inaction-ms", 6_000, 1_000L..120_000L),
                    fallbackPollTicks = epicFightNode.long("fallback-poll-ticks", 1, 1L..20L)
                )
            )
            epicFightNode.finish()
            compatibilityNode.finish()
            root.finish()

            SymphonySettings(
                schema,
                combat,
                scripts,
                performance,
                features,
                equipment,
                compatibility
            )
        }
    }

    private fun loadAttributes(issues: ValidationCollector): Map<AttributeKey, CompiledAttributeDefinition>? {
        val files = yamlFiles(dataRoot.resolve("attributes"))
        if (files.isEmpty()) {
            issues.error(dataRoot.resolve("attributes"), "attributes", "未找到属性定义文件")
            return null
        }
        val result = linkedMapOf<AttributeKey, CompiledAttributeDefinition>()
        files.forEach { file ->
            capture(file, "attributes", issues) {
                val root = StrictObject(yaml.load(file), relative(file))
                root.int("schema", 0, 1..1)
                val entries = root.map("attributes")
                root.finish()
                require(entries.isNotEmpty()) { "${relative(file)}.attributes 不能为空" }
                entries.toSortedMap().forEach { (rawId, value) ->
                    val key = attributeKey(rawId)
                    require(!result.containsKey(key)) { "属性 ID 重复：$key" }
                    result[key] = parseAttribute(key, StrictObject(StrictObject.asMap(value, "$file.$rawId"), "$file.$rawId"))
                }
            }
        }
        return result
    }

    private fun parseAttribute(key: AttributeKey, node: StrictObject): CompiledAttributeDefinition {
        val boundsNode = StrictObject(node.map("bounds"), "attribute.${key.value}.bounds")
        val bounds = AttributeBounds(boundsNode.nullableDouble("min"), boundsNode.nullableDouble("max"))
        boundsNode.finish()
        val callbacks = parseCallbacks(node.map("callbacks"), "attribute.${key.value}.callbacks", requireScript = false)
        val definition = AttributeDefinition(
            key = key,
            name = node.requiredString("name"),
            description = node.string("description", "").orEmpty(),
            category = node.string("category", "general")!!,
            base = node.double("base", 0.0),
            bounds = bounds,
            format = when (node.string("format", "number")!!.lowercase()) {
                "number" -> AttributeFormat.NUMBER
                "integer" -> AttributeFormat.INTEGER
                "percent" -> AttributeFormat.PERCENT
                else -> throw IllegalArgumentException("attribute.${key.value}.format 无效")
            },
            roundingScale = node.int("rounding", 2, 0..12),
            priority = node.int("priority", 0),
            dependsOn = node.stringList("depends_on").map(::attributeKey).toSet()
        )
        node.finish()
        return CompiledAttributeDefinition(definition, callbacks)
    }

    private fun loadDamage(
        issues: ValidationCollector
    ): Pair<ArmorFormulaDefinition, Map<String, DamageChannelDefinition>>? {
        val files = yamlFiles(dataRoot.resolve("damage"))
        if (files.isEmpty()) {
            issues.error(dataRoot.resolve("damage"), "damage", "未找到伤害定义文件")
            return null
        }
        var armor: ArmorFormulaDefinition? = null
        val channels = linkedMapOf<String, DamageChannelDefinition>()
        files.forEach { file ->
            capture(file, "damage", issues) {
                val root = StrictObject(yaml.load(file), relative(file))
                root.int("schema", 0, 1..1)
                val formulaMap = root.map("formulas")
                if (formulaMap.containsKey("armor")) {
                    require(armor == null) { "护甲公式被重复定义" }
                    val node = StrictObject(StrictObject.asMap(formulaMap.getValue("armor"), "$file.formulas.armor"), "$file.formulas.armor")
                    val type = when (node.requiredString("type").lowercase()) {
                        "diminishing" -> ArmorFormulaType.DIMINISHING
                        else -> throw IllegalArgumentException("目前仅支持 diminishing 护甲公式")
                    }
                    armor = ArmorFormulaDefinition(
                        type,
                        node.double("constant", 100.0),
                        attributeKey(node.requiredString("defense")),
                        attributeKey(node.requiredString("percent-penetration")),
                        attributeKey(node.requiredString("flat-penetration"))
                    )
                    node.finish()
                    require(formulaMap.keys == setOf("armor")) { "存在未知伤害公式：${formulaMap.keys - "armor"}" }
                }
                root.map("channels").toSortedMap().forEach { (id, value) ->
                    require(ID.matches(id)) { "伤害通道 ID 无效：$id" }
                    require(!channels.containsKey(id)) { "伤害通道重复：$id" }
                    val node = StrictObject(StrictObject.asMap(value, "$file.channels.$id"), "$file.channels.$id")
                    channels[id] = DamageChannelDefinition(
                        id = id,
                        name = node.string("name", id)!!,
                        damageAttribute = node.string("damage-attribute", null)?.let(::attributeKey),
                        resistanceAttribute = node.string("resistance-attribute", null)?.let(::attributeKey),
                        amplificationAttribute = node.string("amplification-attribute", null)?.let(::attributeKey),
                        mitigation = node.string("mitigation", null),
                        canCrit = node.boolean("can-crit", false),
                        element = node.boolean("element", false),
                        color = node.string("color", null)
                    )
                    node.finish()
                }
                root.finish()
            }
        }
        if (armor == null) issues.error(null, "damage.formulas.armor", "缺少护甲公式")
        if (channels.isEmpty()) issues.error(null, "damage.channels", "至少需要一个伤害通道")
        return armor?.let { it to channels }
    }

    private fun loadSets(
        issues: ValidationCollector,
        attributes: Map<AttributeKey, CompiledAttributeDefinition>?
    ): Map<String, SetDefinition>? {
        val result = linkedMapOf<String, SetDefinition>()
        yamlFiles(dataRoot.resolve("items/sets")).forEach { file ->
            capture(file, "sets", issues) {
                val root = StrictObject(yaml.load(file), relative(file))
                root.int("schema", 1, 1..1)
                val id = namespacedId(root.requiredString("id"))
                require(!result.containsKey(id)) { "套装 ID 重复：$id" }
                val displayNode = StrictObject(root.map("display"), "$id.display")
                val name = displayNode.requiredString("name")
                val pieces = displayNode.map("pieces").mapValues { (pieceId, value) ->
                    require(pieceId.isNotBlank()) { "$id.pieces 包含空白的部件 ID" }
                    require(value is String && value.isNotBlank()) { "$id.pieces.$pieceId 必须是非空字符串" }
                    value
                }
                displayNode.finish()
                val counting = StrictObject(root.map("counting"), "$id.counting")
                require(counting.string("mode", "active_item_sources") == "active_item_sources") {
                    "$id.counting.mode 必须为 active_item_sources"
                }
                val duplicateInstanceOnce = when (counting.string("duplicate-instance", "once")) {
                    "once" -> true
                    "per-source" -> false
                    else -> throw IllegalArgumentException("$id.counting.duplicate-instance 必须为 once 或 per-source")
                }
                val allowDuplicatePiece = counting.boolean("allow-duplicate-piece-id", false)
                counting.finish()
                val bonuses = linkedMapOf<Int, SetBonusDefinition>()
                root.map("bonuses").forEach { (thresholdText, value) ->
                    val threshold = thresholdText.toIntOrNull()
                        ?: throw IllegalArgumentException("$id.bonuses.$thresholdText 必须是正整数")
                    require(threshold > 0) { "$id.bonuses.$threshold 必须大于零" }
                    val node = StrictObject(StrictObject.asMap(value, "$id.bonuses.$threshold"), "$id.bonuses.$threshold")
                    val bonusDisplay = StrictObject(node.map("display"), "$id.bonuses.$threshold.display")
                    val bonusName = bonusDisplay.requiredString("name")
                    val description = bonusDisplay.stringList("description")
                    require(description.isNotEmpty()) { "$id.bonuses.$threshold.display.description 不能为空" }
                    bonusDisplay.finish()
                    val modifiers = parseModifiers(node.map("modifiers"), "set:$id:$threshold", attributes)
                    val callbacks = parseCallbacks(node.map("callbacks"), "$id.bonuses.$threshold.callbacks", false)
                    node.finish()
                    bonuses[threshold] = SetBonusDefinition(
                        threshold,
                        modifiers,
                        callbacks,
                        SetBonusDisplayDefinition(bonusName, description)
                    )
                }
                require(bonuses.isNotEmpty()) { "$id.bonuses 不能为空" }
                root.finish()
                result[id] = SetDefinition(
                    id,
                    duplicateInstanceOnce,
                    allowDuplicatePiece,
                    bonuses,
                    SetDisplayDefinition(name, pieces)
                )
            }
        }
        return result
    }

    private fun loadEnhancement(issues: ValidationCollector): EnhancementDefinition? {
        val file = dataRoot.resolve("items/enhancement.yml")
        if (!Files.isRegularFile(file)) return null
        return capture(file, "items.enhancement", issues) {
            val root = StrictObject(yaml.load(file), relative(file))
            root.int("schema", 0, 1..1)
            val levels = root.map("levels").map { (rawLevel, rawValue) ->
                val level = rawLevel.toIntOrNull() ?: throw IllegalArgumentException("强化等级 $rawLevel 不是整数")
                require(level > 0) { "强化等级必须大于零" }
                val node = StrictObject(StrictObject.asMap(rawValue, "items.enhancement.levels.$level"), "items.enhancement.levels.$level")
                val success = node.double("success", 0.0)
                val downgrade = node.double("downgrade", 0.0)
                val destroy = node.double("destroy", 0.0)
                require(success in 0.0..1.0 && downgrade in 0.0..1.0 && destroy in 0.0..1.0) {
                    "强化概率必须位于 0 到 1 之间"
                }
                require(success + downgrade + destroy <= 1.0) { "成功、降级与销毁概率之和不能超过 1" }
                val multiplier = node.double("multiplier", 1.0).also { require(it > 0.0) }
                val cost = node.raw("cost")?.let {
                    parseItemRequirement(StrictObject.asMap(it, "items.enhancement.levels.$level.cost"), "items.enhancement.levels.$level.cost")
                }
                node.finish()
                level to EnhancementLevelDefinition(level, success, downgrade, destroy, multiplier, cost)
            }.toMap()
            require(levels.isNotEmpty()) { "items.enhancement.levels 不能为空" }
            val protection = StrictObject(root.map("protection-items"), "items.enhancement.protection-items")
            val preventDestroy = protection.string("prevent-destroy", null)?.also {
                require(it.isNotBlank()) { "items.enhancement.protection-items.prevent-destroy 不能为空" }
            }
            val preventDowngrade = protection.string("prevent-downgrade", null)?.also {
                require(it.isNotBlank()) { "items.enhancement.protection-items.prevent-downgrade 不能为空" }
            }
            protection.finish()
            root.finish()
            EnhancementDefinition(levels, preventDestroy, preventDowngrade)
        }
    }

    private fun loadSocketTools(issues: ValidationCollector): Map<String, SocketToolDefinition> {
        val result = linkedMapOf<String, SocketToolDefinition>()
        yamlFiles(dataRoot.resolve("items/socket-tools")).forEach { file ->
            capture(file, "items.socket-tools", issues) {
                val root = StrictObject(yaml.load(file), relative(file))
                root.int("schema", 0, 1..1)
                val id = namespacedId(root.requiredString("id"))
                require(!result.containsKey(id)) { "打孔工具 ID 重复：$id" }
                // Overture 物品 ID 是由提供者管理的原始 ID（例如 "defense_socket_drill"），
                // 并非 Bukkit NamespacedKey。必须原样保留配置值，因为
                // OvertureAPI.getOvertureId 返回的正是 YAML 顶层 ID。
                val overtureItem = root.requiredString("overture-item").also {
                    require(it.isNotBlank()) { "$id.overture-item 不能为空" }
                }
                val name = root.requiredString("name")
                val accepts = root.stringList("accepts").mapTo(linkedSetOf()) { raw ->
                    require(raw == "*" || ID.matches(raw)) {
                        "$id.accepts 包含无效的宝石类别 $raw"
                    }
                    raw
                }
                require(accepts.isNotEmpty()) { "$id.accepts 不能为空" }
                root.finish()
                result[id] = SocketToolDefinition(id, overtureItem, name, accepts)
            }
        }
        return result
    }

    private fun loadSocketRemoval(issues: ValidationCollector): SocketRemovalDefinition? {
        val file = dataRoot.resolve("items/socket-removal.yml")
        if (!Files.isRegularFile(file)) return null
        return capture(file, "items.socket-removal", issues) {
            val root = StrictObject(yaml.load(file), relative(file))
            root.int("schema", 0, 1..1)
            val tool = parseItemRequirement(root.map("tool"), "items.socket-removal.tool")
            root.finish()
            SocketRemovalDefinition(tool)
        }
    }

    private fun parseModifiers(
        values: Map<String, Any?>,
        ownerId: String,
        attributes: Map<AttributeKey, CompiledAttributeDefinition>?
    ): List<AttributeModifier> = values.toSortedMap().map { (rawAttribute, value) ->
        val key = attributeKey(rawAttribute)
        require(attributes == null || attributes.containsKey(key)) { "$ownerId 引用了未知属性 $key" }
        val node = StrictObject(StrictObject.asMap(value, "$ownerId.modifiers.$rawAttribute"), "$ownerId.modifiers.$rawAttribute")
        val modifier = AttributeModifier(
            id = rawAttribute,
            attribute = key,
            operation = AttributeOperation.parse(node.string("operation", "add")!!),
            value = node.numberOrPercent("value"),
            priority = node.int("priority", 0),
            description = node.string("description", null)
        )
        node.finish()
        modifier
    }

    private fun parseCallbacks(
        values: Map<String, Any?>,
        path: String,
        requireScript: Boolean
    ): List<CallbackDefinition> = values.toSortedMap().map { (id, value) ->
        require(ID.matches(id)) { "$path 包含无效的回调 ID $id" }
        val node = StrictObject(StrictObject.asMap(value, "$path.$id"), "$path.$id")
        val trigger = namespacedId(node.requiredString("trigger"))
        val priority = node.int("priority", 0)
        val inline = node.string("script", null)
        val file = node.string("file", null)?.also { safeRelative(it, "$path.$id.file") }
        require(inline == null || file == null) { "$path.$id 不能同时配置 script 与 file" }
        val actions = node.list("actions").mapIndexed { index, action ->
            StrictObject.asMap(action, "$path.$id.actions[$index]")
        }
        if (requireScript) require(inline != null || file != null) { "$path.$id 必须配置 script 或 file" }
        val conditions = node.list("conditions").mapIndexed { index, condition ->
            StrictObject.asMap(condition, "$path.$id.conditions[$index]")
        }
        val metadata = linkedMapOf<String, Any?>()
        node.raw("when")?.let { metadata["when"] = StrictObject.asMap(it, "$path.$id.when") }
        node.raw("interval")?.let { metadata["interval"] = it }
        node.finish()
        CallbackDefinition(
            id = id,
            trigger = trigger,
            priority = priority,
            script = if (inline != null || file != null) ScriptDefinition("$path.$id", inline, file) else null,
            conditions = conditions,
            actions = actions,
            metadata = metadata
        )
    }

    private fun loadGeneric(
        relativeDirectory: String,
        allowedFields: Set<String>,
        issues: ValidationCollector
    ): Map<String, GenericDefinition> {
        val result = linkedMapOf<String, GenericDefinition>()
        yamlFiles(dataRoot.resolve(relativeDirectory)).forEach { file ->
            capture(file, relativeDirectory, issues) {
                val values = yaml.load(file)
                val schema = values["schema"] ?: 1
                require((schema as? Number)?.toInt() == 1) { "${relative(file)}.schema 必须等于 1" }
                val rawId = values["id"] as? String ?: throw IllegalArgumentException("缺少必填项 ${relative(file)}.id")
                require(ID.matches(rawId)) { "${relative(file)} 中的 ID 无效：$rawId" }
                val id = namespacedId(rawId)
                require(!result.containsKey(id)) { "$relativeDirectory 中存在重复 ID：$id" }
                val unknown = values.keys - allowedFields - "schema" - "id"
                require(unknown.isEmpty()) { "${relative(file)} 包含未知字段：${unknown.sorted()}" }
                result[id] = GenericDefinition(id, relative(file), values.toMap())
            }
        }
        return result
    }

    private fun validateGenericDefinitions(
        issues: ValidationCollector,
        attributes: Map<AttributeKey, CompiledAttributeDefinition>?,
        affixes: Map<String, GenericDefinition>,
        skills: Map<String, GenericDefinition>,
        gems: Map<String, GenericDefinition>,
        reactions: Map<String, GenericDefinition>,
        resonances: Map<String, GenericDefinition>,
        talents: Map<String, GenericDefinition>,
        statuses: Map<String, GenericDefinition>,
        environments: Map<String, GenericDefinition>
    ) {
        validateEach(affixes, issues) { id, values -> validateAffix(id, values, attributes) }
        validateEach(skills, issues) { id, values -> validateSkill(id, values) }
        validateEach(gems, issues) { id, values -> validateGem(id, values, attributes) }
        validateEach(reactions, issues) { id, values -> validateReaction(id, values) }
        validateEach(resonances, issues) { id, values -> validateResonance(id, values, attributes) }
        validateEach(talents, issues) { id, values -> validateTalent(id, values, attributes) }
        validateEach(statuses, issues) { id, values -> validateStatus(id, values, attributes) }
        validateEach(environments, issues) { id, values -> validateEnvironment(id, values, attributes) }
    }

    private fun validateEach(
        definitions: Map<String, GenericDefinition>,
        issues: ValidationCollector,
        validator: (String, Map<String, Any?>) -> Unit
    ) {
        definitions.values.forEach { definition ->
            val source = dataRoot.resolve(definition.sourcePath)
            capture(source, definition.id, issues) { validator(definition.id, definition.values) }
        }
    }

    private fun genericNode(id: String, values: Map<String, Any?>) =
        StrictObject(values - setOf("schema", "id"), id)

    private fun validateAffix(
        id: String,
        values: Map<String, Any?>,
        attributes: Map<AttributeKey, CompiledAttributeDefinition>?
    ) {
        val node = genericNode(id, values)
        node.requiredString("name")
        node.string("rarity", "common")
        node.string("category", "general")
        val maximum = node.int("max-level", 1, 1..10_000)
        node.stringList("tags").forEach { require(ID.matches(it)) { "$id.tags 包含无效标签 $it" } }
        node.string("exclusive-group", null)?.let { require(ID.matches(it)) { "$id.exclusive-group 无效" } }
        val levels = node.map("levels")
        require(levels.isNotEmpty()) { "$id.levels 不能为空" }
        val levelParameters = linkedMapOf<Int, Set<String>>()
        levels.forEach { (rawLevel, rawValues) ->
            val level = rawLevel.toIntOrNull() ?: throw IllegalArgumentException("$id.levels.$rawLevel 必须是整数")
            require(level in 1..maximum) { "$id.levels.$rawLevel 必须位于 1 到 $maximum 之间" }
            val levelValues = StrictObject.asMap(rawValues, "$id.levels.$level")
            levelParameters[level] = levelValues.keys - "modifiers"
            levelValues.forEach { (parameter, value) ->
                if (parameter == "modifiers") validateTemplateModifiers(value, "$id.levels.$level.modifiers", attributes, true)
                else StrictObject.parseFiniteNumber(value, "$id.levels.$level.$parameter")
            }
        }
        val displayValues = node.map("display")
        if (displayValues.isNotEmpty()) {
            val display = StrictObject(displayValues, "$id.display")
            val description = display.stringList("description")
            require(description.isNotEmpty() && description.all(String::isNotBlank)) {
                "$id.display.description 至少要包含一行非空文本"
            }
            val commonParameters = levelParameters.values.reduce(Set<String>::intersect) + "level"
            description.forEachIndexed { index, line ->
                val tokens = Regex("\\{[^{}]+}").findAll(line).map { it.value }.toList()
                tokens.forEach { token ->
                    val match = AFFIX_PLACEHOLDER.matchEntire(token)
                        ?: throw IllegalArgumentException("$id.display.description[$index] 包含无效占位符 $token")
                    require(match.groupValues[1] in commonParameters) {
                        "$id.display.description[$index] 引用了并非每个等级都存在的参数 ${match.groupValues[1]}"
                    }
                }
            }
            display.finish()
        }
        validateTemplateModifiers(node.map("passive"), "$id.passive", attributes, true)
        parseCallbacks(node.map("callbacks"), "$id.callbacks", false)
        node.finish()
    }

    private fun validateSkill(id: String, values: Map<String, Any?>) {
        val node = genericNode(id, values)
        require(node.string("provider", "symphony:aria") in setOf("aria", "symphony:aria")) {
            "$id.provider 必须为 aria 或 symphony:aria"
        }
        node.requiredString("name")
        node.string("description", "")
        node.long("cooldown-ms", 0L, 0L..86_400_000L)
        node.int("max-level", 1, 1..10_000)
        val targeting = StrictObject(node.map("targeting"), "$id.targeting")
        require(targeting.string("type", "self") in setOf("self", "single_enemy", "single_ally")) {
            "$id.targeting.type 不受支持"
        }
        targeting.nullableDouble("range")?.also { require(it >= 0.0) { "$id.targeting.range 不能为负数" } }
        targeting.finish()
        val activationValues = node.map("activation")
        if (activationValues.isNotEmpty()) {
            val activation = StrictObject(activationValues, "$id.activation")
            priv.seventeen.artist.symphony.engine.definition.SkillActivationInput.parse(
                activation.string("input", "right_click")!!
            )
            priv.seventeen.artist.symphony.engine.definition.SkillActivationSource.parse(
                activation.string("source", "main_hand")!!
            )
            priv.seventeen.artist.symphony.engine.definition.SkillCancelPolicy.parse(
                activation.string("cancel-event", "on_success")!!
            )
            activation.int("priority", 0, -1_000_000..1_000_000)
            activation.finish()
        }
        val inline = node.string("script", null)
        val file = node.string("file", null)?.also { safeRelative(it, "$id.file") }
        require(inline == null || file == null) { "$id 不能同时配置 script 与 file" }
        val actions = node.list("actions").mapIndexed { index, action -> StrictObject.asMap(action, "$id.actions[$index]") }
        parseCallbacks(node.map("callbacks"), "$id.callbacks", false)
        require(inline != null || file != null || actions.isNotEmpty()) { "$id 必须配置 script、file 或 actions" }
        node.finish()
    }

    private fun validateGem(
        id: String,
        values: Map<String, Any?>,
        attributes: Map<AttributeKey, CompiledAttributeDefinition>?
    ) {
        val node = genericNode(id, values)
        node.requiredString("overture-item")
        node.requiredString("name")
        val category = node.requiredString("category")
        require(ID.matches(category)) { "$id.category 无效" }
        node.stringList("tags").forEach { require(ID.matches(it)) { "$id.tags 包含无效标签 $it" } }
        val maximum = node.int("max-level", 1, 1..10_000)
        validateModifierLevels(id, node.map("levels"), maximum, attributes)
        node.finish()
    }

    private fun validateModifierLevels(
        id: String,
        levels: Map<String, Any?>,
        maximum: Int,
        attributes: Map<AttributeKey, CompiledAttributeDefinition>?
    ) {
        require(levels.isNotEmpty()) { "$id.levels 不能为空" }
        levels.forEach { (rawLevel, rawValue) ->
            val level = rawLevel.toIntOrNull() ?: throw IllegalArgumentException("$id.levels.$rawLevel 必须是整数")
            require(level in 1..maximum) { "$id.levels.$rawLevel 必须位于 1 到 $maximum 之间" }
            val entry = StrictObject(StrictObject.asMap(rawValue, "$id.levels.$level"), "$id.levels.$level")
            validateTemplateModifiers(entry.map("modifiers"), "$id.levels.$level.modifiers", attributes, false)
            entry.finish()
        }
    }

    private fun validateTemplateModifiers(
        raw: Any?,
        path: String,
        attributes: Map<AttributeKey, CompiledAttributeDefinition>?,
        placeholders: Boolean
    ) {
        val values = when (raw) {
            is Map<*, *> -> StrictObject.asMap(raw, path)
            else -> throw IllegalArgumentException("$path 必须是映射")
        }
        values.forEach { (rawAttribute, rawValue) ->
            val key = attributeKey(rawAttribute)
            require(attributes == null || key in attributes) { "$path 引用了未知属性 $key" }
            val modifier = StrictObject(StrictObject.asMap(rawValue, "$path.$rawAttribute"), "$path.$rawAttribute")
            AttributeOperation.parse(modifier.string("operation", "add")!!)
            val value = modifier.raw("value") ?: throw IllegalArgumentException("缺少必填项 $path.$rawAttribute.value")
            if (!(placeholders && value is String && PLACEHOLDER.matches(value))) {
                StrictObject.parseNumberOrPercent(value, "$path.$rawAttribute.value")
            }
            modifier.int("priority", 0)
            modifier.string("description", null)
            modifier.finish()
        }
    }

    private fun validateReaction(id: String, values: Map<String, Any?>) {
        val node = genericNode(id, values)
        node.requiredString("trigger")
        node.requiredString("aura")
        require(node.string("type", "amplify") in setOf("amplify", "add")) { "$id.type 必须为 amplify 或 add" }
        node.double("multiplier", 1.0).also { require(it >= 0.0) { "$id.multiplier 不能为负数" } }
        node.double("gauge-consume", 0.0).also { require(it >= 0.0) { "$id.gauge-consume 不能为负数" } }
        val effects = StrictObject(node.map("effects"), "$id.effects")
        effects.string("particle", null)
        effects.string("sound", null)
        effects.finish()
        node.finish()
    }

    private fun validateReactionReferences(
        reactions: Map<String, GenericDefinition>,
        channels: Map<String, DamageChannelDefinition>
    ) {
        reactions.values.forEach { reaction ->
            val trigger = reaction.values["trigger"]?.toString()
                ?: throw IllegalArgumentException("缺少必填项 ${reaction.id}.trigger")
            val aura = reaction.values["aura"]?.toString()
                ?: throw IllegalArgumentException("缺少必填项 ${reaction.id}.aura")
            val triggerChannel = channels[trigger]
                ?: throw IllegalArgumentException("${reaction.id}.trigger 引用了未知伤害通道 $trigger")
            val auraChannel = channels[aura]
                ?: throw IllegalArgumentException("${reaction.id}.aura 引用了未知伤害通道 $aura")
            require(triggerChannel.element) { "${reaction.id}.trigger 对应的伤害通道 $trigger 不是元素通道" }
            require(auraChannel.element) { "${reaction.id}.aura 对应的伤害通道 $aura 不是元素通道" }
        }
    }

    private fun validateResonance(
        id: String,
        values: Map<String, Any?>,
        attributes: Map<AttributeKey, CompiledAttributeDefinition>?
    ) {
        val node = genericNode(id, values)
        node.requiredString("name")
        validateResonanceCondition(id, node.map("condition"), attributes)
        validateTemplateModifiers(node.map("modifiers"), "$id.modifiers", attributes, false)
        parseCallbacks(node.map("callbacks"), "$id.callbacks", false)
        node.finish()
    }

    private fun validateResonanceCondition(
        id: String,
        values: Map<String, Any?>,
        attributes: Map<AttributeKey, CompiledAttributeDefinition>?
    ) {
        val condition = StrictObject(values, "$id.condition")
        when (condition.requiredString("type")) {
            "affix_tag_count" -> {
                condition.requiredString("tag")
                condition.int("count", 1, 1..1_000_000)
            }
            "set_count" -> {
                condition.requiredString("set").let(::namespacedId)
                condition.int("count", 1, 1..1_000_000)
            }
            "attribute" -> validateAttributeComparison(condition, "$id.condition", attributes)
            else -> throw IllegalArgumentException("$id.condition.type 不受支持")
        }
        condition.finish()
    }

    private fun validateTalent(
        id: String,
        values: Map<String, Any?>,
        attributes: Map<AttributeKey, CompiledAttributeDefinition>?
    ) {
        val node = genericNode(id, values)
        node.requiredString("name")
        val gate = StrictObject(node.map("gate"), "$id.gate")
        var groups = 0
        listOf("all", "any", "none").forEach { group ->
            gate.raw(group)?.let { raw ->
                groups++
                require(raw is List<*>) { "$id.gate.$group 必须是列表" }
                raw.forEachIndexed { index, value ->
                    val comparison = StrictObject(StrictObject.asMap(value, "$id.gate.$group[$index]"), "$id.gate.$group[$index]")
                    validateAttributeComparison(comparison, "$id.gate.$group[$index]", attributes)
                    comparison.finish()
                }
            }
        }
        require(groups > 0) { "$id.gate 至少需要配置 all、any 或 none 中的一项" }
        gate.finish()
        validateTemplateModifiers(node.map("modifiers"), "$id.modifiers", attributes, false)
        parseCallbacks(node.map("callbacks"), "$id.callbacks", false)
        node.finish()
    }

    private fun validateAttributeComparison(
        node: StrictObject,
        path: String,
        attributes: Map<AttributeKey, CompiledAttributeDefinition>?
    ) {
        val key = attributeKey(node.requiredString("attribute"))
        require(attributes == null || key in attributes) { "$path 引用了未知属性 $key" }
        require(node.string("operator", ">=") in COMPARISON_OPERATORS) { "$path.operator 无效" }
        node.double("value", 0.0)
    }

    private fun validateStatus(
        id: String,
        values: Map<String, Any?>,
        attributes: Map<AttributeKey, CompiledAttributeDefinition>?
    ) {
        val node = genericNode(id, values)
        node.requiredString("name")
        node.int("max-stacks", 1, 1..10_000)
        node.long("duration-ms", 1L, 1L..86_400_000L)
        require(node.string("decay", "individual") == "individual") { "$id.decay 必须为 individual" }
        node.long("tick-ms", 1000L, 50L..86_400_000L)
        val perStack = StrictObject(node.map("per-stack"), "$id.per-stack")
        validateTemplateModifiers(perStack.map("modifiers"), "$id.per-stack.modifiers", attributes, false)
        perStack.finish()
        parseCallbacks(node.map("callbacks"), "$id.callbacks", false)
        node.finish()
    }

    private fun validateEnvironment(
        id: String,
        values: Map<String, Any?>,
        attributes: Map<AttributeKey, CompiledAttributeDefinition>?
    ) {
        val node = genericNode(id, values)
        node.requiredString("name")
        val whenNode = StrictObject(node.map("when"), "$id.when")
        whenNode.stringList("biomes")
        whenNode.stringList("worlds")
        whenNode.boolean("outdoor", false)
        whenNode.raw("weather")?.let { raw ->
            require(raw is String && raw.lowercase() in setOf("clear", "rain", "thunder")) {
                "$id.when.weather 必须为 clear、rain 或 thunder"
            }
        }
        whenNode.raw("time")?.let { raw ->
            val time = StrictObject(StrictObject.asMap(raw, "$id.when.time"), "$id.when.time")
            time.int("from", 0, 0..23_999)
            time.int("to", 23_999, 0..23_999)
            time.finish()
        }
        whenNode.finish()
        validateTemplateModifiers(node.map("modifiers"), "$id.modifiers", attributes, false)
        parseCallbacks(node.map("callbacks"), "$id.callbacks", false)
        node.finish()
    }

    private fun validateReferences(
        attributes: Map<AttributeKey, CompiledAttributeDefinition>,
        channels: Map<String, DamageChannelDefinition>,
        sets: Map<String, SetDefinition>
    ) {
        channels.values.forEach { channel ->
            listOfNotNull(channel.damageAttribute, channel.resistanceAttribute, channel.amplificationAttribute).forEach {
                require(attributes.containsKey(it)) { "伤害通道 ${channel.id} 引用了未知属性 $it" }
            }
            if (channel.mitigation != null) require(channel.mitigation == "armor") {
                "伤害通道 ${channel.id} 引用了未知减伤方式 ${channel.mitigation}"
            }
        }
        sets.values.flatMap { it.bonuses.values }.flatMap { it.modifiers }.forEach {
            require(attributes.containsKey(it.attribute)) { "套装修改器引用了未知属性 ${it.attribute}" }
        }
    }

    private fun compileInteractions(
        definitions: Map<String, GenericDefinition>,
        attributes: Map<AttributeKey, CompiledAttributeDefinition>
    ): Map<String, InteractionDefinition> = definitions.toSortedMap().mapValues { (id, generic) ->
        val values = generic.values
        val type = InteractionType.valueOf((values["type"] as? String
            ?: throw IllegalArgumentException("缺少必填项 $id.type")).uppercase())
        val source = attributeKey(values["source"] as? String
            ?: throw IllegalArgumentException("缺少必填项 $id.source"))
        val target = attributeKey(values["target"] as? String
            ?: throw IllegalArgumentException("缺少必填项 $id.target"))
        require(source in attributes) { "$id 引用了未知的来源属性 $source" }
        require(target in attributes) { "$id 引用了未知的目标属性 $target" }
        val threshold = (values["threshold"] as? Number)?.toDouble() ?: 0.0
        val ratio = (values["ratio"] as? Number)?.toDouble()
            ?: throw IllegalArgumentException("$id.ratio 必须是数字")
        InteractionDefinition(id, type, source, target, threshold, ratio)
    }

    private fun compileAffixPools(
        definitions: Map<String, GenericDefinition>,
        affixes: Map<String, GenericDefinition>
    ): Map<String, AffixPoolDefinition> = definitions.toSortedMap().mapValues { (id, generic) ->
        val root = StrictObject(generic.values - setOf("schema", "id"), "affix-pool.$id")
        val maxAffixes = root.int("max-affixes", 1, 1..64)
        val priority = root.int("priority", 0)
        val applies = StrictObject(root.map("applies-to"), "affix-pool.$id.applies-to")
        val overtureItems = applies.stringList("overture-items", listOf("*")).toSet()
        val materials = applies.stringList("materials").mapTo(linkedSetOf(), String::uppercase)
        require(overtureItems.none(String::isBlank)) { "affix-pool.$id.applies-to.overture-items 包含空白 ID" }
        require(materials.all(MATERIAL_NAME::matches)) { "affix-pool.$id.applies-to.materials 包含无效材质" }
        applies.finish()
        val cost = root.raw("cost")?.let {
            parseItemRequirement(StrictObject.asMap(it, "affix-pool.$id.cost"), "affix-pool.$id.cost")
        }
        val entries = root.list("entries").mapIndexed { index, raw ->
            val path = "affix-pool.$id.entries[$index]"
            val node = StrictObject(StrictObject.asMap(raw, path), path)
            val affixId = namespacedId(node.requiredString("affix"))
            require(affixId in affixes) { "$path 引用了未知词条 $affixId" }
            val weight = node.double("weight", 1.0)
            val affix = affixes.getValue(affixId)
            val definitionMax = (affix.values["max-level"] as? Number)?.toInt() ?: 1
            val minLevel = node.int("min-level", 1, 1..definitionMax)
            val maxLevel = node.int("max-level", definitionMax, minLevel..definitionMax)
            node.finish()
            AffixPoolEntryDefinition(affixId, weight, minLevel, maxLevel)
        }
        root.finish()
        AffixPoolDefinition(id, maxAffixes, cost, entries, priority, overtureItems, materials)
    }

    private fun parseItemRequirement(values: Map<String, Any?>, path: String): ItemRequirementDefinition {
        val node = StrictObject(values, path)
        val material = node.string("material", null)?.uppercase()
        val overtureItem = node.string("overture-item", null)
        val amount = node.int("amount", 1, 1..64 * 54)
        node.finish()
        if (material != null) require(MATERIAL_NAME.matches(material)) { "$path.material 无效" }
        if (overtureItem != null) require(overtureItem.isNotBlank()) { "$path.overture-item 不能为空" }
        return ItemRequirementDefinition(material, overtureItem, amount)
    }

    private fun addInteractionDependencies(
        attributes: Map<AttributeKey, CompiledAttributeDefinition>,
        interactions: Map<String, InteractionDefinition>
    ): Map<AttributeKey, CompiledAttributeDefinition> {
        val byTarget = interactions.values.groupBy(InteractionDefinition::target)
        return attributes.mapValues { (key, compiled) ->
            val added = byTarget[key].orEmpty().mapTo(linkedSetOf(), InteractionDefinition::source)
            if (added.isEmpty()) compiled else compiled.copy(
                definition = compiled.definition.copy(dependsOn = compiled.definition.dependsOn + added)
            )
        }
    }

    private fun yamlFiles(directory: Path): List<Path> {
        if (!Files.isDirectory(directory)) return emptyList()
        return Files.walk(directory).use { stream ->
            stream.filter { Files.isRegularFile(it) && it.fileName.toString().endsWith(".yml", true) }
                .sorted()
                .toList()
        }
    }

    private fun attributeKey(raw: String): AttributeKey = AttributeKey(namespacedId(raw))

    private fun namespacedId(raw: String): String {
        val value = if (':' in raw) raw else "symphony:$raw"
        require(NAMESPACED_ID.matches(value)) { "命名空间 ID 无效：$value" }
        return value
    }

    private fun safeRelative(raw: String, path: String): Path {
        val candidate = Path.of(raw)
        require(!candidate.isAbsolute && candidate.none { it.toString() == ".." }) { "$path 必须位于插件目录内" }
        return candidate.normalize()
    }

    private fun relative(file: Path): String = dataRoot.relativize(file).toString().replace('\\', '/')

    private inline fun <T> capture(
        source: Path,
        path: String,
        issues: ValidationCollector,
        block: () -> T
    ): T? = try {
        block()
    } catch (error: Exception) {
        issues.error(source, path, error.message ?: "配置无效", error)
        null
    }

    companion object {
        private val ID = Regex("^[a-z0-9._/-]+$")
        private val NAMESPACED_ID = Regex("^[a-z0-9._-]+:[a-z0-9._/-]+$")
        private val AFFIX_FIELDS = setOf("name", "rarity", "category", "max-level", "levels", "passive", "callbacks", "tags", "exclusive-group", "display")
        private val AFFIX_POOL_FIELDS = setOf("max-affixes", "cost", "entries", "priority", "applies-to")
        private val SKILL_FIELDS = setOf("provider", "name", "description", "cooldown-ms", "max-level", "targeting", "activation", "script", "file", "actions", "callbacks")
        private val GEM_FIELDS = setOf("overture-item", "name", "category", "tags", "max-level", "levels")
        private val INTERACTION_FIELDS = setOf("type", "source", "threshold", "target", "ratio")
        private val REACTION_FIELDS = setOf("trigger", "aura", "type", "multiplier", "gauge-consume", "effects")
        private val RESONANCE_FIELDS = setOf("condition", "modifiers", "callbacks", "name")
        private val TALENT_FIELDS = setOf("gate", "modifiers", "callbacks", "name")
        private val STATUS_FIELDS = setOf("name", "max-stacks", "duration-ms", "decay", "tick-ms", "per-stack", "callbacks")
        private val ENVIRONMENT_FIELDS = setOf("name", "when", "modifiers", "callbacks")
        private val PLACEHOLDER = Regex("^\\{[a-zA-Z0-9_.-]+}$")
        private val COMPARISON_OPERATORS = setOf(">", ">=", "<", "<=", "==", "=", "!=", "<>")
        private val MATERIAL_NAME = Regex("^[A-Z0-9_]+$")
        private val COMBAT_POWER_BUILT_INS = setOf(
            "level", "experience", "source_count", "item_source_count",
            "set_count", "set_piece_count", "set_tier_count",
            "skill_count", "affix_count", "gem_count",
            "enhancement_total", "enhancement_max"
        )
    }
}
