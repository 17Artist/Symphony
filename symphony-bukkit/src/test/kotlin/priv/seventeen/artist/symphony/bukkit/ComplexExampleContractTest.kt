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
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertNotNull
import kotlin.test.assertTrue
import kotlin.test.fail
import org.bukkit.NamespacedKey
import org.junit.jupiter.api.io.TempDir
import priv.seventeen.artist.overture.api.component.ComponentDecodeContext
import priv.seventeen.artist.overture.api.component.ComponentDecodeResult
import priv.seventeen.artist.overture.api.data.ItemDataNode
import priv.seventeen.artist.symphony.api.attribute.AttributeKey
import priv.seventeen.artist.symphony.engine.config.DefinitionLoader
import priv.seventeen.artist.symphony.engine.config.LanguageBundle
import priv.seventeen.artist.symphony.engine.config.StrictYaml
import priv.seventeen.artist.symphony.engine.attribute.InteractionCalculator
import priv.seventeen.artist.symphony.engine.damage.ArmorFormula
import priv.seventeen.artist.symphony.engine.damage.ElementalDamageFormula
import priv.seventeen.artist.symphony.engine.definition.*
import priv.seventeen.artist.symphony.overture.component.AttributeComponentCodec
import priv.seventeen.artist.symphony.overture.component.OffhandComponentCodec
import priv.seventeen.artist.symphony.overture.component.SetComponentCodec
import priv.seventeen.artist.symphony.overture.component.SkillComponentCodec
import priv.seventeen.artist.symphony.overture.component.SocketComponentCodec

class ComplexExampleContractTest {
    @TempDir
    lateinit var directory: Path

