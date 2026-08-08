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

import org.bukkit.plugin.Plugin
import java.nio.file.Files
import java.nio.file.Path

internal object DefaultResources {
    data class InstallResult(
        val firstInstall: Boolean,
        val showcase: BundledShowcaseInstaller.InstallResult
    )

    private val resources = listOf(
        "config.yml",
        "combat-power.yml",
        "language.yml",
        "display.yml",
        "attributes/combat.yml",
        "attributes/elements.yml",
        "attributes/movement.yml",
        "attributes/resource.yml",
        "damage/channels.yml",
        "affixes/thunder_strike.yml",
        "affix-pools/default.yml",
        "skills/arc_bolt.yml",
        "items/enhancement.yml",
        "items/gems/arcane_ruby.yml",
        "items/socket-tools/universal_drill.yml",
        "items/socket-removal.yml",
        "items/sets/frost_guardian.yml",
        "advanced/interactions/crit_overflow.yml",
        "advanced/reactions/overload.yml",
        "advanced/resonances/arcane_master.yml",
        "advanced/talents/immovable.yml",
        "advanced/statuses/poison.yml",
        "advanced/environments/desert_heat.yml",
        "gui/attributes.yml",
        "gui/attribute-detail.yml",
        "gui/affix.yml",
        "gui/socket.yml",
        "gui/unsocket.yml",
        "gui/enhance.yml",
        "gui/admin.yml"
    )

    private val directories = listOf(
        "affix-pools",
        "scripts/callbacks",
        "scripts/modules"
    )

    fun install(plugin: Plugin): InstallResult {
        val root = plugin.dataFolder.toPath()
        val firstInstall = !Files.isRegularFile(root.resolve("config.yml"))
        val legacyEntry = if (Files.isDirectory(root)) Files.list(root).use { entries ->
            entries.filter { !isBootstrapEntry(it) }.findFirst().orElse(null)
        } else null
        if (legacyEntry != null && !Files.isRegularFile(root.resolve("config.yml"))) {
            throw IllegalStateException(
                "检测到旧版 Symphony 数据 ${legacyEntry.fileName}，但缺少当前 config.yml；请先备份并迁移 ${plugin.dataFolder}"
            )
        }
        Files.createDirectories(root)
        val overture = requireNotNull(plugin.server.pluginManager.getPlugin("Overture")) {
            "缺少必须依赖 Overture，无法安装内置配置样例"
        }
        // 初次安装时，展示配置会提供两个全局单例定义。应先安装展示配置，
        // 再写出普通默认文件，避免回退示例覆盖这些定义。
        val showcase = BundledShowcaseInstaller.install(
            root,
            overture.dataFolder.toPath(),
            firstInstall,
            plugin::getResource
        )
        resources.forEach { relative ->
            val target = root.resolve(relative)
            if (Files.exists(target)) return@forEach
            Files.createDirectories(target.parent)
            val source = plugin.getResource("assets/$relative")
                ?: throw IllegalStateException("发布 JAR 缺少默认资源 assets/$relative")
            source.use { Files.copy(it, target) }
        }
        directories.forEach { Files.createDirectories(root.resolve(it)) }
        return InstallResult(firstInstall, showcase)
    }

    private val BLINK_BOOTSTRAP_ENTRIES = setOf(
        "libs",
        "blink.yml",
        BundledShowcaseInstaller.INSTALLING_MARKER,
        BundledShowcaseInstaller.INSTALLED_MARKER
    )
    private val MANAGED_EMPTY_ROOTS = directories.mapTo(linkedSetOf()) { it.substringBefore('/') }

    private fun isBootstrapEntry(path: Path): Boolean {
        if (path.fileName.toString() in BLINK_BOOTSTRAP_ENTRIES) return true
        if (path.fileName.toString() !in MANAGED_EMPTY_ROOTS || !Files.isDirectory(path)) return false
        return Files.walk(path).use { stream -> stream.noneMatch(Files::isRegularFile) }
    }
}
