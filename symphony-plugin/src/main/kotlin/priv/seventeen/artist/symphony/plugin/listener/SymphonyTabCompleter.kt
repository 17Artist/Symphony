package priv.seventeen.artist.symphony.plugin.listener

import org.bukkit.Bukkit
import org.bukkit.event.server.TabCompleteEvent
import priv.seventeen.artist.blink.event.AutoListener
import priv.seventeen.artist.symphony.core.affix.AffixManagerImpl
import priv.seventeen.artist.symphony.core.attribute.AttributeProviderRegistry
import priv.seventeen.artist.symphony.core.attribute.AttributeRegistry
import priv.seventeen.artist.symphony.core.attribute.provider.GemProvider
import priv.seventeen.artist.symphony.core.growth.rune.RuneRegistry
import priv.seventeen.artist.symphony.plugin.SymphonyPlugin

/**
 * 上下文感知的 Tab 补全器（接管 `/sym item ...` 和 `/sym player ...`）。
 *
 * 背景：blink 的 [priv.seventeen.artist.blink.command.BlinkCommand.tabComplete] 是
 * "按参数位置取占位符 → 无参 provider" 的纯位置模型，无法感知前序输入。
 * `item` / `player` 命令所有子命令共用同一套 args 模板，
 * 因此各段的 provider 把所有子命令的候选混在一起返回。
 *
 * [TabCompleteEvent] 在 blink 补全返回之后触发，可拿到完整 buffer，
 * 据此精确判断当前应补什么并覆盖结果。该监听器是命令补全树的"声明式镜像"：
 * 不改动任何命令执行逻辑，对 `item` 和 `player` 两条线接管补全，其余命令
 * （reload / explain / menu / debug）保持 blink 原生行为。
 */
object SymphonyTabCompleter {

    private val ROOTS = setOf("sym", "symphony")

    private val ITEM_SUBS = listOf("attr", "affix", "enhance", "gem", "set")
    private val PLAYER_SUBS = listOf("attr", "buff", "level", "rune")

    /** 规范装备槽名（与命令 SLOT_NAMES 一致）。 */
    private val SLOT_NAMES = listOf("MAIN", "OFF", "HELMET", "CHEST", "LEGS", "BOOTS")

    private val FLAT_PERCENT = listOf("FLAT", "PERCENT")

    @AutoListener
    fun onTabComplete(event: TabCompleteEvent) {
        val raw = event.buffer.removePrefix("/")
        val tokens = raw.split(" ")
        if (tokens.size < 2) return                              // 还在补 "sym" 本身

        val root = tokens[0].lowercase()
        if (root !in ROOTS) return

        // sym 之后的片段：最后一段是"正在输入"的部分 token，其余是已确定的上下文。
        val args = tokens.drop(1)
        val ctx = args.dropLast(1)
        val partial = args.last()

        val cmd = ctx.getOrNull(0)?.lowercase()

        val candidates = when (cmd) {
            "item" -> completeItem(ctx.drop(1))
            "player" -> completePlayer(ctx.drop(1))
            else -> return  // 其余命令交还 blink 默认补全
        } ?: return

        event.completions = candidates
            .filter { it.startsWith(partial, ignoreCase = true) }
            .sorted()
    }

    // ══════════════════════════════════════════════════════════════
    //  item 命令补全
    // ══════════════════════════════════════════════════════════════

    /**
     * @param a `item` 命令内部已完整输入的参数（不含正在输入的部分 token）。
     *          a[0]=i_sub, a[1]=i_action, a[2]=i_arg1 ...
     * @return 当前位置应补全的候选；返回 null 表示不接管（交还 blink）。
     */
    private fun completeItem(a: List<String>): List<String>? {
        val sub = a.getOrNull(0)?.lowercase()
        val action = a.getOrNull(1)?.lowercase()
        return when (a.size) {
            0 -> ITEM_SUBS                       // 补 i_sub
            1 -> itemActions(sub)                // 补 i_action
            2 -> itemArg1(sub, action)           // 补 i_arg1
            3 -> itemArg2(sub, action)           // 补 i_arg2
            4 -> itemArg3(sub, action)           // 补 i_arg3
            else -> emptyList()
        }
    }

    /** i_action：各子命令的动作。 */
    private fun itemActions(sub: String?): List<String> = when (sub) {
        "attr" -> listOf("add", "remove", "list", "clear")
        "affix" -> listOf("list", "add", "clear", "generate")
        "enhance" -> listOf("get", "set")
        "gem" -> listOf("list", "insert", "remove", "unlock", "init")
        "set" -> listOf("list", "mark", "unmark")
        else -> emptyList()
    }

