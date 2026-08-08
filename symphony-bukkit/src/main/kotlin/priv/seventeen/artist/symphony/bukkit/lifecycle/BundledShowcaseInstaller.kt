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

package priv.seventeen.artist.symphony.bukkit.lifecycle

import java.io.InputStream
import java.nio.file.AtomicMoveNotSupportedException
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.StandardCopyOption
import java.nio.file.StandardOpenOption

/** Symphony 初次安装时仅写出一次内置配置展示包。 */
internal object BundledShowcaseInstaller {
    const val ID = "prismatic-arsenal"
    const val MANIFEST_RESOURCE = "showcase/$ID/manifest.txt"
    const val INSTALLING_MARKER = ".$ID-installing"
    const val INSTALLED_MARKER = ".$ID-installed"

    data class InstallResult(
        val attempted: Boolean,
        val installed: Boolean,
        val copiedSymphonyFiles: Int,
        val copiedOvertureFiles: Int,
        val verifiedExistingFiles: Int
    )

    fun install(
        symphonyRoot: Path,
        overtureRoot: Path,
        firstInstall: Boolean,
        openResource: (String) -> InputStream?
    ): InstallResult {
        val normalizedSymphony = symphonyRoot.toAbsolutePath().normalize()
        val normalizedOverture = overtureRoot.toAbsolutePath().normalize()
        val installing = normalizedSymphony.resolve(INSTALLING_MARKER)
        val installed = normalizedSymphony.resolve(INSTALLED_MARKER)

        if (Files.isRegularFile(installed)) return InstallResult(false, false, 0, 0, 0)
        if (!firstInstall && !Files.isRegularFile(installing)) return InstallResult(false, false, 0, 0, 0)

        Files.createDirectories(normalizedSymphony)
        Files.createDirectories(normalizedOverture)
        if (!Files.exists(installing)) {
            Files.writeString(
                installing,
                "showcase=$ID\n",
                StandardOpenOption.CREATE_NEW,
                StandardOpenOption.WRITE
            )
        }

        val entries = loadManifest(openResource)
        var copiedSymphony = 0
        var copiedOverture = 0
        var verified = 0
        entries.forEach { entry ->
            val targetRoot = when (entry.owner) {
                Owner.SYMPHONY -> normalizedSymphony
                Owner.OVERTURE -> normalizedOverture
            }
            val target = targetRoot.resolve(entry.relativePath).normalize()
            check(target.startsWith(targetRoot)) { "内置样例路径越界: ${entry.manifestValue}" }
            val bytes = openResource(entry.resourcePath)?.use(InputStream::readBytes)
                ?: error("发布 JAR 缺少内置样例资源 ${entry.resourcePath}")
            when (copyOrVerify(target, bytes)) {
                CopyResult.COPIED -> when (entry.owner) {
                    Owner.SYMPHONY -> copiedSymphony++
                    Owner.OVERTURE -> copiedOverture++
                }
                CopyResult.IDENTICAL -> verified++
            }
        }

        moveMarker(installing, installed)
        return InstallResult(true, true, copiedSymphony, copiedOverture, verified)
    }

    private fun loadManifest(openResource: (String) -> InputStream?): List<Entry> {
        val lines = openResource(MANIFEST_RESOURCE)?.bufferedReader(Charsets.UTF_8)?.use { it.readLines() }
            ?: error("发布 JAR 缺少内置样例清单 $MANIFEST_RESOURCE")
        val seenTargets = linkedSetOf<String>()
        val entries = lines.mapIndexedNotNull { index, raw ->
            val value = raw.substringBefore('#').trim()
            if (value.isEmpty()) return@mapIndexedNotNull null
            val separator = value.indexOf(':')
            require(separator > 0 && separator < value.lastIndex) {
                "$MANIFEST_RESOURCE 第 ${index + 1} 行格式错误"
            }
            val owner = when (value.substring(0, separator)) {
                "symphony" -> Owner.SYMPHONY
                "overture" -> Owner.OVERTURE
                else -> error("$MANIFEST_RESOURCE 第 ${index + 1} 行包含未知目标")
            }
            val rawPath = value.substring(separator + 1)
            require('\\' !in rawPath && !rawPath.startsWith('/') && rawPath.isNotBlank()) {
                "$MANIFEST_RESOURCE 第 ${index + 1} 行路径无效"
            }
            val relative = Path.of(rawPath).normalize()
            require(!relative.isAbsolute && relative.none { it.toString() == ".." }) {
                "$MANIFEST_RESOURCE 第 ${index + 1} 行路径越界"
            }
            require(seenTargets.add(value)) { "$MANIFEST_RESOURCE 包含重复目标 $value" }
            Entry(owner, relative, value, "showcase/$ID/${owner.directory}/$rawPath")
        }
        require(entries.any { it.owner == Owner.SYMPHONY }) { "$MANIFEST_RESOURCE 没有 Symphony 配置" }
        require(entries.any { it.owner == Owner.OVERTURE }) { "$MANIFEST_RESOURCE 没有 Overture 配置" }
        return entries
    }

    private fun copyOrVerify(target: Path, bytes: ByteArray): CopyResult {
        if (Files.exists(target)) {
            require(Files.isRegularFile(target)) { "内置样例目标不是文件: $target" }
            require(Files.readAllBytes(target).contentEquals(bytes)) {
                "内置样例与已有文件冲突，未覆盖: $target"
            }
            return CopyResult.IDENTICAL
        }
        Files.createDirectories(requireNotNull(target.parent))
        val temporary = Files.createTempFile(target.parent, ".${target.fileName}.", ".tmp")
        try {
            Files.write(temporary, bytes, StandardOpenOption.TRUNCATE_EXISTING)
            try {
                Files.move(temporary, target, StandardCopyOption.ATOMIC_MOVE)
            } catch (_: AtomicMoveNotSupportedException) {
                Files.move(temporary, target)
            }
        } finally {
            Files.deleteIfExists(temporary)
        }
        return CopyResult.COPIED
    }

    private fun moveMarker(source: Path, target: Path) {
        try {
            Files.move(source, target, StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING)
        } catch (_: AtomicMoveNotSupportedException) {
            Files.move(source, target, StandardCopyOption.REPLACE_EXISTING)
        }
    }

    private enum class Owner(val directory: String) {
        SYMPHONY("symphony"),
        OVERTURE("overture")
    }

    private enum class CopyResult { COPIED, IDENTICAL }

    private data class Entry(
        val owner: Owner,
        val relativePath: Path,
        val manifestValue: String,
        val resourcePath: String
    )
}
