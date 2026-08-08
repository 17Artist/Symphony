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

package priv.seventeen.artist.symphony.bukkit.gui

import org.bukkit.Material
import priv.seventeen.artist.symphony.engine.config.StrictYaml
import java.nio.file.Files
import java.nio.file.Path

data class GuiSlotLayout(
    val slot: Int,
    val fallback: Material? = null,
    val open: GuiScreenId? = null,
    val overtureItem: String? = null
)

data class GuiLayout(
    val titleKey: String,
    val rows: Int,
    val refreshTicks: Long,
    val pageSlots: List<Int>,
    val slots: Map<String, GuiSlotLayout>,
    val scalarSlots: Map<String, Int>
)

class GuiLayoutRepository private constructor(private val layouts: Map<GuiScreenId, GuiLayout>) {
    fun layout(screen: GuiScreenId): GuiLayout = layouts[screen] ?: defaultLayout(screen)
    fun minimumRefreshTicks(): Long = layouts.values.minOfOrNull { it.refreshTicks } ?: 20L

    companion object {
        fun load(dataRoot: Path, yaml: StrictYaml = StrictYaml()): GuiLayoutRepository {
            val directory = dataRoot.resolve("gui")
            val files = FILE_SCREENS.mapValues { directory.resolve(it.key) }
            val parsed = linkedMapOf<GuiScreenId, GuiLayout>()
            files.forEach { (fileName, path) ->
                require(Files.isRegularFile(path)) { "缺少 GUI 配置 gui/$fileName" }
                val root = yaml.load(path)
                val allowed = setOf(
                    "schema", "id", "title-key", "rows", "refresh-ticks", "slots", "page-slots",
                    "target-slot", "material-slot", "downgrade-protection-slot",
                    "destroy-protection-slot", "output-slot",
                    "preview-slot", "confirm-slot", "cancel-slot"
                )
                require((root.keys - allowed).isEmpty()) { "gui/$fileName 包含未知字段 ${(root.keys - allowed).sorted()}" }
                require((root["schema"] as? Number)?.toInt() == 1) { "gui/$fileName.schema 必须等于 1" }
                val titleKey = (root["title-key"] as? String)?.takeIf(String::isNotBlank)
                    ?: throw IllegalArgumentException("缺少必填项 gui/$fileName.title-key")
                val rows = (root["rows"] as? Number)?.toInt() ?: 6
                require(rows == 6) { "gui/$fileName.rows 必须为 6，因为 Symphony 界面使用固定边框" }
                val size = rows * 9
                val screens = FILE_SCREENS.getValue(fileName)
                val defaultPageSlots = if (screens.any { it in ITEM_INTERACTION_SCREENS }) {
                    DEFAULT_WORKSHOP_CONTENT_SLOTS
                } else {
                    DEFAULT_CONTENT_SLOTS
                }
                val pageSlots = (root["page-slots"] as? List<*>)?.mapIndexed { index, value ->
                    (value as? Number)?.toInt() ?: throw IllegalArgumentException("gui/$fileName.page-slots[$index] 必须是整数")
                } ?: defaultPageSlots.filter { it < size }
                require(pageSlots.distinct().size == pageSlots.size && pageSlots.all { it in 0 until size }) {
                    "gui/$fileName.page-slots 包含重复或越界槽位"
                }
                val slotLayouts = (root["slots"] as? Map<*, *>)?.entries?.associate { (rawKey, rawValue) ->
                    val key = rawKey.toString()
                    val node = rawValue as? Map<*, *> ?: throw IllegalArgumentException("gui/$fileName.slots.$key 必须是映射")
                    val known = setOf("slot", "fallback", "open", "overture-item", "icon")
                    require((node.keys.map(Any?::toString).toSet() - known).isEmpty()) { "gui/$fileName.slots.$key 包含未知字段" }
                    val slot = (node["slot"] as? Number)?.toInt()
                        ?: throw IllegalArgumentException("缺少必填项 gui/$fileName.slots.$key.slot")
                    require(slot in 0 until size) { "gui/$fileName.slots.$key.slot 超出背包范围" }
                    val icon = node["icon"] as? Map<*, *>
                    val fallbackRaw = node["fallback"]?.toString() ?: icon?.get("fallback")?.toString()
                    val fallback = fallbackRaw?.let {
                        Material.matchMaterial(it) ?: throw IllegalArgumentException("gui/$fileName.slots.$key.fallback 对应的材质不存在：$it")
                    }
                    val open = node["open"]?.toString()?.let {
                        GuiScreenId.fromAlias(it) ?: throw IllegalArgumentException("gui/$fileName.slots.$key.open 对应的界面不存在：$it")
                    }
                    key to GuiSlotLayout(slot, fallback, open, node["overture-item"]?.toString() ?: icon?.get("overture-item")?.toString())
                }.orEmpty()
                val scalarSlots = listOf(
                    "target-slot", "material-slot", "downgrade-protection-slot",
                    "destroy-protection-slot", "output-slot",
                    "preview-slot", "confirm-slot", "cancel-slot"
                ).mapNotNull { key ->
                    (root[key] as? Number)?.toInt()?.let { value ->
                        require(value in 0 until size) { "gui/$fileName.$key 超出背包范围" }
                        key to value
                    }
                }.toMap()
                val layout = GuiLayout(
                    titleKey,
                    rows,
                    (root["refresh-ticks"] as? Number)?.toLong()?.coerceIn(1L, 1200L) ?: 20L,
                    pageSlots,
                    slotLayouts,
                    scalarSlots
                )
                screens.forEach { screen ->
                    if (screen in ITEM_INTERACTION_SCREENS) validateWorkshopLayout(layout, size, screen, fileName)
                    parsed[screen] = layout
                }
            }
            return GuiLayoutRepository(parsed)
        }

        fun defaults(): GuiLayoutRepository = GuiLayoutRepository(emptyMap())

        private fun defaultLayout(screen: GuiScreenId) = GuiLayout(
            titleKey = "gui.titles.default",
            rows = 6,
            refreshTicks = 20,
            pageSlots = if (screen in ITEM_INTERACTION_SCREENS) DEFAULT_WORKSHOP_CONTENT_SLOTS else DEFAULT_CONTENT_SLOTS,
            slots = emptyMap(),
            scalarSlots = emptyMap()
        )

        private val FILE_SCREENS = linkedMapOf(
            "attributes.yml" to setOf(GuiScreenId.ATTRIBUTE_BROWSER),
            "attribute-detail.yml" to setOf(GuiScreenId.ATTRIBUTE_EXPLAIN),
            "affix.yml" to setOf(GuiScreenId.AFFIX_WORKSHOP),
            "socket.yml" to setOf(GuiScreenId.SOCKET_WORKSHOP),
            "unsocket.yml" to setOf(GuiScreenId.UNSOCKET_WORKSHOP),
            "enhance.yml" to setOf(GuiScreenId.ENHANCEMENT_WORKSHOP),
            "admin.yml" to setOf(GuiScreenId.ADMIN_DIAGNOSTICS)
        )
    }
}

