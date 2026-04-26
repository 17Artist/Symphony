package priv.seventeen.artist.symphony.plugin.command

import org.bukkit.Bukkit
import org.bukkit.entity.Player
import org.bukkit.inventory.ItemStack
import priv.seventeen.artist.aria.Aria
import priv.seventeen.artist.blink.bukkitPlugin
import priv.seventeen.artist.blink.command.*
import priv.seventeen.artist.symphony.api.affix.AffixInstance
import priv.seventeen.artist.symphony.api.affix.AffixRarity
import priv.seventeen.artist.symphony.api.attribute.Operation
import priv.seventeen.artist.symphony.core.advanced.element.ReactionSystem
import priv.seventeen.artist.symphony.core.advanced.environment.EnvironmentSystem
import priv.seventeen.artist.symphony.core.advanced.interaction.InteractionNetwork
import priv.seventeen.artist.symphony.core.advanced.resonance.ResonanceManager
import priv.seventeen.artist.symphony.core.advanced.status.StatusLayerSystem
import priv.seventeen.artist.symphony.core.advanced.talent.TalentManager
import priv.seventeen.artist.symphony.core.affix.AffixManagerImpl
import priv.seventeen.artist.symphony.core.attribute.AttributeCache
import priv.seventeen.artist.symphony.core.attribute.AttributeCalculator
import priv.seventeen.artist.symphony.core.attribute.AttributeRegistry
import priv.seventeen.artist.symphony.core.config.ConfigLoader
import priv.seventeen.artist.symphony.core.data.ActiveBuff
import priv.seventeen.artist.symphony.core.growth.rune.RuneRegistry
import priv.seventeen.artist.symphony.core.script.AttributeCallableRegistry
import priv.seventeen.artist.symphony.core.skill.builtin.AriaSkillProvider
import priv.seventeen.artist.symphony.core.skill.builtin.SymphonySkillProvider
import priv.seventeen.artist.symphony.core.storage.PlayerDataManager
import priv.seventeen.artist.symphony.nms.SymphonyItemData
import priv.seventeen.artist.symphony.plugin.SymphonyPlugin
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import java.util.UUID

object SymphonyCommands {

    private val gson = Gson()

    private data class ModifierListData(val modifiers: List<ModData> = emptyList())
    private data class ModData(val attr: String, val op: String, val value: Double, val source: String = "base")

    private fun readItemModifiers(item: ItemStack): MutableList<ModData> {
        val json = SymphonyItemData.getString(item, "attributes") ?: return mutableListOf()
        return try {
            val type = object : TypeToken<ModifierListData>() {}.type
            gson.fromJson<ModifierListData>(json, type).modifiers.toMutableList()
        } catch (_: Exception) { mutableListOf() }
    }

    private fun writeItemModifiers(item: ItemStack, mods: List<ModData>) {
        if (mods.isEmpty()) {
            SymphonyItemData.remove(item, "attributes")
        } else {
            SymphonyItemData.setString(item, "attributes", gson.toJson(ModifierListData(mods)))
        }
    }

    private fun pickSlot(player: Player, slot: String): ItemStack? {
        val eq = player.equipment ?: return null
        return when (slot) {
            "MAIN", "MAIN_HAND", "HAND" -> eq.itemInMainHand
            "OFF", "OFF_HAND" -> eq.itemInOffHand
            "HELMET", "HEAD" -> eq.helmet
            "CHEST", "CHESTPLATE" -> eq.chestplate
            "LEGS", "LEGGINGS" -> eq.leggings
            "BOOTS", "FEET" -> eq.boots
            else -> null
        }
    }

