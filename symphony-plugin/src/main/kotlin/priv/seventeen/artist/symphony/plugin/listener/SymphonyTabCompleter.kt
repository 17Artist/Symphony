package priv.seventeen.artist.symphony.plugin.listener

import org.bukkit.event.server.TabCompleteEvent
import priv.seventeen.artist.blink.event.AutoListener
import priv.seventeen.artist.symphony.core.affix.AffixManagerImpl
import priv.seventeen.artist.symphony.core.attribute.AttributeProviderRegistry
import priv.seventeen.artist.symphony.core.attribute.AttributeRegistry
import priv.seventeen.artist.symphony.core.attribute.provider.GemProvider
import priv.seventeen.artist.symphony.plugin.SymphonyPlugin

/**
 * 上下文感知的 Tab 补全器（仅接管 `/sym item ...`）。
 *
 * 背景：blink 的 [priv.seventeen.artist.blink.command.BlinkCommand.tabComplete] 是
 * “按参数位置取占位符 → 无参 provider” 的纯位置模型，无法感知前序输入。
 * `item` 命令所有子命令共用同一套 args 模板（i_sub / i_action / i_arg1 ...），
 * 因此第三段永远落在 `i_arg1` 上，其 provider 把
 * 属性ID + 词条ID + 词条池ID + 装备槽名 全部混在一起返回——这正是
 * `/sym item affix add <TAB>` 冒出 accuracy / BOOTS / CHEST 等无关项的原因。
 *
 * [TabCompleteEvent] 在 blink 补全返回之后触发，可拿到完整 buffer，
 * 据此精确判断当前应补什么并覆盖结果。该监听器是命令补全树的“声明式镜像”：
 * 不改动任何命令执行逻辑，只对 `item` 这一条线接管补全，其余命令
 * （reload / player / explain / menu / debug）保持 blink 原生行为。
 */
object SymphonyTabCompleter {

    private val ROOTS = setOf("sym", "symphony")

    private val ITEM_SUBS = listOf("attr", "affix", "enhance", "gem", "set")

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

        // sym 之后的片段：最后一段是“正在输入”的部分 token，其余是已确定的上下文。
        val args = tokens.drop(1)
        val ctx = args.dropLast(1)
        val partial = args.last()

        // 仅接管 item 命令；其余命令交还 blink 默认补全。
        if (ctx.getOrNull(0)?.lowercase() != "item") return

        // 计算 item 命令内部已确定的各段（i_sub / i_action / i_arg1 ...）。
        val candidates = completeItem(ctx.drop(1)) ?: return

        event.completions = candidates
            .filter { it.startsWith(partial, ignoreCase = true) }
            .sorted()
    }

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

    /** i_arg1（第三段）。这里是原 bug 的发生位置。 */
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
            // mark 支持 <套装ID> 或 <槽位> <套装ID>，两者都补
            "mark" -> SLOT_NAMES + setIds()
            "unmark" -> SLOT_NAMES               // unmark [槽位]（可选）
            else -> emptyList()
        }
        else -> emptyList()
    }

    /** i_arg2（第四段）。 */
    private fun itemArg2(sub: String?, action: String?): List<String> = when (sub) {
        "attr" -> if (action == "add") FLAT_PERCENT else emptyList()
        "set" -> if (action == "mark") setIds() else emptyList()   // mark <槽位> <套装ID>
        // attr/affix 的数值、等级，gem 的索引/数量等自由输入字段：不补全
        else -> emptyList()
    }

    /** i_arg3（第五段）。 */
    private fun itemArg3(sub: String?, action: String?): List<String> = when (sub) {
        "gem" -> if (action == "insert") gemIds() else emptyList() // gem insert <槽位> <索引> <宝石ID>
        else -> emptyList()
    }

    // ── 候选来源（权威注册表，保证与实际可用值一致）──
    private fun attributeIds(): List<String> = AttributeRegistry.ids().toList()
    private fun affixIds(): List<String> = AffixManagerImpl.definitionIds().toList()
    private fun poolIds(): List<String> = AffixManagerImpl.poolIds().toList()
    private fun setIds(): List<String> =
        SymphonyPlugin.growthManager.setManager.getAllDefinitions().map { it.id }
    private fun gemIds(): List<String> =
        AttributeProviderRegistry.getAll().filterIsInstance<GemProvider>()
            .firstOrNull()?.gemIds()?.toList() ?: emptyList()
}