internal val DEFAULT_CONTENT_SLOTS = listOf(10, 11, 12, 13, 14, 15, 16, 19, 20, 21, 22, 23, 24, 25, 28, 29, 30, 31, 32, 33, 34)
internal val DEFAULT_WORKSHOP_CONTENT_SLOTS = listOf(13, 14, 15, 16, 22, 23, 24, 25, 31, 32, 33, 34)

internal fun GuiLayout.workshopSlot(role: WorkshopSlotRole): Int =
    scalarSlots[role.configKey] ?: role.defaultSlot

internal fun validateWorkshopLayout(
    layout: GuiLayout,
    size: Int,
    screen: GuiScreenId = GuiScreenId.ENHANCEMENT_WORKSHOP,
    fileName: String = "workshop.yml"
) {
    val roles = screen.workshopRoles()
    val physicalWorkSlots = roles.mapTo(linkedSetOf()) { layout.workshopSlot(it) }
    require(physicalWorkSlots.size == roles.size) {
        "gui/$fileName 的工作槽位不能重复"
    }
    require(physicalWorkSlots.none { it in layout.pageSlots }) {
        "gui/$fileName 的工作槽位不能与 page-slots 重叠"
    }
    val preview = layout.scalarSlots["preview-slot"] ?: 31
    val confirm = layout.scalarSlots["confirm-slot"] ?: 40
    require(preview in 0 until size && confirm in 0 until size) {
        "gui/$fileName 的 preview-slot 或 confirm-slot 超出背包范围"
    }
    require(preview !in physicalWorkSlots && confirm !in physicalWorkSlots && preview != confirm) {
        "gui/$fileName 的工作槽、预览槽和确认槽必须各不相同"
    }
}
