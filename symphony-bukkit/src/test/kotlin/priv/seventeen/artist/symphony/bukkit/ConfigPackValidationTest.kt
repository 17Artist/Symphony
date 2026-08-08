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

package priv.seventeen.artist.symphony.bukkit

import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.StandardCopyOption
import java.lang.reflect.Proxy
import kotlin.test.Test
import kotlin.test.assertIs
import kotlin.test.assertNotNull
import kotlin.test.assertTrue
import kotlin.test.fail
import org.bukkit.NamespacedKey
import priv.seventeen.artist.aria.Aria
import org.junit.jupiter.api.Assumptions.assumeTrue
import org.junit.jupiter.api.io.TempDir
import priv.seventeen.artist.overture.api.component.ComponentDecodeContext
import priv.seventeen.artist.overture.api.component.ComponentDecodeResult
import priv.seventeen.artist.overture.api.data.ItemDataNode
import priv.seventeen.artist.symphony.api.attribute.AttributeKey
import priv.seventeen.artist.symphony.engine.config.DefinitionLoader
import priv.seventeen.artist.symphony.engine.config.LanguageBundle
import priv.seventeen.artist.symphony.engine.config.StrictYaml
import priv.seventeen.artist.symphony.engine.definition.DefinitionRepository
import priv.seventeen.artist.symphony.engine.definition.CallbackDefinition
import priv.seventeen.artist.symphony.engine.definition.GenericDefinition
import priv.seventeen.artist.symphony.api.attribute.AttributeService
import priv.seventeen.artist.symphony.api.damage.DamageService
import priv.seventeen.artist.symphony.bukkit.script.AriaCallbackRuntime
import priv.seventeen.artist.symphony.bukkit.script.CallbackActivationResolver
import priv.seventeen.artist.symphony.bukkit.script.ConfiguredCallbackRuntime
import priv.seventeen.artist.symphony.bukkit.script.ConfiguredCallbackSchema
import priv.seventeen.artist.symphony.engine.definition.DefinitionSnapshot
import priv.seventeen.artist.symphony.engine.trigger.EntityTriggerContext
import priv.seventeen.artist.symphony.overture.component.AttributeComponentCodec
import priv.seventeen.artist.symphony.overture.component.OffhandComponentCodec
import priv.seventeen.artist.symphony.overture.component.SetComponentCodec
import priv.seventeen.artist.symphony.overture.component.SkillComponentCodec
import priv.seventeen.artist.symphony.overture.component.SocketComponentCodec

/** 供 ai/skill/write-symphony-config 使用、与正式环境行为一致的校验入口。 */
class ConfigPackValidationTest {
    @TempDir
    lateinit var directory: Path

    @Test
    fun `external configuration pack satisfies Symphony and Overture contracts`() {
        val configured = System.getProperty("symphony.config.pack")
        assumeTrue(!configured.isNullOrBlank(), "run through the validateConfigPack task")
        val pack = Path.of(configured).toAbsolutePath().normalize()
        assertTrue(Files.isDirectory(pack), "configuration pack does not exist: $pack")
        val packSymphony = pack.resolve("Symphony")
        assertTrue(Files.isDirectory(packSymphony), "configuration pack must contain Symphony/")

        val resources = Path.of("src", "main", "resources").toAbsolutePath().normalize()
        val assets = resources.resolve("assets")
        val showcase = resources.resolve("showcase/prismatic-arsenal")
        copyTree(assets, directory, replace = true)
        copyTree(showcase.resolve("symphony"), directory, replace = true)
        copyTree(packSymphony, directory, replace = true)

        val loaded = DefinitionLoader(directory).load()
        assertTrue(
            loaded.report.valid,
            loaded.report.issues.joinToString("\n") { "${it.source}:${it.path}: ${it.message}" }
        )
        val snapshot = assertNotNull(loaded.snapshot)
        val definitions = DefinitionRepository(snapshot)
        val language = LanguageBundle.load(directory.resolve("language.yml"))
        validateSymphonyPlayerText(showcase.resolve("symphony"))
        validateSymphonyPlayerText(packSymphony)
        validateCallbacksAndScripts(snapshot, directory.resolve("scripts"))
        validateCallbackReferences(snapshot)

        val overtureRoots = buildList {
            add(showcase.resolve("overture"))
            val supplied = pack.resolve("Overture")
            if (Files.exists(supplied)) {
                assertTrue(Files.isDirectory(supplied), "Overture must be a directory")
                add(supplied)
            }
        }
        val displays = loadDisplays(overtureRoots)
        val items = loadItems(overtureRoots)
        validateComponents(items, definitions, language)
        validateItemDisplays(items, displays)
        validateConditionalDisplays(displays)
        validateGroups(overtureRoots)
        validateCrossReferences(snapshot, items.keys)
    }

