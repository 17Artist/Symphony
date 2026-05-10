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
import priv.seventeen.artist.symphony.core.attribute.AttributeProviderRegistry
import priv.seventeen.artist.symphony.core.attribute.AttributeRegistry
import priv.seventeen.artist.symphony.core.attribute.provider.GemProvider
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

    private val SLOT_NAMES = listOf("MAIN", "OFF", "HELMET", "CHEST", "LEGS", "BOOTS")

    fun register() {
        BlinkCommandRegistrar.register(bukkitPlugin,
            BlinkCommand("symphony", "sym")
                // ── reload ──
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
                    val gemProvider = AttributeProviderRegistry.getAll().filterIsInstance<GemProvider>().firstOrNull()
                    ConfigLoader.loadAll(
                        bukkitPlugin.dataFolder,
                        SymphonyPlugin.apiImpl.affixManagerInstance,
                        provider ?: SymphonySkillProvider(),
                        SymphonyPlugin.growthManager.setManager,
                        aria,
                        SymphonyPlugin.growthManager.levelManager,
                        SymphonyPlugin.growthManager.enhanceManager,
                        gemProvider
                    )

                    Bukkit.getOnlinePlayers().forEach { AttributeCache.markDirty(it.uniqueId) }
                    ctx.reply("§a重载完成 — 属性 §b${AttributeRegistry.ids().size}§a 个")
                }
                // ── player <sub> <player> ... ──
                .command("player", "玩家操作", args = arrayOf("p_sub", "player", "?p_action", "?p_arg1", "?p_arg2", "?p_arg3", "?p_arg4"), permission = "symphony.admin") { ctx ->
                    val sub = ctx.arg(0)
                    val target = ctx.argPlayer(1) ?: return@command ctx.reply("§c未找到玩家")
                    val a1 = ctx.arg(2); val a2 = ctx.arg(3); val a3 = ctx.arg(4); val a4 = ctx.arg(5); val a5 = ctx.arg(6)
                    when (sub) {
                        "attr" -> when (a1) {
                            "get" -> {
                                val attrId = a2.takeIf { it.isNotEmpty() } ?: return@command ctx.reply("§c用法: /sym player attr <玩家> get <属性ID>")
                                val value = AttributeCalculator.getValue(target, attrId)
                                ctx.reply("§b${target.name} §f的 §e$attrId §f= §a${"%.2f".format(value)}")
                            }
                            "set" -> {
                                val attrId = a2.takeIf { it.isNotEmpty() } ?: return@command ctx.reply("§c用法: /sym player attr <玩家> set <属性ID> <值> [FLAT|PERCENT]")
                                val value = a3.toDoubleOrNull() ?: return@command ctx.reply("§c无效数值")
                                val op = if (a4.uppercase() == "PERCENT") Operation.PERCENT else Operation.FLAT
                                val data = PlayerDataManager.getData(target.uniqueId) ?: return@command
                                data.runtime.activeBuffs.add(ActiveBuff(
                                    id = "cmd:set:$attrId",
                                    attribute = attrId,
                                    operation = op,
                                    value = value,
                                    expireTime = -1L,
                                    source = "command:set"
                                ))
                                AttributeCache.markDirty(target.uniqueId)
                                val opLabel = if (op == Operation.PERCENT) "×${"%.1f%%".format(value * 100)}" else "+${"%.2f".format(value)}"
                                ctx.reply("§a已设置 ${target.name} 的 $attrId $opLabel (永久)")
                            }
                            "list" -> {
                                val values = AttributeCalculator.getValues(target)
                                ctx.reply("§b${target.name} §f的属性列表:")
                                values.entries.sortedBy { AttributeRegistry.get(it.key)?.priority ?: 999 }.forEach { (id, value) ->
                                    val display = AttributeRegistry.get(id)?.displayName ?: id
                                    ctx.reply("  §7$display §f($id): §a${"%.2f".format(value)}")
                                }
                            }
                            else -> ctx.reply("§c用法: /sym player attr <玩家> <get|set|list> ...")
                        }
                        "buff" -> when (a1) {
                            "add" -> {
                                val attrId = a2.takeIf { it.isNotEmpty() } ?: return@command ctx.reply("§c用法: /sym player buff <玩家> add <属性ID> <值> [FLAT|PERCENT] [秒]")
                                if (!AttributeRegistry.exists(attrId)) return@command ctx.reply("§c未知属性: $attrId")
                                val value = a3.toDoubleOrNull() ?: return@command ctx.reply("§c无效数值")
                                val op = if (a4.uppercase() == "PERCENT") Operation.PERCENT else Operation.FLAT
                                val duration = a5.toLongOrNull() ?: -1L
                                val expire = if (duration > 0) System.currentTimeMillis() + duration * 1000L else -1L
                                val data = PlayerDataManager.getData(target.uniqueId) ?: return@command
                                val buffId = "cmd:buff:$attrId"
                                // 移除同属性的旧命令 Buff（替换模式）
                                data.runtime.activeBuffs.removeAll { it.id == buffId }
                                data.runtime.activeBuffs.add(ActiveBuff(
                                    id = buffId,
                                    attribute = attrId,
                                    operation = op,
                                    value = value,
                                    expireTime = expire,
                                    source = "command:buff"
                                ))
                                AttributeCache.markDirty(target.uniqueId)
                                val durLabel = if (duration > 0) "${duration}s" else "永久"
                                ctx.reply("§a已添加 Buff $attrId ($durLabel)")
                            }
                            "list" -> {
                                val data = PlayerDataManager.getData(target.uniqueId) ?: return@command ctx.reply("§c无数据")
                                val buffs = data.runtime.activeBuffs
                                if (buffs.isEmpty()) return@command ctx.reply("§7${target.name} 无活跃 Buff")
                                ctx.reply("§b${target.name} §f的 Buff (${buffs.size}):")
                                buffs.forEach { b ->
                                    val opMark = if (b.operation == Operation.PERCENT) "×${"%.1f%%".format(b.value * 100)}" else "+${"%.2f".format(b.value)}"
                                    val durMark = if (b.expireTime < 0) "§7永久" else "§7${((b.expireTime - System.currentTimeMillis()) / 1000).coerceAtLeast(0)}s"
                                    ctx.reply("  §e${b.attribute} §f$opMark $durMark §8(${b.id})")
                                }
                            }
                            "clear" -> {
                                val data = PlayerDataManager.getData(target.uniqueId) ?: return@command
                                val count = data.runtime.activeBuffs.size
                                data.runtime.activeBuffs.clear()
                                AttributeCache.markDirty(target.uniqueId)
                                ctx.reply("§a已清除 ${target.name} 的 $count 个 Buff")
                            }
                            else -> ctx.reply("§c用法: /sym player buff <玩家> <add|list|clear> ...")
                        }
                        "level" -> when (a1) {
                            "get" -> {
                                val data = PlayerDataManager.getData(target.uniqueId)
                                ctx.reply("§b${target.name} §f等级: §a${data?.persistent?.level ?: 1} §f经验: §e${data?.persistent?.exp ?: 0}")
                            }
                            "set" -> {
                                val level = a2.toIntOrNull() ?: return@command ctx.reply("§c无效等级")
                                val data = PlayerDataManager.getData(target.uniqueId) ?: return@command
                                data.persistent.level = level
                                data.dirty = true
                                AttributeCalculator.markDirty(target)
                                ctx.reply("§a已设置 ${target.name} 等级为 $level")
                            }
                            else -> ctx.reply("§c用法: /sym player level <玩家> <get|set> [值]")
                        }
                        "rune" -> when (a1) {
                            "activate" -> {
                                val runeId = a2.takeIf { it.isNotEmpty() } ?: return@command ctx.reply("§c用法: /sym player rune <玩家> activate <符文ID> [等级]")
                                val level = a3.toIntOrNull() ?: 1
                                SymphonyPlugin.growthManager.activateRune(target, runeId, level)
                                ctx.reply("§a已激活符文 $runeId Lv.$level")
                            }
                            "fragment" -> {
                                val runeId = a2.takeIf { it.isNotEmpty() } ?: return@command ctx.reply("§c用法: /sym player rune <玩家> fragment <符文ID> [数量]")
                                val amount = a3.toIntOrNull() ?: 1
                                SymphonyPlugin.growthManager.addFragments(target, runeId, amount)
                                ctx.reply("§a已给予 ${target.name} $amount 个 $runeId 碎片")
                            }
                            else -> ctx.reply("§c用法: /sym player rune <玩家> <activate|fragment> <符文ID> ...")
                        }
                        else -> ctx.reply("§c用法: /sym player <attr|buff|level|rune> <玩家> ...")
                    }
                }
                // ── item <sub> ... ──（操作执行者主手物品）
                .command("item", "物品操作", args = arrayOf("i_sub", "?i_action", "?i_arg1", "?i_arg2", "?i_arg3", "?i_arg4"), permission = "symphony.admin") { ctx ->
                    val sub = ctx.arg(0)
                    val target = ctx.player ?: return@command ctx.reply("§c仅玩家可用")
                    val a1 = ctx.arg(1); val a2 = ctx.arg(2); val a3 = ctx.arg(3); val a4 = ctx.arg(4); val a5 = ctx.arg(5)
                    val item = target.inventory.itemInMainHand
                    if (item.type.isAir) return@command ctx.reply("§c主手没有物品")
                    when (sub) {
                        "attr" -> when (a1) {
                            "add" -> {
                                val attrId = a2.takeIf { it.isNotEmpty() } ?: return@command ctx.reply("§c用法: /sym item attr add <属性ID> <值> [FLAT|PERCENT]")
                                val value = a3.toDoubleOrNull() ?: return@command ctx.reply("§c无效数值")
                                val op = a4.uppercase().takeIf { it == "PERCENT" } ?: "FLAT"
                                val mods = readItemModifiers(item)
                                mods.add(ModData(attrId, op, value))
                                writeItemModifiers(item, mods)
                                AttributeCalculator.markDirty(target)
                                val opLabel = if (op == "PERCENT") "×${"%.1f%%".format(value * 100)}" else "+${"%.2f".format(value)}"
                                ctx.reply("§a已添加属性 $attrId $opLabel")
                            }
                            "remove" -> {
                                val attrId = a2.takeIf { it.isNotEmpty() } ?: return@command ctx.reply("§c用法: /sym item attr remove <属性ID>")
                                val mods = readItemModifiers(item)
                                val before = mods.size
                                mods.removeAll { it.attr == attrId }
                                if (mods.size == before) return@command ctx.reply("§c未找到属性 $attrId")
                                writeItemModifiers(item, mods)
                                AttributeCalculator.markDirty(target)
                                ctx.reply("§a已移除属性 $attrId (${before - mods.size} 条)")
                            }
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
                            "clear" -> {
                                writeItemModifiers(item, emptyList())
                                AttributeCalculator.markDirty(target)
                                ctx.reply("§a已清空主手物品所有属性")
                            }
                            else -> ctx.reply("§c用法: /sym item attr <add|remove|list|clear> ...")
                        }
                        "affix" -> when (a1) {
                            "list" -> {
                                val affixes = AffixManagerImpl.collectItemAffixes(item)
                                if (affixes.isEmpty()) return@command ctx.reply("§7主手物品无词条")
                                ctx.reply("§b主手词条:")
                                affixes.forEach { a ->
                                    ctx.reply("  §e${a.affixId} §fLv.${a.level} §8(${a.uuid.toString().take(8)})")
                                }
                            }
                            "add" -> {
                                val affixId = a2.takeIf { it.isNotEmpty() } ?: return@command ctx.reply("§c用法: /sym item affix add <词条ID> [等级]")
                                val level = a3.toIntOrNull() ?: 1
                                val def = AffixManagerImpl.getDefinition(affixId)
                                    ?: return@command ctx.reply("§c未知词条: $affixId")
                                val instance = AffixInstance(UUID.randomUUID(), affixId, level, def.getLevelParams(level))
                                SymphonyPlugin.apiImpl.affixManagerInstance.addAffix(item, instance)
                                AttributeCalculator.markDirty(target)
                                ctx.reply("§a已添加词条 $affixId Lv.$level")
                            }
                            "clear" -> {
                                SymphonyPlugin.apiImpl.affixManagerInstance.clearAffixes(item)
                                AttributeCalculator.markDirty(target)
                                ctx.reply("§a已清除主手所有词条")
                            }
                            "generate" -> {
                                val poolId = a2.takeIf { it.isNotEmpty() } ?: return@command ctx.reply("§c用法: /sym item affix generate <词条池ID>")
                                val luck = AttributeCalculator.getValue(target, "luck")
                                val affixes = SymphonyPlugin.apiImpl.affixManagerInstance.generateAffixes(poolId, AffixRarity.RARE, luck)
                                if (affixes.isEmpty()) return@command ctx.reply("§c生成失败，检查词条池 $poolId")
                                SymphonyPlugin.apiImpl.affixManagerInstance.applyAffixes(item, affixes)
                                AttributeCalculator.markDirty(target)
                                ctx.reply("§a已生成 ${affixes.size} 个词条")
                            }
                            else -> ctx.reply("§c用法: /sym item affix <list|add|clear|generate> ...")
                        }
                        "enhance" -> when (a1) {
                            "get" -> ctx.reply("§b主手强化等级: §a${SymphonyPlugin.growthManager.getEnhanceLevel(item)}")
                            "set" -> {
                                val level = a2.toIntOrNull() ?: return@command ctx.reply("§c无效等级")
                                SymphonyPlugin.growthManager.setEnhanceLevel(item, level)
                                AttributeCalculator.markDirty(target)
                                ctx.reply("§a已设置强化等级为 $level")
                            }
                            else -> ctx.reply("§c用法: /sym item enhance <get|set> [等级]")
                        }
                        "gem" -> {
                            val slotName = a2.uppercase()
                            val slotItem = pickSlot(target, slotName) ?: return@command ctx.reply("§c无效装备槽: $slotName (可选: ${SLOT_NAMES.joinToString()})")
                            when (a1) {
                                "list" -> {
                                    val slots = SymphonyPlugin.growthManager.getGemSlots(slotItem)
                                    if (slots.isEmpty()) return@command ctx.reply("§7该装备无宝石槽")
                                    slots.forEach { s ->
                                        val mark = if (s.locked) "§8[锁定]" else if (s.gemId != null) "§a[${s.gemId} Lv.${s.gemLevel}]" else "§7[空]"
                                        ctx.reply("  §e#${s.index} §f$mark")
                                    }
                                }
                                "insert" -> {
                                    val idx = a3.toIntOrNull() ?: return@command ctx.reply("§c用法: /sym item gem insert <槽位> <索引> <宝石ID> [等级]")
                                    val gemId = a4.takeIf { it.isNotEmpty() } ?: return@command ctx.reply("§c缺少宝石ID")
                                    val level = a5.toIntOrNull() ?: 1
                                    val slots = SymphonyPlugin.growthManager.getGemSlots(slotItem)
                                    if (idx >= slots.size) return@command ctx.reply("§c槽位索引越界：该装备仅 ${slots.size} 个宝石槽")
                                    val ok = SymphonyPlugin.growthManager.gemManager.insertGem(target, slotItem, idx, gemId, level)
                                    AttributeCalculator.markDirty(target)
                                    ctx.reply(if (ok) "§a已镶嵌 $gemId Lv.$level 到 $slotName #$idx" else "§c镶嵌失败（槽位被锁/已占用）")
                                }
                                "remove" -> {
                                    val idx = a3.toIntOrNull() ?: return@command ctx.reply("§c用法: /sym item gem remove <槽位> <索引>")
                                    val ok = SymphonyPlugin.growthManager.removeGem(slotItem, idx)
                                    AttributeCalculator.markDirty(target)
                                    ctx.reply(if (ok) "§a已移除 $slotName #$idx 的宝石" else "§c移除失败（槽位为空）")
                                }
                                "unlock" -> {
                                    val idx = a3.toIntOrNull() ?: return@command ctx.reply("§c用法: /sym item gem unlock <槽位> <索引>")
                                    val ok = SymphonyPlugin.growthManager.unlockSlot(slotItem, idx)
                                    ctx.reply(if (ok) "§a已解锁 $slotName #$idx" else "§c解锁失败")
                                }
                                else -> ctx.reply("§c用法: /sym item gem <list|insert|remove|unlock> <槽位> ...")
                            }
                        }
                        "set" -> when (a1) {
                            "list" -> {
                                val sets = SymphonyPlugin.growthManager.setManager.detectSets(target)
                                if (sets.isEmpty()) return@command ctx.reply("§7无激活套装")
                                sets.forEach { (setId, pieces) ->
                                    val def = SymphonyPlugin.growthManager.setManager.getDefinition(setId)
                                    ctx.reply("  §e${def?.displayName ?: setId} §f($setId) §7× $pieces")
                                }
                            }
                            "mark" -> {
                                val slotName = a2.uppercase()
                                val setId = a3.takeIf { it.isNotEmpty() } ?: return@command ctx.reply("§c用法: /sym item set mark <槽位> <套装ID>")
                                if (SymphonyPlugin.growthManager.setManager.getDefinition(setId) == null) return@command ctx.reply("§c未知套装: $setId")
                                val slotItem = pickSlot(target, slotName) ?: return@command ctx.reply("§c无效装备槽: $slotName")
                                SymphonyItemData.setString(slotItem, "set_id", setId)
                                target.updateInventory()
                                AttributeCalculator.markDirty(target)
                                ctx.reply("§a已标记 $slotName 为套装 $setId")
                            }
                            "unmark" -> {
                                val slotName = a2.uppercase()
                                val slotItem = pickSlot(target, slotName) ?: return@command ctx.reply("§c无效装备槽: $slotName")
                                SymphonyItemData.remove(slotItem, "set_id")
                                target.updateInventory()
                                AttributeCalculator.markDirty(target)
                                ctx.reply("§a已取消标记 $slotName")
                            }
                            else -> ctx.reply("§c用法: /sym item set <list|mark|unmark> ...")
                        }
                        else -> ctx.reply("§c用法: /sym item <attr|affix|enhance|gem|set> ...")
                    }
                }
                // ── 通用命令 ──
                .command("debug", "调试信息", args = arrayOf("?player"), permission = "symphony.admin") { ctx ->
                    val target = ctx.argPlayer(0) ?: (ctx.player ?: return@command ctx.reply("§c请指定玩家"))
                    val data = PlayerDataManager.getData(target.uniqueId)
                    ctx.reply("§8» §f${target.name}")
                    ctx.reply("§8  §7lvl §f${data?.persistent?.level ?: 1}  §7exp §f${data?.persistent?.exp ?: 0}  §7combat §f${data?.runtime?.inCombat ?: false}")
                    ctx.reply("§8  §7buff §f${data?.runtime?.activeBuffs?.size ?: 0}  §7temp_affix §f${data?.runtime?.tempAffixes?.size ?: 0}  §7registered §f${AttributeRegistry.ids().size}")
                }
                .command("explain", "属性流水线", args = arrayOf("explain_attr", "?player"), permission = "symphony.admin") { ctx ->
                    val attrId = ctx.arg(0).takeIf { it.isNotEmpty() } ?: return@command ctx.reply("§c用法: /sym explain <属性ID> [玩家]")
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
                // ── Tab 补全 ──
                // player 命令补全
                .tabComplete("p_sub") { listOf("attr", "buff", "level", "rune") }
                .tabComplete("p_action") { listOf("get", "set", "list", "add", "clear", "activate", "fragment") }
                .tabComplete("p_arg1") { AttributeRegistry.ids().toList() + RuneRegistry.ids().toList() }
                .tabComplete("p_arg3") { listOf("FLAT", "PERCENT") }
                // item 命令补全
                .tabComplete("i_sub") { listOf("attr", "affix", "enhance", "gem", "set") }
                .tabComplete("i_action") { listOf("get", "set", "list", "add", "remove", "clear", "generate", "insert", "unlock", "mark", "unmark") }
                .tabComplete("i_arg1") { AttributeRegistry.ids().toList() + AffixManagerImpl.definitionIds().toList() + AffixManagerImpl.poolIds().toList() + SLOT_NAMES }
                .tabComplete("i_arg3") { listOf("FLAT", "PERCENT") }
                // explain 命令补全
                .tabComplete("explain_attr") { AttributeRegistry.ids().toList() }
        )
    }
}
