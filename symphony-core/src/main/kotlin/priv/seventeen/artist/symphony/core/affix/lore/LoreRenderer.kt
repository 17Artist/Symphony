package priv.seventeen.artist.symphony.core.affix.lore

import org.bukkit.ChatColor
import org.bukkit.inventory.ItemStack
import priv.seventeen.artist.symphony.api.affix.AffixInstance
import priv.seventeen.artist.symphony.api.affix.AffixRarity
import priv.seventeen.artist.symphony.core.affix.AffixManagerImpl
import priv.seventeen.artist.symphony.nms.NMSAdapterFactory

/**
 * 词条 Lore 渲染器 — 将词条信息渲染到物品 Lore 中。
 */
object LoreRenderer {
    private val rarityColors = mapOf(
        AffixRarity.COMMON to "&f",
        AffixRarity.UNCOMMON to "&a",
        AffixRarity.RARE to "&9",
        AffixRarity.EPIC to "&5",
        AffixRarity.LEGENDARY to "&6",
        AffixRarity.MYTHIC to "&c"
    )

    private val rarityNames = mapOf(
        AffixRarity.COMMON to "普通",
        AffixRarity.UNCOMMON to "优秀",
        AffixRarity.RARE to "稀有",
        AffixRarity.EPIC to "史诗",
        AffixRarity.LEGENDARY to "传说",
        AffixRarity.MYTHIC to "神话"
    )

    fun renderAffixLore(item: ItemStack): List<String> {
        val affixes = AffixManagerImpl.collectItemAffixes(item)
        if (affixes.isEmpty()) return emptyList()

        val lines = mutableListOf<String>()
        lines.add("&8&m─────────&r &6词条 &8&m─────────")

        for (affix in affixes) {
            val def = AffixManagerImpl.getDefinition(affix.affixId) ?: continue
            val color = rarityColors[def.rarity] ?: "&f"
            val rarityName = rarityNames[def.rarity] ?: "未知"

            lines.add("&7[$color$rarityName&7] ${def.displayName} &8Lv.${affix.level}")

            // 渲染描述，替换参数
            for (desc in def.description) {
                var rendered = desc
                for ((key, value) in affix.parameters) {
                    rendered = rendered.replace("{$key}", value.toString())
                }
                lines.add("  &7$rendered")
            }
        }

        lines.add("&8&m──────────────────────")
        return lines.map { ChatColor.translateAlternateColorCodes('&', it) }
    }

    fun applyLore(item: ItemStack) {
        val affixLore = renderAffixLore(item)
        if (affixLore.isEmpty()) return

        val meta = item.itemMeta ?: return
        val existingLore = meta.lore?.toMutableList() ?: mutableListOf()

        // 移除旧的词条 Lore（在分隔线之间）
        val startIdx = existingLore.indexOfFirst { ChatColor.stripColor(it)?.contains("─────────") == true && ChatColor.stripColor(it)?.contains("词条") == true }
        val endIdx = if (startIdx >= 0) {
            existingLore.subList(startIdx + 1, existingLore.size).indexOfFirst {
                ChatColor.stripColor(it)?.matches(Regex("─{10,}")) == true
            }.let { if (it >= 0) startIdx + 1 + it + 1 else existingLore.size }
        } else -1

        if (startIdx >= 0 && endIdx > startIdx) {
            for (i in (endIdx - 1) downTo startIdx) {
                existingLore.removeAt(i)
            }
        }

        existingLore.addAll(affixLore)
        meta.lore = existingLore
        item.itemMeta = meta
    }
}