    private fun validateCallbacksAndScripts(
        snapshot: DefinitionSnapshot,
        scriptsRoot: Path
    ) {
        val schema = object : ConfiguredCallbackRuntime {
            override fun validateConditions(ownerId: String, conditions: List<Map<String, Any?>>) =
                ConfiguredCallbackSchema.validateConditions(ownerId, conditions)

            override fun validateActions(ownerId: String, actions: List<Map<String, Any?>>) =
                ConfiguredCallbackSchema.validateActions(ownerId, actions)

            override fun test(
                ownerId: String,
                conditions: List<Map<String, Any?>>,
                context: EntityTriggerContext
            ): Boolean = error("validation must not execute callbacks")

            override fun execute(
                ownerId: String,
                actions: List<Map<String, Any?>>,
                context: EntityTriggerContext
            ): Unit = error("validation must not execute callbacks")
        }
        AriaCallbackRuntime(
            scriptsRoot = scriptsRoot,
            attributes = unusedInterface<AttributeService>(),
            damageService = unusedInterface<DamageService>(),
            callbacks = schema,
            activationResolver = { _, _ -> emptyMap() },
            slowWarningMillis = Long.MAX_VALUE,
            scriptCompiler = { id, source -> Aria.compile(id, source) }
        ).prepare(snapshot)
    }

    private inline fun <reified T> unusedInterface(): T {
        val type = T::class.java
        require(type.isInterface) { "${type.name} is not an interface" }
        @Suppress("UNCHECKED_CAST")
        return Proxy.newProxyInstance(type.classLoader, arrayOf(type)) { _, method, _ ->
            error("validation unexpectedly invoked ${type.name}.${method.name}")
        } as T
    }

    private fun validateCallbackReferences(
        snapshot: DefinitionSnapshot
    ) {
        fun validate(owner: String, callback: CallbackDefinition) {
            callback.conditions.forEachIndexed { index, condition ->
                validateConditionReferences(snapshot, condition, "$owner.${callback.id}.conditions[$index]")
            }
            callback.actions.forEachIndexed { index, action ->
                validateActionReferences(snapshot, action, "$owner.${callback.id}.actions[$index]")
            }
        }

        snapshot.attributes.forEach { (key, definition) ->
            definition.callbacks.forEach { validate("attribute.${key.value}", it) }
        }
        snapshot.sets.forEach { (setId, set) ->
            set.bonuses.forEach { (threshold, bonus) ->
                bonus.callbacks.forEach { validate("set.$setId.$threshold", it) }
            }
        }

        val genericGroups = listOf(
            snapshot.affixes,
            snapshot.skills,
            snapshot.statuses,
            snapshot.resonances,
            snapshot.talents,
            snapshot.environments
        )
        genericGroups.forEach { definitions ->
            definitions.values.forEach { definition ->
                validateGenericCallbackReferences(snapshot, definition)
            }
        }
        snapshot.skills.values.forEach { skill ->
            mapList(skill.values["actions"]).forEachIndexed { index, action ->
                validateActionReferences(snapshot, action, "${skill.id}.actions[$index]")
            }
        }
    }

    private fun validateGenericCallbackReferences(
        snapshot: DefinitionSnapshot,
        definition: GenericDefinition
    ) {
        val callbacks = definition.values["callbacks"] as? Map<*, *> ?: return
        callbacks.forEach { (rawId, rawDefinition) ->
            val id = rawId.toString()
            val values = stringMap(rawDefinition)
            mapList(values["conditions"]).forEachIndexed { index, condition ->
                validateConditionReferences(snapshot, condition, "${definition.id}.$id.conditions[$index]")
            }
            mapList(values["actions"]).forEachIndexed { index, action ->
                validateActionReferences(snapshot, action, "${definition.id}.$id.actions[$index]")
            }
        }
    }

