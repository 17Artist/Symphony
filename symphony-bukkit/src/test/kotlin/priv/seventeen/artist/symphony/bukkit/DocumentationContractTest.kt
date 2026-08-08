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
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import priv.seventeen.artist.symphony.engine.config.StrictYaml

class DocumentationContractTest {
    private val root: Path = Path.of("..").toAbsolutePath().normalize()

    @Test
    fun `canonical wiki pointer and repository machine contracts exist`() {
        val readme = root.resolve("README.md")
        assertTrue(Files.isRegularFile(readme), "missing README.md")
        assertTrue(
            Files.readString(readme).contains("https://wiki.arcartx.com/docs/symphony/01_start"),
            "README must point to the canonical Symphony Wiki"
        )
        assertTrue(Files.notExists(root.resolve("docs")), "project-local docs must stay removed; use the ArcartX Wiki")
        listOf("config", "definition", "gui").forEach { name ->
            val schema = StrictYaml().load(root.resolve("symphony-bukkit/src/test/resources/schemas/$name.schema.json"))
            assertEquals("https://json-schema.org/draft/2020-12/schema", schema["\$schema"])
            assertTrue(schema.containsKey("type"))
        }
    }

    @Test
    fun `user facing yaml uses readable block collections`() {
        val violations = mutableListOf<String>()
        Files.walk(root).use { paths ->
            paths.filter(Files::isRegularFile)
                .filter(::isUserFacingYamlSource)
                .forEach { path ->
                    val lines = Files.readAllLines(path)
                    val inspected = if (isMarkdown(path)) {
                        markdownYamlLines(lines)
                    } else {
                        lines.mapIndexed { index, line -> index + 1 to line }
                    }
                    flowStyleViolations(inspected).forEach { (line, value) ->
                        violations += "${root.relativize(path)}:$line: $value"
                    }
                    if (isMarkdown(path)) {
                        lines.forEachIndexed { index, line ->
                            if (
                                Regex("\\{\\s*[A-Za-z0-9_.-]+\\s*:").containsMatchIn(line) ||
                                Regex("[A-Za-z0-9_.-]+:\\s*\\[[^]]+]").containsMatchIn(line)
                            ) {
                                violations += "${root.relativize(path)}:${index + 1}: ${line.trim()}"
                            }
                        }
                    }
                }
        }
        assertTrue(
            violations.isEmpty(),
            "YAML examples must use block-style mappings and sequences:\n${violations.joinToString("\n")}"
        )
    }

    private fun isUserFacingYamlSource(path: Path): Boolean {
        val relative = root.relativize(path)
        if (relative.any { it.toString() in setOf("build", ".runtime", ".gradle", ".git") || it.toString().startsWith(".tmp") }) {
            return false
        }
        return path.fileName.toString().substringAfterLast('.', "").lowercase() in setOf("md", "mdx", "yml", "yaml")
    }

    private fun isMarkdown(path: Path): Boolean =
        path.fileName.toString().substringAfterLast('.', "").lowercase() in setOf("md", "mdx")

    private fun markdownYamlLines(lines: List<String>): List<Pair<Int, String>> {
        val result = mutableListOf<Pair<Int, String>>()
        var yaml = false
        lines.forEachIndexed { index, line ->
            val marker = line.trim()
            if (!yaml && marker.matches(Regex("```ya?ml", RegexOption.IGNORE_CASE))) {
                yaml = true
            } else if (yaml && marker == "```") {
                yaml = false
            } else if (yaml) {
                result += index + 1 to line
            }
        }
        return result
    }

    private fun flowStyleViolations(lines: List<Pair<Int, String>>): List<Pair<Int, String>> {
        val violations = mutableListOf<Pair<Int, String>>()
        var blockScalarIndent: Int? = null
        lines.forEach { (lineNumber, line) ->
            val trimmed = line.trim()
            val indent = line.indexOfFirst { !it.isWhitespace() }.let { if (it < 0) line.length else it }
            val scalarIndent = blockScalarIndent
            if (scalarIndent != null) {
                if (trimmed.isEmpty() || indent > scalarIndent) return@forEach
                blockScalarIndent = null
            }
            if (Regex(":\\s*[>|][-+]?\\s*$").containsMatchIn(line)) {
                blockScalarIndent = indent
                return@forEach
            }
            if (
                Regex("^\\s*-\\s*[\\[{]").containsMatchIn(line) ||
                Regex(":\\s*[\\[{]").containsMatchIn(line)
            ) {
                violations += lineNumber to trimmed
            }
        }
        return violations
    }
}
