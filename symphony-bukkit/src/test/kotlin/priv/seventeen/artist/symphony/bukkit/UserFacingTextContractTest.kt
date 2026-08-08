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
import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue
import priv.seventeen.artist.symphony.engine.config.LanguageBundle

class UserFacingTextContractTest {
    private val projectRoot = Path.of("..").toAbsolutePath().normalize()
    private val language = LanguageBundle.load(
        projectRoot.resolve("symphony-bukkit/src/main/resources/assets/language.yml")
    )

    @Test
    fun `all statically referenced language keys exist`() {
        val sourceRoots = listOf(
            projectRoot.resolve("symphony-bukkit/src/main/kotlin"),
            projectRoot.resolve("symphony-overture/src/main/kotlin"),
            projectRoot.resolve("symphony-integrations/src/main/kotlin")
        )
        val files = sourceRoots.flatMap { sourceRoot ->
            Files.walk(sourceRoot).use { paths ->
                paths.filter(Files::isRegularFile)
                    .filter { it.fileName.toString().endsWith(".kt") }
                    .toList()
            }
        }
        val keyPattern = Regex("(?:context\\.t|(?<![A-Za-z0-9_.])t|language(?:\\(\\))?\\.text|(?<![A-Za-z0-9_.])text|(?<![A-Za-z0-9_.])language)\\(\\s*\"([a-z][a-z0-9_-]*\\.[a-z0-9_.-]+)\"")
        val keys = files.flatMap { file -> keyPattern.findAll(Files.readString(file)).map { it.groupValues[1] }.toList() }
        val missing = keys.distinct().filterNot(language::contains)
        assertTrue(missing.isEmpty(), "以下语言键没有对应文本：$missing")
    }

    @Test
    fun `normal gui and workshop strings do not expose integration or storage identifiers`() {
        val sources = listOf(
            "symphony-bukkit/src/main/kotlin/priv/seventeen/artist/symphony/bukkit/gui/GuiScreens.kt",
            "symphony-bukkit/src/main/kotlin/priv/seventeen/artist/symphony/bukkit/gui/GuiService.kt",
            "symphony-bukkit/src/main/kotlin/priv/seventeen/artist/symphony/bukkit/gui/ItemWorkshopService.kt"
        ).joinToString("\n") { Files.readString(projectRoot.resolve(it)) }
        listOf(
            "主手不是 Overture", "物品没有 Symphony", "Overture ID", "\"instanceId",
            "Provider §", "Aura ", "此界面不编译或编辑 YAML", "缺少权限 symphony."
        ).forEach { forbidden -> assertFalse(sources.contains(forbidden), "面向玩家的文本泄露了内部标识：$forbidden") }
    }

    @Test
    fun `生产代码中的说明性注释使用中文`() {
        val issues = productionSourceFiles().flatMap { file ->
            val lines = Files.readAllLines(file)
            var insideBlock = false
            buildList {
                lines.forEachIndexed { index, line ->
                    if (index < APACHE_HEADER_LINES) return@forEachIndexed
                    val fragments = mutableListOf<String>()
                    if (insideBlock) {
                        val end = line.indexOf("*/")
                        if (end >= 0) {
                            fragments += line.substring(0, end)
                            insideBlock = false
                        } else {
                            fragments += line
                        }
                    } else {
                        val lineComment = line.indexOf("//")
                        val blockComment = line.indexOf("/*")
                        val urlLiteral = lineComment >= 0 && line.substring(0, lineComment).contains(Regex("\"https?:$"))
                        if (lineComment >= 0 && !urlLiteral && (blockComment < 0 || lineComment < blockComment)) {
                            fragments += line.substring(lineComment + 2)
                        } else if (blockComment >= 0) {
                            val remainder = line.substring(blockComment + 2)
                            val end = remainder.indexOf("*/")
                            if (end >= 0) {
                                fragments += remainder.substring(0, end)
                            } else {
                                fragments += remainder
                                insideBlock = true
                            }
                        }
                    }
                    fragments.map { it.trim(' ', '*', '/') }
                        .filter { comment ->
                            ASCII_WORD.containsMatchIn(comment) &&
                                !CHINESE_CHARACTER.containsMatchIn(comment) &&
                                !comment.contains("http://") &&
                                !comment.contains("https://")
                        }
                        .forEach { comment -> add("${projectRoot.relativize(file)}:${index + 1}: $comment") }
                }
            }
        }
        assertTrue(issues.isEmpty(), "以下说明性注释尚未本土化：\n${issues.joinToString("\n")}")
    }

    @Test
    fun `生产代码中的运行诊断不包含英文句式`() {
        val issues = productionSourceFiles().flatMap { file ->
            Files.readAllLines(file).flatMapIndexed { index, line ->
                if (index < APACHE_HEADER_LINES) return@flatMapIndexed emptyList()
                STRING_LITERAL.findAll(line).mapNotNull { match ->
                    val literal = match.value.removeSurrounding("\"")
                    if (
                        literal.contains(' ') &&
                        !literal.contains("\${if (") &&
                        !CHINESE_CHARACTER.containsMatchIn(literal) &&
                        ENGLISH_DIAGNOSTIC_WORD.containsMatchIn(literal)
                    ) {
                        "${projectRoot.relativize(file)}:${index + 1}: $literal"
                    } else {
                        null
                    }
                }.toList()
            }
        }
        assertTrue(issues.isEmpty(), "以下运行诊断仍包含英文句式：\n${issues.joinToString("\n")}")
    }

    private fun productionSourceFiles(): List<Path> {
        val sourceRoots = listOf(
            "symphony-api/src/main",
            "symphony-engine/src/main",
            "symphony-bukkit/src/main",
            "symphony-overture/src/main",
            "symphony-integrations/src/main",
            "example"
        ).map(projectRoot::resolve)
        val sources = sourceRoots.flatMap { sourceRoot ->
            Files.walk(sourceRoot).use { paths ->
                paths.filter(Files::isRegularFile)
                    .filter { file ->
                        val normalized = file.toString().replace('\\', '/')
                        val extension = file.fileName.toString().substringAfterLast('.', "")
                        extension in setOf("kt", "kts", "java") &&
                            "/build/" !in normalized &&
                            "/src/test/" !in normalized
                    }
                    .toList()
            }
        }
        val buildScripts = listOf(
            "build.gradle.kts",
            "settings.gradle.kts",
            "symphony-api/build.gradle.kts",
            "symphony-engine/build.gradle.kts",
            "symphony-bukkit/build.gradle.kts",
            "symphony-overture/build.gradle.kts",
            "symphony-integrations/build.gradle.kts"
        ).map(projectRoot::resolve).filter(Files::isRegularFile)
        return (sources + buildScripts).distinct()
    }

    private companion object {
        const val APACHE_HEADER_LINES = 15
        val ASCII_WORD = Regex("\\b[A-Za-z]{2,}\\b")
        val CHINESE_CHARACTER = Regex("[\\u3400-\\u9fff]")
        val STRING_LITERAL = Regex("\"(?:\\\\.|[^\"\\\\])*\"")
        val ENGLISH_DIAGNOSTIC_WORD = Regex(
            "\\b(?:must|cannot|missing|invalid|unknown|failed|failure|required|unsupported|unavailable|expected|exceeded|contains|empty|not|disabled|enabled)\\b",
            RegexOption.IGNORE_CASE
        )
    }
}