    private fun validateConditionReferences(
        snapshot: DefinitionSnapshot,
        condition: Map<String, Any?>,
        path: String
    ) {
        when (condition["type"]?.toString()) {
            "attribute" -> {
                val key = AttributeKey(namespaced(condition["attribute"].toString()))
                assertTrue(key in snapshot.attributes, "$path references unknown attribute $key")
            }
            "and", "or" -> mapList(condition["conditions"]).forEachIndexed { index, nested ->
                validateConditionReferences(snapshot, nested, "$path.conditions[$index]")
            }
            "not" -> validateConditionReferences(snapshot, stringMap(condition["condition"]), "$path.condition")
        }
    }

    private fun validateActionReferences(
        snapshot: DefinitionSnapshot,
        action: Map<String, Any?>,
        path: String
    ) {
        when (action["type"]?.toString()) {
            "damage" -> {
                val channel = action["channel"].toString()
                assertTrue(channel in snapshot.damageChannels, "$path references unknown damage channel $channel")
            }
            "attribute_buff", "permanent_modifier" -> {
                val key = AttributeKey(namespaced(action["attribute"].toString()))
                assertTrue(key in snapshot.attributes, "$path references unknown attribute $key")
            }
            "skill" -> {
                val id = namespaced(action["skill"].toString())
                assertTrue(id in snapshot.skills, "$path references unknown skill $id")
            }
            "status" -> {
                val id = namespaced(action["status"].toString())
                assertTrue(id in snapshot.statuses, "$path references unknown status $id")
            }
        }
    }

    private fun mapList(raw: Any?): List<Map<String, Any?>> =
        (raw as? List<*>)?.map(::stringMap).orEmpty()

    private fun stringMap(raw: Any?): Map<String, Any?> =
        (raw as? Map<*, *>)?.entries?.associate { it.key.toString() to it.value }
            ?: fail("expected a mapping while validating callback references")

    private fun namespaced(raw: String): String = if (':' in raw) raw else "symphony:$raw"

    private fun loadDisplays(roots: List<Path>): Map<String, DisplayRecord> {
        val result = linkedMapOf<String, DisplayRecord>()
        roots.forEach { root ->
            val displays = root.resolve("displays")
            if (!Files.isDirectory(displays)) return@forEach
            Files.walk(displays).use { paths ->
                paths.filter(::isYamlFile).sorted().forEach { file ->
                    val values = StrictYaml().load(file)
                    values.forEach { (id, raw) ->
                        assertTrue(id !in result, "duplicate Overture display id $id ($file)")
                        val display = raw as? Map<*, *> ?: fail("display $id must be a mapping ($file)")
                        result[id] = if ("conditions" in display) {
                            val conditions = display["conditions"]
                            assertTrue(conditions is List<*>, "conditional display $id conditions must be a list")
                            val targets = conditions.mapIndexed { index, entry ->
                                val condition = entry as? Map<*, *>
                                    ?: fail("conditional display $id conditions[$index] must be a mapping")
                                assertTrue(
                                    condition["condition"] is String && condition["condition"].toString().isNotBlank(),
                                    "conditional display $id conditions[$index] must define condition"
                                )
                                (condition["display"] as? String)?.takeIf(String::isNotBlank)
                                    ?: fail("conditional display $id conditions[$index] must define display")
                            }.toMutableSet()
                            display["default"]?.let {
                                assertTrue(it is String && it.isNotBlank(), "conditional display $id default must be a string")
                                targets += it.toString()
                            }
                            DisplayRecord(conditional = true, targets = targets)
                        } else {
                            assertTrue(display["name"] is String, "display $id must define string name")
                            val lore = display["lore"]
                            assertTrue(lore is List<*> && lore.all { it is String }, "display $id lore must be a string list")
                            validateSymphonyTags(id, display)
                            validatePlayerFacingValue(display["name"], "display $id name")
                            validatePlayerFacingValue(display["lore"], "display $id lore")
                            DisplayRecord(conditional = false)
                        }
                    }
                }
            }
        }
        return result
    }

    private fun loadItems(roots: List<Path>): Map<String, ItemRecord> {
        val result = linkedMapOf<String, ItemRecord>()
        roots.forEach { root ->
            val items = root.resolve("items")
            if (!Files.isDirectory(items)) return@forEach
            Files.walk(items).use { paths ->
                paths.filter(::isYamlFile).filter { it.fileName.toString() != "__group__.yml" }.sorted().forEach { file ->
                    StrictYaml().load(file).forEach { (id, raw) ->
                        val item = raw as? Map<*, *> ?: fail("Overture item $id must be a mapping ($file)")
                        if (result.put(id, ItemRecord(file, item)) != null) fail("duplicate Overture item id $id")
                    }
                }
            }
        }
        return result
    }

