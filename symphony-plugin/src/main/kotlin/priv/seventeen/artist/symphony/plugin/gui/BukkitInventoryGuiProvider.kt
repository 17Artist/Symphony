package priv.seventeen.artist.symphony.plugin.gui

import org.bukkit.Bukkit
import org.bukkit.Material
import org.bukkit.entity.Player
import org.bukkit.event.EventHandler
import org.bukkit.event.Listener
import org.bukkit.event.inventory.InventoryClickEvent
import org.bukkit.inventory.Inventory
import org.bukkit.inventory.InventoryHolder
import org.bukkit.inventory.ItemStack
import org.bukkit.inventory.meta.ItemMeta
import priv.seventeen.artist.symphony.core.attribute.AttributeCalculator
import priv.seventeen.artist.symphony.core.attribute.AttributeRegistry

/**
 * 兜底 GUI：原生 Bukkit Inventory，54 格双层。
 *
 * 布局：
 * - 顶部 9 格：分类切换按钮（最多 9 个 category）
 * - 中部 4×9 = 36 格：当前分类下的属性，按 priority 排序，多于 36 时分页
 * - 底部 9 格：上一页 / 下一页 / 空 / 战斗状态指示
 */
class BukkitInventoryGuiProvider : AttributeGuiProvider, Listener {
    override val id = "bukkit"

    private val viewState = mutableMapOf<java.util.UUID, ViewState>()

    private data class ViewState(val category: String, val page: Int)

    override fun open(player: Player) {
        viewState[player.uniqueId] = ViewState(firstCategory(), 0)
        player.openInventory(buildInventory(player))
    }

    private fun firstCategory(): String =
        AttributeRegistry.getAll().map { it.category }.distinct().firstOrNull() ?: "custom"

    private fun categories(): List<String> =
        AttributeRegistry.getAll().map { it.category }.distinct().sorted()

    private fun buildInventory(player: Player): Inventory {
        val state = viewState[player.uniqueId] ?: ViewState(firstCategory(), 0)
        val inv = Bukkit.createInventory(Holder, 54, "Symphony Attributes")

        // 顶栏：分类切换
        for ((i, cat) in categories().withIndex()) {
            if (i >= 9) break
            inv.setItem(i, categoryButton(cat, cat == state.category))
        }

        // 中部：当前分类下的属性
        val attrs = AttributeRegistry.getByCategory(state.category)
            .sortedBy { it.priority }
        val pageSize = 36
        val totalPages = ((attrs.size + pageSize - 1) / pageSize).coerceAtLeast(1)
        val page = state.page.coerceIn(0, totalPages - 1)
        val slice = attrs.drop(page * pageSize).take(pageSize)
        for ((i, attr) in slice.withIndex()) {
            inv.setItem(9 + i, attributeItem(player, attr.id))
        }

        // 底栏：分页
        if (page > 0) inv.setItem(45, navItem("§7« 上一页", "prev"))
        if (page < totalPages - 1) inv.setItem(53, navItem("§f下一页 »", "next"))
        inv.setItem(49, navItem("§8${state.category} §7${page + 1}/$totalPages", "info"))

        return inv
    }

    private fun categoryButton(category: String, active: Boolean): ItemStack {
        val item = ItemStack(if (active) Material.ENCHANTED_BOOK else Material.BOOK)
        item.itemMeta = item.itemMeta?.also { meta ->
            meta.setDisplayName(if (active) "§a${category}" else "§7${category}")
            meta.lore = listOf("§8» 切换分类", "§8» symphony:cat=${category}")
        }
        return item
    }

    private fun attributeItem(player: Player, attrId: String): ItemStack {
        val attr = AttributeRegistry.get(attrId) ?: return ItemStack(Material.BARRIER)
        val value = AttributeCalculator.getValue(player, attrId)
        val display = formatValue(value, attr.format)
        val item = ItemStack(if (attr.readonly) Material.AMETHYST_SHARD else Material.PAPER)
        item.itemMeta = item.itemMeta?.also { meta ->
            meta.setDisplayName("§f${attr.displayName}  §7${display}")
            val lore = mutableListOf<String>()
            if (attr.description.isNotEmpty()) lore += "§8${attr.description}"
            lore += "§8id §7${attr.id}"
            if (attr.readonly) lore += "§9derived"
            if (attr.whenConditions.isNotEmpty()) lore += "§8when §7${attr.whenConditions.joinToString(" & ")}"
            if (attr.dependsOn.isNotEmpty()) lore += "§8deps §7${attr.dependsOn.joinToString(", ")}"
            meta.lore = lore
        }
        return item
    }

    private fun navItem(name: String, tag: String): ItemStack {
        val item = ItemStack(Material.GRAY_STAINED_GLASS_PANE)
        item.itemMeta = (item.itemMeta as ItemMeta).also { meta ->
            meta.setDisplayName(name)
            meta.lore = listOf("§8» $tag")
        }
        return item
    }

    private fun formatValue(value: Double, format: String): String = when (format) {
        "percent" -> "${"%.1f".format(value * 100)}%"
        "integer" -> value.toInt().toString()
        else -> "%.2f".format(value)
    }

    @EventHandler
    fun onClick(e: InventoryClickEvent) {
        if (e.inventory.holder !== Holder) return
        e.isCancelled = true
        val player = e.whoClicked as? Player ?: return
        val state = viewState[player.uniqueId] ?: return
        val slot = e.rawSlot
        when {
            slot in 0..8 -> {
                val cats = categories()
                val cat = cats.getOrNull(slot) ?: return
                viewState[player.uniqueId] = ViewState(cat, 0)
                player.openInventory(buildInventory(player))
            }
            slot == 45 -> {
                viewState[player.uniqueId] = state.copy(page = state.page - 1)
                player.openInventory(buildInventory(player))
            }
            slot == 53 -> {
                viewState[player.uniqueId] = state.copy(page = state.page + 1)
                player.openInventory(buildInventory(player))
            }
        }
    }

    private object Holder : InventoryHolder {
        override fun getInventory(): Inventory =
            Bukkit.createInventory(this, 54, "Symphony Attributes")
    }
}
