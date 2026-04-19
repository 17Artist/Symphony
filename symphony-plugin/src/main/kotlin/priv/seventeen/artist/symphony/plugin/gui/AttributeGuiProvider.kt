package priv.seventeen.artist.symphony.plugin.gui

import org.bukkit.entity.Player

/**
 * 属性面板 GUI 抽象。
 * 启动时按可用性二选一注入；缺省走 [BukkitInventoryGuiProvider]。
 */
interface AttributeGuiProvider {
    val id: String
    fun open(player: Player)
}