    @Test
    fun `bundled prismatic arsenal satisfies production contracts`() {
        copyTree(Path.of("src", "main", "resources", "assets").toAbsolutePath().normalize(), directory)
        val exampleRoot = Path.of("src", "main", "resources", "showcase", "prismatic-arsenal")
            .toAbsolutePath().normalize()
        assertTrue(Files.isRegularFile(exampleRoot.resolve("manifest.txt")), "bundled showcase manifest is missing")
        copyTree(exampleRoot.resolve("symphony"), directory)

        val loaded = DefinitionLoader(directory).load()
        assertTrue(loaded.report.valid, loaded.report.issues.joinToString("\n") { "${it.source}:${it.path}: ${it.message}" })
        val snapshot = assertNotNull(loaded.snapshot)
        assertTrue(snapshot.combatPower.enabled)
        assertTrue("attribute:elemental_mastery" in snapshot.combatPower.expression.variables)
        assertTrue("set_pieces:elemental_vanguard" in snapshot.combatPower.expression.variables)
        assertEquals(
            2_250.0,
            snapshot.combatPower.expression.evaluate(
                mapOf(
                    "attribute:elemental_mastery" to 100.0,
                    "set_pieces:elemental_vanguard" to 8.0,
                    "set_tiers:elemental_vanguard" to 4.0,
                    "level" to 10.0,
                    "skill_count" to 2.0,
                    "affix_count" to 2.0,
                    "gem_count" to 2.0
                )
            ),
            1.0e-9
        )
        assertEquals(
            listOf(
                "被动：获得 40 点元素精通。",
                "每次普通攻击附带火焰、寒冰和雷电伤害各 14 点。",
                "命中后有 18% 几率再追加 14 点雷电伤害并施加元素暴露，冷却 2.5 秒。"
            ),
            snapshot.affixes.getValue("symphony:prismatic_conduit")
                .affixDescription(2, mapOf("chance" to 0.18, "damage" to 14.0, "mastery" to 40.0))
        )
        assertEquals(
            SkillActivationInput.RIGHT_CLICK,
            snapshot.skills.getValue("symphony:prismatic_burst").skillActivation()?.input
        )
        assertEquals(
            SkillActivationInput.SNEAK_RIGHT_CLICK,
            snapshot.skills.getValue("symphony:bulwark_pulse").skillActivation()?.input
        )
        assertEquals(setOf(2, 4, 6, 8), snapshot.sets.getValue("symphony:elemental_vanguard").bonuses.keys)
        assertTrue(
            setOf("symphony:vaporize", "symphony:thermal_shock", "symphony:superconduct", "symphony:static_suppression")
                .all(snapshot.reactions::containsKey)
        )
        assertTrue(snapshot.statuses.containsKey("symphony:elemental_exposure"))
        assertTrue(
            setOf("symphony:prismatic_conduit", "symphony:aegis_matrix", "symphony:tempo_engine")
                .all(snapshot.affixes::containsKey)
        )
        assertTrue(snapshot.affixPools.containsKey("symphony:prismatic_vanguard"))
        assertTrue(setOf("symphony:prismatic_burst", "symphony:bulwark_pulse").all(snapshot.skills::containsKey))
        assertTrue(
            snapshot.gems.keys.containsAll(
                setOf(
                    "symphony:ember_core",
                    "symphony:frost_core",
                    "symphony:storm_core",
                    "symphony:bulwark_core",
                    "symphony:universal_prism",
                    "symphony:piercing_quartz"
                )
            )
        )
        assertEquals(setOf("defense"), snapshot.socketTools.getValue("symphony:defense_drill").accepts)
        assertEquals(setOf("fire", "ice", "lightning"), snapshot.socketTools.getValue("symphony:elemental_drill").accepts)
        assertEquals("defense_socket_drill", snapshot.socketTools.getValue("symphony:defense_drill").overtureItem)
        assertEquals("elemental_socket_drill", snapshot.socketTools.getValue("symphony:elemental_drill").overtureItem)
        assertEquals("universal_socket_drill", snapshot.socketTools.getValue("symphony:universal_drill").overtureItem)
        assertTrue(snapshot.attributes.containsKey(AttributeKey.symphony("elemental_guard_core")))
        assertEquals(7, snapshot.interactions.values.count { it.id.startsWith("symphony:focus_") })
        assertTrue(setOf("symphony:assembled_vanguard", "symphony:focused_conduit").all(snapshot.resonances::containsKey))
        val adaptiveBulwark = snapshot.talents.getValue("symphony:adaptive_bulwark")
        val talentGate = adaptiveBulwark.values.getValue("gate") as Map<*, *>
        val talentAll = talentGate["all"] as List<*>
        assertTrue(
            talentAll
                .filterIsInstance<Map<*, *>>()
                .any { it["attribute"] == "vanguard_focus" && (it["value"] as Number).toDouble() == 20.0 },
            "the example talent must be isolated behind its pack-specific focus attribute"
        )
        assertTrue(snapshot.environments.containsKey("symphony:stormfront"))

        val definitions = DefinitionRepository(snapshot)
        val language = LanguageBundle.load(directory.resolve("language.yml"))
        val codecs = mapOf(
            "symphony:attributes" to AttributeComponentCodec(definitions) { language },
            "symphony:sockets" to SocketComponentCodec { language },
            "symphony:skills" to SkillComponentCodec(definitions) { language },
            "symphony:set" to SetComponentCodec(definitions) { language },
            "symphony:offhand" to OffhandComponentCodec { language }
        )
        val overtureRoot = exampleRoot.resolve("overture")
        val itemsRoot = overtureRoot.resolve("items/prismatic-arsenal")
        val items = loadOvertureItems(itemsRoot)
        assertEquals(34, items.size)
        assertEquals("gem_extractor", snapshot.socketRemoval?.tool?.overtureItem)
        val accuracyCalibrator = items.getValue("damage_accuracy_calibrator") as Map<*, *>
        val accuracyComponents = accuracyCalibrator["components"] as Map<*, *>
        val accuracyAttributes = accuracyComponents["symphony:attributes"] as Map<*, *>
        val accuracyModifier = accuracyAttributes["accuracy"] as Map<*, *>
        assertEquals("add", accuracyModifier["operation"])
        assertEquals("-90%", accuracyModifier["value"])
        val accuracyOffhand = accuracyComponents["symphony:offhand"] as Map<*, *>
        assertEquals(true, accuracyOffhand["enabled"])
        assertEquals("50%", accuracyOffhand["attribute-scale"])
        var componentCount = 0
        items.forEach { (itemId, rawItem) ->
            val item = rawItem as? Map<*, *> ?: error("$itemId must be a mapping")
            val components = item["components"] as? Map<*, *> ?: return@forEach
            components.forEach { (rawKey, rawComponent) ->
                val key = rawKey.toString()
                val codec = assertNotNull(codecs[key], "unknown component in $itemId: $key")
                val source = assertIs<ItemDataNode.Compound>(toNode(rawComponent))
                val result = codec.decode(
                    ComponentDecodeContext(
                        NamespacedKey(key.substringBefore(':'), key.substringAfter(':')),
                        itemId,
                        source,
                        "items.$itemId.components.$key"
                    )
                )
                assertIs<ComponentDecodeResult.Success>(result, "$itemId/$key: $result")
                componentCount++
            }
        }
        assertEquals(36, componentCount)

        val display = Files.readString(overtureRoot.resolve("displays/prismatic-arsenal.yml"))
        listOf("attributes", "affixes", "skills", "sockets", "enhancement", "offhand", "set").forEach {
            assertTrue("<symphony:$it...>" in display || "<symphony:$it>" in display, "missing renderer $it")
        }

        validateOvertureGroups(itemsRoot)
        validateCrossReferences(snapshot, items.keys)
        validateSetCoverage(items, snapshot.sets.getValue("symphony:elemental_vanguard").pieces.keys)
        validateDeterministicCalculations(definitions)
    }