    fun register() {
        BlinkCommandRegistrar.register(bukkitPlugin,
            BlinkCommand("symphony", "sym")
                .command("reload", "重载配置", permission = "symphony.admin") { ctx ->
                    ctx.reply("§e正在重载...")
                    SymphonyPlugin.config.reload()
                    AttributeRegistry.clear()
                    AttributeCallableRegistry.clear()
                    Aria.getEngine().annotationRegistry.clear()
                    ReactionSystem.clear()
                    StatusLayerSystem.clear()
                    EnvironmentSystem.clear()
                    InteractionNetwork.clear()
                    ResonanceManager.clear()
                    TalentManager.clear()
                    RuneRegistry.clear()
                    AffixManagerImpl.clearDefinitions()
                    SymphonyPlugin.growthManager.setManager.clear()

                    SymphonyPlugin.formulaEngine.clear()
                    SymphonyPlugin.scriptEngine.loadAttributeScripts(bukkitPlugin.dataFolder)
                    SymphonyPlugin.scriptEngine.loadFormulaScripts(bukkitPlugin.dataFolder, SymphonyPlugin.formulaEngine)
                    SymphonyPlugin.scriptEngine.loadMechanicsScripts(bukkitPlugin.dataFolder)

                    if (SymphonyPlugin.config.elementEnabled) ReactionSystem.registerDefaults()
                    if (SymphonyPlugin.config.statusEnabled) StatusLayerSystem.registerDefaults()
                    if (SymphonyPlugin.config.environmentEnabled) EnvironmentSystem.registerDefaults()

                    val provider = SymphonyPlugin.skillProviderManager.getProvider("symphony") as? SymphonySkillProvider
                    val aria = SymphonyPlugin.skillProviderManager.getProvider("aria") as? AriaSkillProvider
                    provider?.clear()
                    aria?.clear()
                    ConfigLoader.loadAll(
                        bukkitPlugin.dataFolder,
                        SymphonyPlugin.apiImpl.affixManagerInstance,
                        provider ?: SymphonySkillProvider(),
                        SymphonyPlugin.growthManager.setManager,
                        aria
                    )

                    Bukkit.getOnlinePlayers().forEach { AttributeCache.markDirty(it.uniqueId) }
                    ctx.reply("§a重载完成 — 属性 §b${AttributeRegistry.ids().size}§a 个")
                }
                .command("attribute", "属性操作", args = arrayOf("action", "player", "?attribute", "?value"), permission = "symphony.admin") { ctx ->
                    val action = ctx.arg(0)
                    val target = ctx.argPlayer(1) ?: return@command ctx.reply("§c未找到玩家")
                    when (action) {
                        "get" -> {
                            val attrId = ctx.arg(2)
                            val value = AttributeCalculator.getValue(target, attrId)
                            ctx.reply("§b${target.name} §f的 §e$attrId §f= §a${"%.2f".format(value)}")
                        }
                        "list" -> {
                            val values = AttributeCalculator.getValues(target)
                            ctx.reply("§b${target.name} §f的属性列表:")
                            values.entries.sortedBy { AttributeRegistry.get(it.key)?.priority ?: 999 }.forEach { (id, value) ->
                                val display = AttributeRegistry.get(id)?.displayName ?: id
                                ctx.reply("  §7$display §f($id): §a${"%.2f".format(value)}")
                            }
                        }
                        "set" -> {
                            val attrId = ctx.arg(2)
                            val value = ctx.arg(3).toDoubleOrNull() ?: return@command ctx.reply("§c无效数值")
                            val data = PlayerDataManager.getData(target.uniqueId) ?: return@command
                            data.runtime.activeBuffs.add(ActiveBuff(
                                id = "cmd:set:$attrId",
                                attribute = attrId,
                                operation = Operation.FLAT,
                                value = value,
                                expireTime = -1L,
                                source = "command:set"
                            ))
                            AttributeCache.markDirty(target.uniqueId)
                            ctx.reply("§a已设置 ${target.name} 的 $attrId += $value (永久Buff)")
                        }
                        else -> ctx.reply("§c用法: /symphony attribute <get|list|set> <player> [attribute] [value]")
                    }
                }
                .command("level", "等级操作", args = arrayOf("action", "player", "?value"), permission = "symphony.admin") { ctx ->
                    val action = ctx.arg(0)
                    val target = ctx.argPlayer(1) ?: return@command ctx.reply("§c未找到玩家")
                    when (action) {
                        "get" -> {
                            val data = PlayerDataManager.getData(target.uniqueId)
                            ctx.reply("§b${target.name} §f等级: §a${data?.persistent?.level ?: 1} §f经验: §e${data?.persistent?.exp ?: 0}")
                        }
                        "set" -> {
                            val level = ctx.argInt(2, 1)
                            val data = PlayerDataManager.getData(target.uniqueId) ?: return@command
                            data.persistent.level = level
                            data.dirty = true
                            AttributeCalculator.markDirty(target)
                            ctx.reply("§a已设置 ${target.name} 等级为 $level")
                        }
                        else -> ctx.reply("§c用法: /symphony level <get|set|addexp> <player> [value]")
                    }
                }
                .command("affix", "词条操作", args = arrayOf("action", "player", "?affix_id", "?level"), permission = "symphony.admin") { ctx ->
                    val action = ctx.arg(0)
                    val target = ctx.argPlayer(1) ?: return@command ctx.reply("§c未找到玩家")
                    when (action) {
                        "list" -> {
                            val item = target.inventory.itemInMainHand
                            val affixes = AffixManagerImpl.collectItemAffixes(item)
                            if (affixes.isEmpty()) return@command ctx.reply("§7主手物品无词条")
                            ctx.reply("§b${target.name} §f主手词条:")
                            affixes.forEach { a ->
                                ctx.reply("  §e${a.affixId} §fLv.${a.level} §8(${a.uuid.toString().take(8)})")
                            }
                        }
                        "add" -> {
                            val affixId = ctx.arg(2)
                            val level = ctx.argInt(3, 1)
                            val item = target.inventory.itemInMainHand
                            val def = AffixManagerImpl.getDefinition(affixId)
                                ?: return@command ctx.reply("§c未知词条: $affixId")
                            val instance = AffixInstance(UUID.randomUUID(), affixId, level, def.getLevelParams(level))
                            SymphonyPlugin.apiImpl.affixManagerInstance.addAffix(item, instance)
                            ctx.reply("§a已添加词条 $affixId Lv.$level")
                        }
                        "clear" -> {
                            SymphonyPlugin.apiImpl.affixManagerInstance.clearAffixes(target.inventory.itemInMainHand)
                            ctx.reply("§a已清除 ${target.name} 主手所有词条")
                        }
                        "generate" -> {
                            val poolId = ctx.arg(2)
                            val item = target.inventory.itemInMainHand
                            val luck = AttributeCalculator.getValue(target, "luck")
                            val affixes = SymphonyPlugin.apiImpl.affixManagerInstance.generateAffixes(poolId, AffixRarity.RARE, luck)
                            if (affixes.isEmpty()) return@command ctx.reply("§c生成失败，检查词条池 $poolId")
                            SymphonyPlugin.apiImpl.affixManagerInstance.applyAffixes(item, affixes)
                            ctx.reply("§a已生成 ${affixes.size} 个词条")
                        }
                        else -> ctx.reply("§c用法: /sym affix <list|add|clear|generate> <player> [affix_id] [level]")
                    }
                }
                .command("enhance", "强化操作", args = arrayOf("action", "player", "?level"), permission = "symphony.admin") { ctx ->
                    val action = ctx.arg(0)
                    val target = ctx.argPlayer(1) ?: return@command ctx.reply("§c未找到玩家")
                    val item = target.inventory.itemInMainHand
                    when (action) {
                        "get" -> ctx.reply("§b${target.name} §f主手强化等级: §a${SymphonyPlugin.growthManager.getEnhanceLevel(item)}")
                        "set" -> {
                            val level = ctx.argInt(2, 0)
                            SymphonyPlugin.growthManager.setEnhanceLevel(item, level)
                            AttributeCalculator.markDirty(target)
                            ctx.reply("§a已设置强化等级为 $level")
                        }
                        else -> ctx.reply("§c用法: /sym enhance <get|set> <player> [level]")
                    }
                }
                .command("rune", "符文操作", args = arrayOf("action", "player", "?rune_id", "?value"), permission = "symphony.admin") { ctx ->
                    val action = ctx.arg(0)
                    val target = ctx.argPlayer(1) ?: return@command ctx.reply("§c未找到玩家")
                    when (action) {
                        "activate" -> {
                            val runeId = ctx.arg(2)
                            val level = ctx.argInt(3, 1)
                            SymphonyPlugin.growthManager.activateRune(target, runeId, level)
                            ctx.reply("§a已激活符文 $runeId Lv.$level")
                        }
                        "fragment" -> {
                            val runeId = ctx.arg(2)
                            val amount = ctx.argInt(3, 1)
                            SymphonyPlugin.growthManager.addFragments(target, runeId, amount)
                            ctx.reply("§a已给予 ${target.name} $amount 个 $runeId 碎片")
                        }
                        else -> ctx.reply("§c用法: /sym rune <activate|fragment> <player> <rune_id> [value]")
                    }
                }
                .command("gem", "宝石操作", args = arrayOf("action", "player", "?slot", "?index", "?gem_id", "?level"), permission = "symphony.admin") { ctx ->
                    val action = ctx.arg(0)
                    val target = ctx.argPlayer(1) ?: return@command ctx.reply("§c未找到玩家")
                    val slotName = ctx.arg(2).uppercase()
                    val item = pickSlot(target, slotName) ?: return@command ctx.reply("§c无效装备槽: $slotName")
                    when (action) {
                        "list" -> {
                            val slots = SymphonyPlugin.growthManager.getGemSlots(item)
                            if (slots.isEmpty()) return@command ctx.reply("§7该装备无宝石槽")
                            slots.forEach { s ->
                                val mark = if (s.locked) "§8[锁定]" else if (s.gemId != null) "§a[${s.gemId} Lv.${s.gemLevel}]" else "§7[空]"
                                ctx.reply("  §e#${s.index} §f$mark")
                            }
                        }
                        "insert" -> {
                            val idx = ctx.argInt(3, -1)
                            val gemId = ctx.arg(4)
                            val level = ctx.argInt(5, 1)
                            if (idx < 0 || gemId.isEmpty()) return@command ctx.reply("§c用法: /sym gem insert <玩家> <槽位> <索引> <宝石ID> [等级]")
                            val slots = SymphonyPlugin.growthManager.getGemSlots(item)
                            if (idx >= slots.size) return@command ctx.reply("§c槽位索引越界：该装备仅 ${slots.size} 个宝石槽")
                            val ok = SymphonyPlugin.growthManager.gemManager.insertGem(target, item, idx, gemId, level)
                            AttributeCalculator.markDirty(target)
                            ctx.reply(if (ok) "§a已镶嵌 $gemId Lv.$level 到 $slotName #$idx" else "§c镶嵌失败（槽位被锁/已占用）")
                        }
                        "remove" -> {
                            val idx = ctx.argInt(3, -1)
                            if (idx < 0) return@command ctx.reply("§c用法: /sym gem remove <玩家> <槽位> <索引>")
                            val ok = SymphonyPlugin.growthManager.removeGem(item, idx)
                            AttributeCalculator.markDirty(target)
                            ctx.reply(if (ok) "§a已移除 $slotName #$idx 的宝石" else "§c移除失败（槽位为空）")
                        }
                        "unlock" -> {
                            val idx = ctx.argInt(3, -1)
                            if (idx < 0) return@command ctx.reply("§c用法: /sym gem unlock <玩家> <槽位> <索引>")
                            val ok = SymphonyPlugin.growthManager.unlockSlot(item, idx)
                            ctx.reply(if (ok) "§a已解锁 $slotName #$idx" else "§c解锁失败")
                        }
                        else -> ctx.reply("§c用法: /sym gem <list|insert|remove|unlock> <玩家> <槽位> ...")
                    }
                }
                .command("set", "套装操作", args = arrayOf("action", "player", "?slot", "?set_id"), permission = "symphony.admin") { ctx ->
                    val action = ctx.arg(0)
                    val target = ctx.argPlayer(1) ?: return@command ctx.reply("§c未找到玩家")
                    when (action) {
                        "list" -> {
                            val sets = SymphonyPlugin.growthManager.setManager.detectSets(target)
                            if (sets.isEmpty()) return@command ctx.reply("§7无激活套装")
                            sets.forEach { (setId, pieces) ->
                                val def = SymphonyPlugin.growthManager.setManager.getDefinition(setId)
                                ctx.reply("  §e${def?.displayName ?: setId} §f($setId) §7× $pieces")
                            }
                        }
                        "mark" -> {
                            val slotName = ctx.arg(2).uppercase()
                            val setId = ctx.arg(3)
                            if (setId.isEmpty()) return@command ctx.reply("§c用法: /sym set mark <玩家> <槽位> <套装ID>")
                            if (SymphonyPlugin.growthManager.setManager.getDefinition(setId) == null) return@command ctx.reply("§c未知套装: $setId")
                            val item = pickSlot(target, slotName) ?: return@command ctx.reply("§c无效装备槽: $slotName")
                            SymphonyItemData.setString(item, "set_id", setId)
                            target.updateInventory()
                            AttributeCalculator.markDirty(target)
                            ctx.reply("§a已标记 $slotName 为套装 $setId")
                        }
                        "unmark" -> {
                            val slotName = ctx.arg(2).uppercase()
                            val item = pickSlot(target, slotName) ?: return@command ctx.reply("§c无效装备槽: $slotName")
                            SymphonyItemData.remove(item, "set_id")
                            target.updateInventory()
                            AttributeCalculator.markDirty(target)
                            ctx.reply("§a已取消标记 $slotName")
                        }
                        else -> ctx.reply("§c用法: /sym set <list|mark|unmark> <玩家> <槽位> [套装ID]")
                    }
                }
                .command("item", "物品属性操作", args = arrayOf("action", "?attr_id", "?value", "?op"), permission = "symphony.admin") { ctx ->
                    val action = ctx.arg(0)
                    val target = ctx.player ?: return@command ctx.reply("§c仅玩家可用")
                    val item = target.inventory.itemInMainHand
                    if (item.type.isAir) return@command ctx.reply("§c主手没有物品")
                    when (action) {
                        "list" -> {
                            val mods = readItemModifiers(item)
                            if (mods.isEmpty()) return@command ctx.reply("§7主手物品无属性")
                            ctx.reply("§b主手物品属性:")
                            mods.forEach { m ->
                                val display = AttributeRegistry.get(m.attr)?.displayName ?: m.attr
                                val opMark = if (m.op.uppercase() == "PERCENT") "§7×§f${"%.1f%%".format(m.value * 100)}" else "§7+§f${"%.2f".format(m.value)}"
                                ctx.reply("  §e$display §f(${m.attr}) $opMark")
                            }
                        }
                        "add" -> {
                            val attrId = ctx.arg(1).takeIf { it.isNotEmpty() } ?: return@command ctx.reply("§c用法: /sym item add <属性ID> <数值> [FLAT|PERCENT]")
                            val value = ctx.arg(2).toDoubleOrNull() ?: return@command ctx.reply("§c无效数值")
                            val op = ctx.arg(3).uppercase().takeIf { it == "PERCENT" } ?: "FLAT"
                            val mods = readItemModifiers(item)
                            mods.add(ModData(attrId, op, value))
                            writeItemModifiers(item, mods)
                            AttributeCalculator.markDirty(target)
                            val opLabel = if (op == "PERCENT") "×${"%.1f%%".format(value * 100)}" else "+${"%.2f".format(value)}"
                            ctx.reply("§a已添加属性 $attrId $opLabel")
                        }
                        "remove" -> {
                            val attrId = ctx.arg(1).takeIf { it.isNotEmpty() } ?: return@command ctx.reply("§c用法: /sym item remove <属性ID>")
                            val mods = readItemModifiers(item)
                            val before = mods.size
                            mods.removeAll { it.attr == attrId }
                            if (mods.size == before) return@command ctx.reply("§c未找到属性 $attrId")
                            writeItemModifiers(item, mods)
                            AttributeCalculator.markDirty(target)
                            ctx.reply("§a已移除属性 $attrId (${before - mods.size} 条)")
                        }
                        "clear" -> {
                            writeItemModifiers(item, emptyList())
                            AttributeCalculator.markDirty(target)
                            ctx.reply("§a已清空主手物品所有属性")
                        }
                        else -> ctx.reply("§c用法: /sym item <list|add|remove|clear> [属性ID] [数值] [FLAT|PERCENT]")
                    }
                }
                .command("debug", "调试信息", args = arrayOf("?player"), permission = "symphony.admin") { ctx ->
                    val target = ctx.argPlayer(0) ?: (ctx.player ?: return@command ctx.reply("§c请指定玩家"))
                    val data = PlayerDataManager.getData(target.uniqueId)
                    ctx.reply("§8» §f${target.name}")
                    ctx.reply("§8  §7lvl §f${data?.persistent?.level ?: 1}  §7exp §f${data?.persistent?.exp ?: 0}  §7combat §f${data?.runtime?.inCombat ?: false}")
                    ctx.reply("§8  §7buff §f${data?.runtime?.activeBuffs?.size ?: 0}  §7temp_affix §f${data?.runtime?.tempAffixes?.size ?: 0}  §7registered §f${AttributeRegistry.ids().size}")
                }
                .command("explain", "属性流水线", args = arrayOf("attr_id", "?player"), permission = "symphony.admin") { ctx ->
                    val attrId = ctx.arg(0).takeIf { it.isNotEmpty() } ?: return@command ctx.reply("§c用法: /sym explain <attr_id> [player]")
                    val target = ctx.argPlayer(1) ?: (ctx.player ?: return@command ctx.reply("§c请指定玩家"))
                    val explain = AttributeCalculator.explain(target, attrId)
                    if (explain == null) { ctx.reply("§c未找到属性 $attrId"); return@command }
                    val activeMark = if (explain.whenActive) "§a●" else "§c○"
                    val typeMark = if (explain.readonly) "§9derived" else "§7normal"
                    ctx.reply("$activeMark §f${explain.displayName} §8${explain.attrId}  $typeMark")
                    ctx.reply("§8  base §7${"%.3f".format(explain.base)}  §8via §7${explain.formulaDescription}")
                    if (explain.contributions.isEmpty()) {
                        ctx.reply("§8  §7(无 Provider 贡献)")
                    } else {
                        val byProvider = explain.contributions.groupBy { it.providerId }
                        for ((pid, list) in byProvider) {
                            val flatSum = list.filter { it.operation == Operation.FLAT }.sumOf { it.value }
                            val pctSum = list.filter { it.operation == Operation.PERCENT }.sumOf { it.value }
                            val parts = buildList {
                                if (flatSum != 0.0) add("§7+§f${"%.2f".format(flatSum)}")
                                if (pctSum != 0.0) add("§7×§f${"%.1f%%".format(pctSum * 100)}")
                            }.joinToString(" §8· ")
                            ctx.reply("§8  §7${pid.padEnd(12)} §8→ §f$parts §8(${list.size})")
                        }
                    }
                    ctx.reply("§8  §ffinal §e${"%.3f".format(explain.finalValue)}")
                }
                .command("menu", "打开属性面板") { ctx ->
                    val target = ctx.player ?: return@command ctx.reply("§c仅玩家可用")
                    SymphonyPlugin.guiProvider.open(target)
                }
                .tabComplete("action") { listOf("get", "set", "list", "addexp") }
                .tabComplete("attribute") { AttributeRegistry.ids().toList() }
                .tabComplete("attr_id") { AttributeRegistry.ids().toList() }
                .tabComplete("op") { listOf("FLAT", "PERCENT") }
        )
    }
}