    private fun validateComponents(
        items: Map<String, ItemRecord>,
        definitions: DefinitionRepository,
        language: LanguageBundle
    ) {
        val codecs = mapOf(
            "symphony:attributes" to AttributeComponentCodec(definitions) { language },
            "symphony:sockets" to SocketComponentCodec { language },
            "symphony:skills" to SkillComponentCodec(definitions) { language },
            "symphony:set" to SetComponentCodec(definitions) { language },
            "symphony:offhand" to OffhandComponentCodec { language }
        )
        items.forEach { (itemId, record) ->
            val components = record.value["components"] as? Map<*, *> ?: return@forEach
            components.forEach { (rawKey, rawComponent) ->
                val key = rawKey.toString()
                if (!key.startsWith("symphony:")) return@forEach
                val codec = assertNotNull(codecs[key], "unknown Symphony component $key in $itemId")
                val source = assertIs<ItemDataNode.Compound>(toNode(rawComponent))
                val decoded = codec.decode(
                    ComponentDecodeContext(
                        NamespacedKey(key.substringBefore(':'), key.substringAfter(':')),
                        itemId,
                        source,
                        "items.$itemId.components.$key"
                    )
                )
                assertIs<ComponentDecodeResult.Success>(decoded, "$itemId/$key: $decoded")
            }
        }
    }

    private fun validateItemDisplays(items: Map<String, ItemRecord>, displays: Map<String, DisplayRecord>) {
        items.forEach { (id, record) ->
            validateOverturePlayerText(id, record.value)
            val rawDisplay = record.value["display"] ?: return@forEach
            val display = rawDisplay as? String ?: fail("Overture item $id display must be a string (${record.file})")
            assertTrue(display in displays, "Overture item $id references unknown display $display (${record.file})")
        }
    }

    private fun validateConditionalDisplays(displays: Map<String, DisplayRecord>) {
        displays.filterValues(DisplayRecord::conditional).forEach { (id, record) ->
            record.targets.forEach { target ->
                val resolved = displays[target]
                assertNotNull(resolved, "conditional display $id references unknown display $target")
                assertTrue(!resolved.conditional, "conditional display $id must select a regular display, not $target")
            }
        }
    }

    private fun validateGroups(roots: List<Path>) {
        roots.forEach { root ->
            val items = root.resolve("items")
            if (!Files.isDirectory(items)) return@forEach
            Files.walk(items).use { paths ->
                paths.filter(Files::isDirectory).filter { it != items }.forEach { group ->
                    val descriptor = group.resolve("__group__.yml")
                    assertTrue(Files.isRegularFile(descriptor), "Overture group is missing __group__.yml: $group")
                    val values = StrictYaml().load(descriptor)
                    assertTrue(
                        values.keys == setOf("priority", "icon", "name", "lore"),
                        "invalid Overture group descriptor $descriptor"
                    )
                    validatePlayerFacingValue(values["name"], "group $group name")
                    validatePlayerFacingValue(values["lore"], "group $group lore")
                }
            }
        }
    }

    private fun validateSymphonyPlayerText(root: Path) {
        Files.walk(root).use { paths ->
            paths.filter(::isYamlFile).sorted().forEach { file ->
                validatePlayerTextNode(StrictYaml().load(file), file.toString())
            }
        }
    }

    private fun validatePlayerTextNode(raw: Any?, path: String) {
        when (raw) {
            is Map<*, *> -> raw.forEach { (key, value) ->
                val next = "$path.${key.toString()}"
                if (key.toString() in PLAYER_TEXT_KEYS) validatePlayerFacingValue(value, next)
                else validatePlayerTextNode(value, next)
            }
            is List<*> -> raw.forEachIndexed { index, value -> validatePlayerTextNode(value, "$path[$index]") }
        }
    }

    private fun validateOverturePlayerText(id: String, item: Map<*, *>) {
        listOf("name", "name!!", "lore", "lore!!").forEach { key ->
            item[key]?.let { validatePlayerFacingValue(it, "Overture item $id $key") }
        }
    }

    private fun validatePlayerFacingValue(raw: Any?, path: String) {
        when (raw) {
            is String -> {
                val visible = raw.replace(DISPLAY_TAG, "").replace(PLACEHOLDER, "")
                val leaked = INTERNAL_IDENTIFIER.find(visible)?.value
                assertTrue(leaked == null, "$path exposes internal identifier $leaked")
            }
            is Map<*, *> -> raw.forEach { (key, value) -> validatePlayerFacingValue(value, "$path.${key.toString()}") }
            is List<*> -> raw.forEachIndexed { index, value -> validatePlayerFacingValue(value, "$path[$index]") }
        }
    }