    private fun loadOvertureItems(itemsRoot: Path): Map<String, Any?> {
        val result = linkedMapOf<String, Any?>()
        Files.walk(itemsRoot).use { paths ->
            paths.filter {
                Files.isRegularFile(it) && it.fileName.toString().endsWith(".yml") && it.fileName.toString() != "__group__.yml"
            }.sorted().forEach { file ->
                StrictYaml().load(file).forEach { (id, value) ->
                    if (result.put(id, value) != null) fail("duplicate Overture item id $id")
                }
            }
        }
        return result
    }

    private fun validateOvertureGroups(itemsRoot: Path) {
        val expectedGroups = setOf(
            "",
            "01-equipment",
            "02-external-sources",
            "03-gems",
            "04-workshop-tools",
            "05-damage-lab",
            "06-attribute-lab",
            "99-negative-controls"
        )
        val actual = linkedSetOf<String>()
        Files.walk(itemsRoot).use { paths ->
            paths.filter(Files::isDirectory).sorted().forEach { group ->
                val relative = itemsRoot.relativize(group).toString().replace('\\', '/')
                actual += relative
                val descriptor = group.resolve("__group__.yml")
                assertTrue(Files.isRegularFile(descriptor), "Overture group $relative is missing __group__.yml")
                val values = StrictYaml().load(descriptor)
                assertEquals(setOf("priority", "icon", "name", "lore"), values.keys, "invalid group descriptor $relative")
                assertTrue(values["name"].toString().isNotBlank(), "blank group name $relative")
                assertTrue((values["lore"] as? List<*>)?.isNotEmpty() == true, "blank group lore $relative")
            }
        }
        assertEquals(expectedGroups, actual)
    }

    private fun validateCrossReferences(
        snapshot: DefinitionSnapshot,
        overtureItems: Set<String>
    ) {
        snapshot.gems.values.forEach { definition ->
            val item = definition.values["overture-item"]?.toString() ?: fail("${definition.id} has no overture-item")
            assertTrue(item in overtureItems, "missing Overture gem item $item")
        }
        snapshot.socketTools.values.forEach { tool ->
            assertTrue(tool.overtureItem in overtureItems, "missing Overture socket tool ${tool.overtureItem}")
        }
        snapshot.socketRemoval?.tool?.overtureItem?.let {
            assertTrue(it in overtureItems, "missing Overture socket removal tool $it")
        }
        val pool = snapshot.affixPools.getValue("symphony:prismatic_vanguard")
        pool.overtureItems.filterNot { it == "*" }.forEach {
            assertTrue(it in overtureItems, "affix pool references missing Overture item $it")
        }
        pool.cost?.overtureItem?.let { assertTrue(it in overtureItems, "affix cost references missing Overture item $it") }
        snapshot.enhancement?.preventDestroyItem?.let { assertTrue(it in overtureItems) }
        snapshot.enhancement?.preventDowngradeItem?.let { assertTrue(it in overtureItems) }
    }

