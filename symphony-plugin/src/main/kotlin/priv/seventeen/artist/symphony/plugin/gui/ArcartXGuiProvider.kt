package priv.seventeen.artist.symphony.plugin.gui

import org.bukkit.Bukkit
import org.bukkit.entity.Player
import priv.seventeen.artist.blink.BlinkLog

/**
 * ArcartX 客户端 UI 适配 — 反射调用 [ArcartXAPI.getUIRegistry().open]。
 *
 * 期望由用户在 ArcartX 资源中预先注册名为 `symphony_attribute_menu` 的 UI。
 * 若未注册或 ArcartX 不可用，则不应注入此 Provider。
 */
class ArcartXGuiProvider : AttributeGuiProvider {
    override val id = "arcartx"

    private val uiId = "symphony_attribute_menu"

    override fun open(player: Player) {
        try {
            val apiClass = Class.forName("priv.seventeen.artist.arcartx.api.ArcartXAPI")
            val api = apiClass.getMethod("getUIRegistry").invoke(null)
            val openMethod = api.javaClass.methods.firstOrNull {
                it.name == "open" && it.parameterCount == 2
            } ?: return
            openMethod.invoke(api, player, uiId)
        } catch (e: Exception) {
            BlinkLog.warn("ArcartX UI 打开失败: ${e.message}，回退到 Bukkit GUI")
            BukkitInventoryGuiProvider().open(player)
        }
    }

    companion object {
        fun isAvailable(): Boolean {
            if (Bukkit.getPluginManager().getPlugin("ArcartX") == null) return false
            return runCatching {
                Class.forName("priv.seventeen.artist.arcartx.api.ArcartXAPI")
            }.isSuccess
        }
    }
}