    private fun validateCrossReferences(
        snapshot: DefinitionSnapshot,
        overtureItems: Set<String>
    ) {
        snapshot.gems.values.forEach { definition ->
            val item = definition.values["overture-item"]?.toString()
                ?: fail("${definition.id} does not define overture-item")
            assertTrue(item in overtureItems, "${definition.id} references unknown Overture item $item")
        }
        snapshot.socketTools.values.forEach { tool ->
            assertTrue(tool.overtureItem in overtureItems, "${tool.id} references unknown Overture item ${tool.overtureItem}")
        }
        snapshot.socketRemoval?.tool?.overtureItem?.let {
            assertTrue(it in overtureItems, "socket removal references unknown Overture item $it")
        }
        snapshot.affixPools.values.forEach { pool ->
            pool.overtureItems.filterNot { it == "*" }.forEach {
                assertTrue(it in overtureItems, "${pool.id} references unknown Overture item $it")
            }
            pool.cost?.overtureItem?.let {
                assertTrue(it in overtureItems, "${pool.id} cost references unknown Overture item $it")
            }
        }
        snapshot.enhancement?.preventDestroyItem?.let {
            assertTrue(it in overtureItems, "enhancement references unknown destroy protection item $it")
        }
        snapshot.enhancement?.preventDowngradeItem?.let {
            assertTrue(it in overtureItems, "enhancement references unknown downgrade protection item $it")
        }
    }

    private fun validateSymphonyTags(displayId: String, display: Map<*, *>) {
        val supported = setOf("attributes", "affixes", "skills", "sockets", "enhancement", "offhand", "set")
        val values = listOfNotNull(display["name"] as? String) + ((display["lore"] as? List<*>)?.filterIsInstance<String>().orEmpty())
        val tag = Regex("""<symphony:([a-z-]+)(?:\.\.\.)?>""")
        values.forEach { line ->
            tag.findAll(line).forEach { match ->
                assertTrue(match.groupValues[1] in supported, "display $displayId uses unknown Symphony tag ${match.value}")
            }
        }
    }

    private fun copyTree(source: Path, target: Path, replace: Boolean) {
        assertTrue(Files.isDirectory(source), "source directory is missing: $source")
        Files.walk(source).use { paths ->
            paths.forEach { path ->
                val destination = target.resolve(source.relativize(path).toString())
                if (Files.isDirectory(path)) Files.createDirectories(destination)
                else if (replace) Files.copy(path, destination, StandardCopyOption.REPLACE_EXISTING)
                else Files.copy(path, destination)
            }
        }
    }

    private fun isYamlFile(path: Path): Boolean =
        Files.isRegularFile(path) && path.fileName.toString().substringAfterLast('.', "").lowercase() in setOf("yml", "yaml")

    private fun toNode(raw: Any?): ItemDataNode = when (raw) {
        is Map<*, *> -> ItemDataNode.Compound(raw.entries.associate { (key, value) -> key.toString() to toNode(value) })
        is List<*> -> ItemDataNode.ListNode(raw.map(::toNode))
        is Boolean -> ItemDataNode.Bool(raw)
        is Byte, is Short, is Int, is Long -> ItemDataNode.Integer((raw as Number).toLong())
        is Float, is Double -> ItemDataNode.Decimal((raw as Number).toDouble())
        is String -> ItemDataNode.Text(raw)
        else -> error("unsupported YAML node ${raw?.javaClass?.name ?: "null"}")
    }

    private data class ItemRecord(val file: Path, val value: Map<*, *>)
    private data class DisplayRecord(val conditional: Boolean, val targets: Set<String> = emptySet())

    private companion object {
        val PLAYER_TEXT_KEYS = setOf("name", "description", "message", "lore")
        val DISPLAY_TAG = Regex("<[^>]+>")
        val PLACEHOLDER = Regex("\\{[^}]+}")
        val INTERNAL_IDENTIFIER = Regex(
            "(?<![A-Za-z0-9])(?:[a-z][a-z0-9]*:[a-z][a-z0-9._-]*|[a-z][a-z0-9]*(?:[_-][a-z0-9]+)+)(?![A-Za-z0-9])"
        )
    }
}