    private fun validateSetCoverage(items: Map<String, Any?>, expectedPieces: Set<String>) {
        val primaryItems = setOf(
            "vanguard_crown",
            "vanguard_plate",
            "vanguard_leggings",
            "vanguard_boots",
            "prismatic_blade",
            "vanguard_sigil_fire",
            "vanguard_sigil_ice",
            "vanguard_sigil_storm"
        )
        val actualPieces = primaryItems.map { itemId ->
            val item = items.getValue(itemId) as Map<*, *>
            val components = item["components"] as Map<*, *>
            val set = components["symphony:set"] as Map<*, *>
            set["piece"].toString()
        }.toSet()
        assertEquals(expectedPieces, actualPieces)
    }

    private fun validateDeterministicCalculations(definitions: DefinitionRepository) {
        val interactions = InteractionCalculator(definitions)
        val resolved = mapOf("symphony:vanguard_focus" to 40.0)
        val expected = mapOf(
            "interaction_conversion" to 30.0,
            "interaction_overflow" to 40.0,
            "interaction_threshold" to 25.0,
            "interaction_synergy" to 14.0,
            "interaction_conflict" to 6.0,
            "interaction_amplify" to 14.0,
            "interaction_diminish" to (10.0 / 1.4)
        )
        expected.forEach { (id, value) ->
            assertEquals(value, interactions.apply(AttributeKey.symphony(id), 10.0, resolved), 1.0e-9, id)
        }

        assertEquals(9.0, ElementalDamageFormula.calculate(10.0, 0.25, 0.20).output, 1.0e-9)
        assertEquals(18.0, ElementalDamageFormula.calculate(20.0, 0.25, 0.20).output, 1.0e-9)
        assertEquals(4.5, ElementalDamageFormula.calculate(5.0, 0.25, 0.20).output, 1.0e-9)
        assertEquals(19.8, ElementalDamageFormula.calculate(22.0, 0.25, 0.20).output, 1.0e-9)

        val armor = definitions.current().snapshot.armorFormula
        assertEquals(50.0, ArmorFormula.calculate(100.0, 100.0, 0.0, 0.0, armor).afterArmor, 1.0e-9)
        assertEquals(100.0 / 1.4, ArmorFormula.calculate(100.0, 100.0, 0.5, 10.0, armor).afterArmor, 1.0e-9)
    }

    private fun copyTree(source: Path, target: Path) {
        Files.walk(source).use { paths ->
            paths.forEach { path ->
                val destination = target.resolve(source.relativize(path).toString())
                if (Files.isDirectory(path)) Files.createDirectories(destination)
                else Files.copy(path, destination, StandardCopyOption.REPLACE_EXISTING)
            }
        }
    }

    private fun toNode(raw: Any?): ItemDataNode = when (raw) {
        is Map<*, *> -> ItemDataNode.Compound(raw.entries.associate { (key, value) -> key.toString() to toNode(value) })
        is List<*> -> ItemDataNode.ListNode(raw.map(::toNode))
        is Boolean -> ItemDataNode.Bool(raw)
        is Byte, is Short, is Int, is Long -> ItemDataNode.Integer((raw as Number).toLong())
        is Float, is Double -> ItemDataNode.Decimal((raw as Number).toDouble())
        is String -> ItemDataNode.Text(raw)
        else -> error("Unsupported example node ${raw?.javaClass?.name ?: "null"}")
    }
}
