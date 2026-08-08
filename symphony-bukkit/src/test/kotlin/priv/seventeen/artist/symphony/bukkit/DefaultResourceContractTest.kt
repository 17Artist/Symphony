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
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue
import org.junit.jupiter.api.io.TempDir
import priv.seventeen.artist.symphony.api.attribute.AttributeKey
import priv.seventeen.artist.symphony.engine.config.DefinitionLoader
import priv.seventeen.artist.symphony.engine.config.LanguageBundle
import priv.seventeen.artist.symphony.engine.definition.SkillActivationInput
import priv.seventeen.artist.symphony.engine.definition.affixDescription
import priv.seventeen.artist.symphony.engine.definition.skillActivation
import priv.seventeen.artist.symphony.engine.equipment.OffhandMode

class DefaultResourceContractTest {
    @TempDir
    lateinit var directory: Path

    @Test
    fun `all shipped definition examples are accepted by production loader`() {
        val root = Path.of("src", "main", "resources", "assets").toAbsolutePath().normalize()
        val result = DefinitionLoader(root).load()
        assertTrue(result.report.valid, result.report.issues.joinToString("\n") { "${it.source}:${it.path}: ${it.message}" })
        val snapshot = assertNotNull(result.snapshot)
        assertEquals(50, snapshot.attributes.size)
        assertEquals(8, snapshot.damageChannels.size)
        assertTrue(snapshot.combatPower.enabled)
        assertTrue("attribute:physical_damage" in snapshot.combatPower.expression.variables)
        assertTrue("set_tier_count" in snapshot.combatPower.expression.variables)
        val frostGuardian = snapshot.sets.getValue("symphony:frost_guardian")
        assertEquals(setOf(2, 4, 7), frostGuardian.bonuses.keys)
        assertEquals("寒霜守护者", frostGuardian.name)
        assertEquals("寒霜冠冕", frostGuardian.pieces["crown"])
        assertEquals("冰霜壁垒", frostGuardian.bonuses.getValue(2).display.name)
        assertEquals(listOf("冰霜抗性提高 15%"), frostGuardian.bonuses.getValue(2).display.description)
        assertEquals(3, snapshot.affixPools.getValue("symphony:default").maxAffixes)
        assertEquals("LAPIS_LAZULI", snapshot.affixPools.getValue("symphony:default").cost?.material)
        assertEquals(10, snapshot.enhancement?.levels?.get(10)?.cost?.amount)
        assertEquals("通用打孔器", snapshot.socketTools.getValue("symphony:universal_drill").name)
        assertEquals(setOf("*"), snapshot.socketTools.getValue("symphony:universal_drill").accepts)
        assertEquals("AMETHYST_SHARD", snapshot.socketRemoval?.tool?.material)
        assertEquals(1, snapshot.socketRemoval?.tool?.amount)
        assertEquals(
            listOf(
                "被动：每次普通攻击附带 25 点雷电伤害。",
                "命中后有 15% 几率再追加 25 点雷电伤害，冷却 2 秒。"
            ),
            snapshot.affixes.getValue("symphony:thunder_strike")
                .affixDescription(2, mapOf("chance" to 0.15, "damage" to 25.0))
        )
        assertEquals(
            SkillActivationInput.RIGHT_CLICK,
            snapshot.skills.getValue("symphony:arc_bolt").skillActivation()?.input
        )
        val settings = assertNotNull(result.settings)
        assertEquals(2, settings.schema)
        assertTrue(settings.features.skills)
        assertTrue(settings.features.sockets)
        assertEquals(OffhandMode.FULL, settings.equipment.offhand.mode)
        assertEquals(0.5, settings.equipment.offhand.attributeScale)
        val epicFight = settings.compatibility.epicFight
        assertEquals(false, epicFight.enabled)
        assertEquals(4_000L, epicFight.postWorldGraceMillis)
        assertEquals(6_000L, epicFight.stuckInactionMillis)
        assertEquals(1L, epicFight.fallbackPollTicks)
    }

    @Test
    fun `shipped language is strict and provides neutral item rejection`() {
        val root = Path.of("src", "main", "resources", "assets").toAbsolutePath().normalize()
        val language = LanguageBundle.load(root.resolve("language.yml"))
        assertEquals("§c物品不符合要求。", language.text("item.invalid"))
        assertTrue(!language.text("item.invalid").contains("Symphony"))
        assertTrue(!language.text("item.invalid").contains("Overture"))
        assertEquals("§8角色属性", language.text("gui.titles.attributes"))
        assertEquals("§8宝石拆卸", language.text("gui.titles.unsocket"))
        assertTrue(language.contains("gui.overview.combat-power"))
        assertTrue(language.contains("console.combat-power-failed"))
    }

    @Test
    fun `all static bukkit language references exist`() {
        val root = Path.of("src", "main", "resources", "assets").toAbsolutePath().normalize()
        val language = LanguageBundle.load(root.resolve("language.yml"))
        val sourceRoot = Path.of("src", "main", "kotlin").toAbsolutePath().normalize()
        val reference = Regex("""(?:context\.t|\bt|\.lines)\(\"([^\"$]+)\"""")
        val missing = linkedSetOf<String>()
        Files.walk(sourceRoot).use { paths ->
            paths.filter { it.fileName.toString().endsWith(".kt") }.forEach { path ->
                reference.findAll(Files.readString(path)).forEach { match ->
                    val key = match.groupValues[1]
                    if (!language.contains(key)) missing += "$key (${sourceRoot.relativize(path)})"
                }
            }
        }
        assertTrue(missing.isEmpty(), "missing language references:\n${missing.joinToString("\n")}")
    }

    @Test
    fun `attribute callbacks accept structured conditions and actions without an Aria script`() {
        val source = Path.of("src", "main", "resources", "assets").toAbsolutePath().normalize()
        Files.walk(source).use { paths ->
            paths.forEach { path ->
                val target = directory.resolve(source.relativize(path).toString())
                if (Files.isDirectory(path)) Files.createDirectories(target)
                else Files.copy(path, target, StandardCopyOption.REPLACE_EXISTING)
            }
        }
        Files.writeString(
            directory.resolve("attributes/runtime_structured_callback.yml"),
            """
            schema: 1
            attributes:
              runtime_structured_callback:
                name: Structured callback contract
                description: JVM loader contract only
                category: test
                base: 0
                bounds:
                  min: 0
                format: number
                priority: 10000
                callbacks:
                  marker:
                    trigger: combat.damage_taken
                    conditions:
                      - type: attribute
                        attribute: arcane_resistance
                        operator: '>='
                        value: 25%
                        target: self
                    actions:
                      - type: permanent_modifier
                        attribute: luck
                        operation: add
                        value: 7
                        target: self
                        key: loader-test
            """.trimIndent()
        )

        val result = DefinitionLoader(directory).load()
        assertTrue(result.report.valid, result.report.issues.joinToString("\n") { "${it.source}:${it.path}: ${it.message}" })
        val callback = assertNotNull(result.snapshot)
            .attributes.getValue(AttributeKey.symphony("runtime_structured_callback"))
            .callbacks.single()
        assertNull(callback.script)
        assertEquals("permanent_modifier", callback.actions.single()["type"])
    }
}