    /** i_arg1（第三段）。 */
    private fun itemArg1(sub: String?, action: String?): List<String> = when (sub) {
        "attr" -> when (action) {
            "add", "remove" -> attributeIds()
            else -> emptyList()
        }
        "affix" -> when (action) {
            "add" -> affixIds()                  // 只补已注册词条
            "generate" -> poolIds()              // 只补词条池
            else -> emptyList()
        }
        "gem" -> SLOT_NAMES                      // 所有 gem 动作第三段都是装备槽
        "set" -> when (action) {
            "mark" -> SLOT_NAMES + setIds()
            "unmark" -> SLOT_NAMES
            else -> emptyList()
        }
        else -> emptyList()
    }

    /** i_arg2（第四段）。 */
    private fun itemArg2(sub: String?, action: String?): List<String> = when (sub) {
        "attr" -> if (action == "add") FLAT_PERCENT else emptyList()
        "set" -> if (action == "mark") setIds() else emptyList()
        else -> emptyList()
    }

    /** i_arg3（第五段）。 */
    private fun itemArg3(sub: String?, action: String?): List<String> = when (sub) {
        "gem" -> if (action == "insert") gemIds() else emptyList()
        else -> emptyList()
    }

    // ══════════════════════════════════════════════════════════════
    //  player 命令补全
    // ══════════════════════════════════════════════════════════════

    /**
     * player 命令结构: /sym player <p_sub> <player> <p_action> <p_arg1> <p_arg2> ...
     * ctx.drop(1) 后 a 的内容:
     *   a[0] = p_sub (attr/buff/level/rune)
     *   a[1] = player name
     *   a[2] = p_action (get/set/list/add/clear/activate/fragment)
     *   a[3] = p_arg1
     *   a[4] = p_arg2
     *   ...
     */
    private fun completePlayer(a: List<String>): List<String>? {
        val sub = a.getOrNull(0)?.lowercase()
        val action = a.getOrNull(2)?.lowercase()
        return when (a.size) {
            0 -> PLAYER_SUBS                             // 补 p_sub
            1 -> onlinePlayerNames()                     // 补 player name
            2 -> playerActions(sub)                      // 补 p_action
            3 -> playerArg1(sub, action)                 // 补 p_arg1
            4 -> playerArg2(sub, action)                 // 补 p_arg2
            5 -> playerArg3(sub, action)                 // 补 p_arg3
            else -> emptyList()
        }
    }

    /** p_action：各子命令的动作（上下文感知）。 */
    private fun playerActions(sub: String?): List<String> = when (sub) {
        "attr" -> listOf("get", "set", "add", "list")
        "buff" -> listOf("add", "list", "clear")
        "level" -> listOf("get", "set")
        "rune" -> listOf("activate", "fragment")
        else -> emptyList()
    }

    /** p_arg1（action 之后第一个参数）。 */
    private fun playerArg1(sub: String?, action: String?): List<String> = when (sub) {
        "attr" -> when (action) {
            "get", "set", "add" -> attributeIds()
            else -> emptyList()
        }
        "buff" -> when (action) {
            "add" -> attributeIds()
            else -> emptyList()
        }
        "level" -> emptyList()                           // set 后面是数字，不补全
        "rune" -> when (action) {
            "activate", "fragment" -> runeIds()
            else -> emptyList()
        }
        else -> emptyList()
    }

    /** p_arg2（action 之后第二个参数）。 */
    private fun playerArg2(sub: String?, action: String?): List<String> = when (sub) {
        "attr" -> if (action == "set") emptyList() else emptyList()  // 数值，不补全
        "buff" -> if (action == "add") emptyList() else emptyList()  // 数值，不补全
        else -> emptyList()
    }

    /** p_arg3（action 之后第三个参数）。 */
    private fun playerArg3(sub: String?, action: String?): List<String> = when (sub) {
        "attr" -> if (action == "set" || action == "add") FLAT_PERCENT else emptyList()
        "buff" -> if (action == "add") FLAT_PERCENT else emptyList()
        else -> emptyList()
    }

    // ══════════════════════════════════════════════════════════════
    //  候选来源（权威注册表，保证与实际可用值一致）
    // ══════════════════════════════════════════════════════════════

    private fun attributeIds(): List<String> = AttributeRegistry.ids().toList()
    private fun affixIds(): List<String> = AffixManagerImpl.definitionIds().toList()
    private fun poolIds(): List<String> = AffixManagerImpl.poolIds().toList()
    private fun setIds(): List<String> =
        SymphonyPlugin.growthManager.setManager.getAllDefinitions().map { it.id }
    private fun gemIds(): List<String> =
        AttributeProviderRegistry.getAll().filterIsInstance<GemProvider>()
            .firstOrNull()?.gemIds()?.toList() ?: emptyList()
    private fun runeIds(): List<String> = RuneRegistry.ids().toList()
    private fun onlinePlayerNames(): List<String> = Bukkit.getOnlinePlayers().map { it.name }
}
