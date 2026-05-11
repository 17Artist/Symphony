package priv.seventeen.artist.symphony.core.script.namespace

import net.md_5.bungee.api.ChatMessageType
import net.md_5.bungee.api.chat.TextComponent
import org.bukkit.Bukkit
import org.bukkit.Location
import org.bukkit.Particle
import org.bukkit.Sound
import org.bukkit.attribute.Attribute
import org.bukkit.entity.Entity
import org.bukkit.entity.LivingEntity
import org.bukkit.entity.Mob
import org.bukkit.entity.Player
import org.bukkit.inventory.ItemStack
import org.bukkit.potion.PotionEffect
import org.bukkit.potion.PotionEffectType
import org.bukkit.util.Vector
import priv.seventeen.artist.symphony.api.SymphonyAPI
import priv.seventeen.artist.symphony.api.attribute.Operation
import priv.seventeen.artist.symphony.api.event.SymphonyHealEvent
import priv.seventeen.artist.symphony.api.trigger.TriggerType
import priv.seventeen.artist.symphony.core.advanced.element.ElementAuraSystem
import priv.seventeen.artist.symphony.core.advanced.element.ReactionSystem
import priv.seventeen.artist.symphony.core.advanced.environment.EnvironmentModifier
import priv.seventeen.artist.symphony.core.advanced.environment.EnvironmentSystem
import priv.seventeen.artist.symphony.core.advanced.environment.EnvironmentType
import priv.seventeen.artist.symphony.core.advanced.interaction.InteractionDefinition
import priv.seventeen.artist.symphony.core.advanced.interaction.InteractionNetwork
import priv.seventeen.artist.symphony.core.advanced.interaction.InteractionType
import priv.seventeen.artist.symphony.core.advanced.resonance.ResonanceCondition
import priv.seventeen.artist.symphony.core.advanced.resonance.ResonanceConditionType
import priv.seventeen.artist.symphony.core.advanced.resonance.ResonanceDefinition
import priv.seventeen.artist.symphony.core.advanced.resonance.ResonanceManager
import priv.seventeen.artist.symphony.core.advanced.status.DecayMode
import priv.seventeen.artist.symphony.core.advanced.status.StatusDefinition
import priv.seventeen.artist.symphony.core.advanced.status.StatusLayerSystem
import priv.seventeen.artist.symphony.core.advanced.talent.TalentDefinition
import priv.seventeen.artist.symphony.core.advanced.talent.TalentGate
import priv.seventeen.artist.symphony.core.advanced.talent.TalentManager
import priv.seventeen.artist.symphony.core.attribute.AttributeCache
import priv.seventeen.artist.symphony.core.attribute.AttributeCalculator
import priv.seventeen.artist.symphony.core.attribute.AttributeRegistry
import priv.seventeen.artist.symphony.core.data.ActiveBuff
import priv.seventeen.artist.symphony.core.data.RuneData
import priv.seventeen.artist.symphony.core.growth.gem.GemManager
import priv.seventeen.artist.symphony.core.storage.PlayerDataManager
import priv.seventeen.artist.symphony.core.trigger.CooldownManager
import priv.seventeen.artist.symphony.core.trigger.TriggerDispatcher
import priv.seventeen.artist.symphony.nms.SymphonyItemData
import java.util.UUID

/**
 * Aria 脚本桥接类。
 * Aria 通过 use('...SymphonyBridge') 加载此类，调用其公开方法。
 *
 * 属性声明改用 @attribute 注解式脚本；运行时查询/实体操作/特效保留为桥接方法。
 */
@Suppress("unused")
class SymphonyBridge {

    // symphony.attribute.*  (只读查询 + 运行时读取)
    fun attributeUnregister(id: Any?) {
        AttributeRegistry.unregister(id?.toString() ?: return)
    }

    fun attributeList(): List<String> {
        return AttributeRegistry.ids().toList()
    }

    fun attributeExists(id: Any?): Boolean {
        return AttributeRegistry.exists(id?.toString() ?: return false)
    }

    fun attributeGetInfo(id: Any?): Map<String, Any>? {
        val attr = AttributeRegistry.get(id?.toString() ?: return null) ?: return null
        return mapOf(
            "id" to attr.id,
            "display_name" to attr.displayName,
            "category" to attr.category,
            "default_value" to attr.defaultValue,
            "format" to attr.format,
            "readonly" to attr.readonly
        )
    }

    fun attributeListByCategory(category: Any?): List<String> {
        return AttributeRegistry.getByCategory(category?.toString() ?: return emptyList()).map { it.id }
    }

    fun attributeListByTag(tag: Any?): List<String> {
        return AttributeRegistry.getByTag(tag?.toString() ?: return emptyList()).map { it.id }
    }

    fun attributeGet(entity: Any?, attrId: Any?): Double {
        val id = attrId?.toString() ?: return 0.0
        // derive 上下文中的递归请求 → 走拓扑求解
        AttributeCalculator.lookupForDerive(id)?.let { return it }
        val e = unwrapEntity(entity) as? LivingEntity
            ?: return AttributeRegistry.get(id)?.defaultValue ?: 0.0
        return AttributeCalculator.getValue(e, id)
    }

    fun attributeGetRaw(holder: Any?, attrId: Any?): Double {
        val id = attrId?.toString() ?: return 0.0
        AttributeCalculator.lookupForDerive(id)?.let { return it }
        val e = unwrapEntity(holder) as? LivingEntity
            ?: return AttributeRegistry.get(id)?.defaultValue ?: 0.0
        return AttributeCalculator.getValue(e, id)
    }

    fun attributeRecalculate(entity: Any?) {
        val e = unwrapEntity(entity) as? LivingEntity ?: return
        AttributeCalculator.markDirty(e)
    }

    // symphony.entity.*
    fun entityDamage(target: Any?, amount: Any?, damageType: Any?) {
        val e = unwrapEntity(target) as? LivingEntity ?: return
        val dmg = (amount as? Number)?.toDouble() ?: return
        // damageType 暂作标注用途（事件路径区分），底层仍走 Bukkit 伤害
        e.damage(dmg)
    }

    fun entityHeal(target: Any?, amount: Any?) {
        val e = unwrapEntity(target) as? LivingEntity ?: return
        val heal = (amount as? Number)?.toDouble() ?: return
        val event = SymphonyHealEvent(e, null, heal, "script")
        Bukkit.getPluginManager().callEvent(event)
        if (event.isCancelled) return
        val maxHp = e.getAttribute(Attribute.GENERIC_MAX_HEALTH)?.value ?: e.health
        e.health = (e.health + event.amount).coerceIn(0.0, maxHp)
    }

    fun entityGetHealth(entity: Any?): Double {
        return (unwrapEntity(entity) as? LivingEntity)?.health ?: 0.0
    }

    fun entityGetMaxHealth(entity: Any?): Double {
        val e = unwrapEntity(entity) as? LivingEntity ?: return 0.0
        return e.getAttribute(Attribute.GENERIC_MAX_HEALTH)?.value ?: 0.0
    }

    // symphony.effect.*
    fun effectParticle(location: Any?, particleName: Any?, count: Any?) {
        val loc = unwrapLocation(location) ?: return
        val world = loc.world ?: return
        val name = particleName?.toString()?.uppercase() ?: return
        val n = (count as? Number)?.toInt() ?: 1
        val particle = runCatching { Particle.valueOf(name) }.getOrNull() ?: return
        world.spawnParticle(particle, loc, n)
    }

    fun effectSound(location: Any?, soundName: Any?, volume: Any?, pitch: Any?) {
        val loc = unwrapLocation(location) ?: return
        val world = loc.world ?: return
        val name = soundName?.toString()?.uppercase() ?: return
        val v = (volume as? Number)?.toFloat() ?: 1f
        val p = (pitch as? Number)?.toFloat() ?: 1f
        val sound = runCatching { Sound.valueOf(name) }.getOrNull() ?: return
        world.playSound(loc, sound, v, p)
    }

    // symphony.attribute.* (运行时修改)
    fun attributeSnapshot(entity: Any?): Map<String, Double> {
        val e = unwrapEntity(entity) as? LivingEntity ?: return emptyMap()
        AttributeCalculator.ensureCalculated(e)
        return AttributeCache.getAll(e.uniqueId)
    }

    fun attributeBuff(entity: Any?, attrId: Any?, operation: Any?, value: Any?, duration: Any?) {
        val e = unwrapEntity(entity) as? Player ?: return
        val id = attrId?.toString() ?: return
        val op = if (operation?.toString()?.uppercase() == "PERCENT") Operation.PERCENT else Operation.FLAT
        val v = (value as? Number)?.toDouble() ?: return
        val dur = (duration as? Number)?.toLong() ?: return
        val data = PlayerDataManager.getData(e.uniqueId) ?: return
        data.runtime.activeBuffs.add(ActiveBuff(
            id = "script_buff:$id",
            attribute = id,
            operation = op,
            value = v,
            source = "script",
            expireTime = System.currentTimeMillis() + dur
        ))
        AttributeCache.markDirty(e.uniqueId)
    }

    fun attributeModify(entity: Any?, attrId: Any?, operation: Any?, value: Any?, source: Any?) {
        val e = unwrapEntity(entity) as? Player ?: return
        val id = attrId?.toString() ?: return
        val op = if (operation?.toString()?.uppercase() == "PERCENT") Operation.PERCENT else Operation.FLAT
        val v = (value as? Number)?.toDouble() ?: return
        val src = source?.toString() ?: "script"
        val data = PlayerDataManager.getData(e.uniqueId) ?: return
        data.runtime.activeBuffs.add(ActiveBuff(
            id = "script_modify:$id:$src",
            attribute = id,
            operation = op,
            value = v,
            source = src,
            expireTime = -1L
        ))
        AttributeCache.markDirty(e.uniqueId)
    }

    fun attributeRemove(entity: Any?, source: Any?) {
        val e = unwrapEntity(entity) as? Player ?: return
        val src = source?.toString() ?: return
        val data = PlayerDataManager.getData(e.uniqueId) ?: return
        data.runtime.activeBuffs.removeIf { it.source == src }
        AttributeCache.markDirty(e.uniqueId)
    }

    // symphony.entity.* (扩展)
    fun entitySetHealth(entity: Any?, value: Any?) {
        val e = unwrapEntity(entity) as? LivingEntity ?: return
        val v = (value as? Number)?.toDouble() ?: return
        val max = e.getAttribute(Attribute.GENERIC_MAX_HEALTH)?.value ?: 20.0
        e.health = v.coerceIn(0.0, max)
    }

    fun entityGetMana(entity: Any?): Double {
        val e = unwrapEntity(entity) as? Player ?: return 0.0
        return PlayerDataManager.getData(e.uniqueId)?.runtime?.currentMana ?: 0.0
    }

    fun entitySetMana(entity: Any?, value: Any?) {
        val e = unwrapEntity(entity) as? Player ?: return
        val v = (value as? Number)?.toDouble() ?: return
        val data = PlayerDataManager.getData(e.uniqueId) ?: return
        val max = AttributeCache.get(e.uniqueId, "max_mana") ?: 100.0
        data.runtime.currentMana = v.coerceIn(0.0, max)
    }

    fun entityCostMana(entity: Any?, amount: Any?): Boolean {
        val e = unwrapEntity(entity) as? Player ?: return false
        val cost = (amount as? Number)?.toDouble() ?: return false
        val data = PlayerDataManager.getData(e.uniqueId) ?: return false
        if (data.runtime.currentMana < cost) return false
        data.runtime.currentMana -= cost
        return true
    }

    fun entityGetNearbyHostile(entity: Any?, radius: Any?): List<LivingEntity> {
        val e = unwrapEntity(entity) as? LivingEntity ?: return emptyList()
        val r = (radius as? Number)?.toDouble() ?: 5.0
        return e.getNearbyEntities(r, r, r)
            .filterIsInstance<LivingEntity>()
            .filter { it != e && isHostile(e, it) }
    }

    fun entityGetNearby(entity: Any?, radius: Any?): List<LivingEntity> {
        val e = unwrapEntity(entity) as? LivingEntity ?: return emptyList()
        val r = (radius as? Number)?.toDouble() ?: 5.0
        return e.getNearbyEntities(r, r, r).filterIsInstance<LivingEntity>()
    }

    fun entityGetNearbyPlayers(entity: Any?, radius: Any?): List<Player> {
        val e = unwrapEntity(entity) as? LivingEntity ?: return emptyList()
        val r = (radius as? Number)?.toDouble() ?: 5.0
        return e.getNearbyEntities(r, r, r).filterIsInstance<Player>()
    }

    fun entityAddPotion(entity: Any?, effect: Any?, duration: Any?, amplifier: Any?) {
        val e = unwrapEntity(entity) as? LivingEntity ?: return
        val type = PotionEffectType.getByName(effect?.toString() ?: return) ?: return
        val dur = ((duration as? Number)?.toInt() ?: 200) * 20
        val amp = (amplifier as? Number)?.toInt() ?: 0
        e.addPotionEffect(PotionEffect(type, dur, amp))
    }

    fun entityRemovePotion(entity: Any?, effect: Any?) {
        val e = unwrapEntity(entity) as? LivingEntity ?: return
        val type = PotionEffectType.getByName(effect?.toString() ?: return) ?: return
        e.removePotionEffect(type)
    }

    fun entityHasPotion(entity: Any?, effect: Any?): Boolean {
        val e = unwrapEntity(entity) as? LivingEntity ?: return false
        val type = PotionEffectType.getByName(effect?.toString() ?: return false) ?: return false
        return e.hasPotionEffect(type)
    }

    fun entityGetLocation(entity: Any?): Location? {
        return (unwrapEntity(entity) as? LivingEntity)?.location
    }

    fun entityGetDirection(entity: Any?): Vector? {
        return (unwrapEntity(entity) as? LivingEntity)?.location?.direction
    }

    fun entityTeleport(entity: Any?, location: Any?) {
        val e = unwrapEntity(entity) ?: return
        val loc = unwrapLocation(location) ?: return
        e.teleport(loc)
    }

    fun entitySetVelocity(entity: Any?, x: Any?, y: Any?, z: Any?) {
        val e = unwrapEntity(entity) as? LivingEntity ?: return
        val vx = (x as? Number)?.toDouble() ?: 0.0
        val vy = (y as? Number)?.toDouble() ?: 0.0
        val vz = (z as? Number)?.toDouble() ?: 0.0
        e.velocity = Vector(vx, vy, vz)
    }

    // symphony.effect.* (扩展)
    fun effectParticleAt(x: Any?, y: Any?, z: Any?, world: Any?, particleName: Any?, count: Any?) {
        val w = Bukkit.getWorld(world?.toString() ?: return) ?: return
        val loc = Location(w, (x as? Number)?.toDouble() ?: return, (y as? Number)?.toDouble() ?: return, (z as? Number)?.toDouble() ?: return)
        val particle = runCatching { Particle.valueOf(particleName?.toString()?.uppercase() ?: return) }.getOrNull() ?: return
        w.spawnParticle(particle, loc, (count as? Number)?.toInt() ?: 1)
    }

    fun effectSoundAt(x: Any?, y: Any?, z: Any?, world: Any?, soundName: Any?, volume: Any?, pitch: Any?) {
        val w = Bukkit.getWorld(world?.toString() ?: return) ?: return
        val loc = Location(w, (x as? Number)?.toDouble() ?: return, (y as? Number)?.toDouble() ?: return, (z as? Number)?.toDouble() ?: return)
        val sound = runCatching { Sound.valueOf(soundName?.toString()?.uppercase() ?: return) }.getOrNull() ?: return
        w.playSound(loc, sound, (volume as? Number)?.toFloat() ?: 1f, (pitch as? Number)?.toFloat() ?: 1f)
    }

    fun effectActionbar(entity: Any?, message: Any?) {
        val p = unwrapEntity(entity) as? Player ?: return
        p.spigot().sendMessage(ChatMessageType.ACTION_BAR, TextComponent(message?.toString() ?: return))
    }

    fun effectTitle(entity: Any?, title: Any?, subtitle: Any?, fadeIn: Any?, stay: Any?, fadeOut: Any?) {
        val p = unwrapEntity(entity) as? Player ?: return
        p.sendTitle(title?.toString() ?: "", subtitle?.toString() ?: "", (fadeIn as? Number)?.toInt() ?: 10, (stay as? Number)?.toInt() ?: 40, (fadeOut as? Number)?.toInt() ?: 10)
    }

    fun effectMessage(entity: Any?, message: Any?) {
        val p = unwrapEntity(entity) as? Player ?: return
        p.sendMessage(message?.toString() ?: return)
    }

    fun effectLine(entity1: Any?, entity2: Any?, particleName: Any?, count: Any?, density: Any?) {
        val start = unwrapLocation(entity1) ?: return
        val end = unwrapLocation(entity2) ?: return
        val world = start.world ?: return
        val particle = runCatching { Particle.valueOf(particleName?.toString()?.uppercase() ?: return) }.getOrNull() ?: return
        val steps = ((density as? Number)?.toInt() ?: 10).coerceIn(2, 100)
        val dx = (end.x - start.x) / steps
        val dy = (end.y - start.y) / steps
        val dz = (end.z - start.z) / steps
        for (i in 0..steps) {
            world.spawnParticle(particle, start.x + dx * i, start.y + dy * i, start.z + dz * i, (count as? Number)?.toInt() ?: 1)
        }
    }

    fun effectCircle(location: Any?, particleName: Any?, radius: Any?, count: Any?, points: Any?) {
        val loc = unwrapLocation(location) ?: return
        val world = loc.world ?: return
        val particle = runCatching { Particle.valueOf(particleName?.toString()?.uppercase() ?: return) }.getOrNull() ?: return
        val r = (radius as? Number)?.toDouble() ?: 1.0
        val n = ((points as? Number)?.toInt() ?: 20).coerceIn(4, 100)
        val c = (count as? Number)?.toInt() ?: 1
        for (i in 0 until n) {
            val angle = 2 * Math.PI * i / n
            world.spawnParticle(particle, loc.x + r * Math.cos(angle), loc.y, loc.z + r * Math.sin(angle), c)
        }
    }

    fun effectSphere(location: Any?, particleName: Any?, radius: Any?, count: Any?, points: Any?) {
        val loc = unwrapLocation(location) ?: return
        val world = loc.world ?: return
        val particle = runCatching { Particle.valueOf(particleName?.toString()?.uppercase() ?: return) }.getOrNull() ?: return
        val r = (radius as? Number)?.toDouble() ?: 1.0
        val n = ((points as? Number)?.toInt() ?: 30).coerceIn(8, 200)
        val c = (count as? Number)?.toInt() ?: 1
        val goldenAngle = Math.PI * (3.0 - Math.sqrt(5.0))
        for (i in 0 until n) {
            val y = 1.0 - (2.0 * i / (n - 1))
            val rr = Math.sqrt(1.0 - y * y) * r
            val theta = goldenAngle * i
            world.spawnParticle(particle, loc.x + rr * Math.cos(theta), loc.y + y * r, loc.z + rr * Math.sin(theta), c)
        }
    }

    fun effectHelix(location: Any?, particleName: Any?, radius: Any?, height: Any?, count: Any?, turns: Any?) {
        val loc = unwrapLocation(location) ?: return
        val world = loc.world ?: return
        val particle = runCatching { Particle.valueOf(particleName?.toString()?.uppercase() ?: return) }.getOrNull() ?: return
        val r = (radius as? Number)?.toDouble() ?: 1.0
        val h = (height as? Number)?.toDouble() ?: 3.0
        val t = ((turns as? Number)?.toInt() ?: 3).coerceIn(1, 10)
        val c = (count as? Number)?.toInt() ?: 1
        val steps = t * 20
        for (i in 0..steps) {
            val angle = 2 * Math.PI * t * i / steps
            val dy = h * i / steps
            world.spawnParticle(particle, loc.x + r * Math.cos(angle), loc.y + dy, loc.z + r * Math.sin(angle), c)
        }
    }

    // symphony.item.*
    fun itemGetAttributes(entity: Any?): Map<String, Double> {
        val e = unwrapEntity(entity) as? LivingEntity ?: return emptyMap()
        AttributeCalculator.ensureCalculated(e)
        return AttributeCache.getAll(e.uniqueId)
    }

    fun itemGetMainHand(entity: Any?): ItemStack? {
        return (unwrapEntity(entity) as? Player)?.inventory?.itemInMainHand
    }

    fun itemGetOffHand(entity: Any?): ItemStack? {
        return (unwrapEntity(entity) as? Player)?.inventory?.itemInOffHand
    }

    fun itemGetAffixes(item: Any?): List<Map<String, Any>> {
        val stack = item as? ItemStack ?: return emptyList()
        val api = SymphonyAPI.getInstance() ?: return emptyList()
        return api.getAffixManager().getAffixes(stack).map {
            mapOf("uuid" to it.uuid.toString(), "affix_id" to it.affixId, "level" to it.level)
        }
    }

    fun itemGetEnhanceLevel(item: Any?): Int {
        val stack = item as? ItemStack ?: return 0
        return SymphonyItemData.getInt(stack, "enhance_level") ?: 0
    }

    fun itemGetSetId(item: Any?): String? {
        val stack = item as? ItemStack ?: return null
        return SymphonyItemData.getString(stack, "set_id")
    }

    fun itemHasAffix(item: Any?, affixId: Any?): Boolean {
        val stack = item as? ItemStack ?: return false
        val id = affixId?.toString() ?: return false
        val api = SymphonyAPI.getInstance() ?: return false
        return api.getAffixManager().getAffixes(stack).any { it.affixId == id }
    }

    fun itemGetEquipment(entity: Any?): Map<String, Any?> {
        val p = unwrapEntity(entity) as? Player ?: return emptyMap()
        val eq = p.inventory
        return mapOf(
            "mainHand" to eq.itemInMainHand,
            "offHand" to eq.itemInOffHand,
            "helmet" to eq.helmet,
            "chestplate" to eq.chestplate,
            "leggings" to eq.leggings,
            "boots" to eq.boots
        )
    }

    fun itemGetGems(item: Any?): List<Map<String, Any?>> {
        val stack = item as? ItemStack ?: return emptyList()
        return GemManager().getGemSlots(stack).map {
            mapOf("index" to it.index, "gemId" to it.gemId, "gemLevel" to it.gemLevel, "locked" to it.locked)
        }
    }

    fun itemGetRarity(item: Any?): String? {
        val stack = item as? ItemStack ?: return null
        return SymphonyItemData.getString(stack, "rarity")
    }

    // symphony.trigger.*
    fun triggerDispatch(type: Any?, entity: Any?) {
        val e = unwrapEntity(entity) as? LivingEntity ?: return
        val triggerType = TriggerType.of(type?.toString() ?: return)
        TriggerDispatcher.dispatch(triggerType, e) {}
    }

    fun triggerIsOnCooldown(entity: Any?, key: Any?): Boolean {
        val e = unwrapEntity(entity) ?: return false
        return CooldownManager.isOnCooldown(e.uniqueId, key?.toString() ?: return false)
    }

    fun triggerGetCooldown(entity: Any?, key: Any?): Long {
        val e = unwrapEntity(entity) ?: return 0L
        return CooldownManager.getRemaining(e.uniqueId, key?.toString() ?: return 0L)
    }

    fun triggerSetCooldown(entity: Any?, key: Any?, duration: Any?) {
        val e = unwrapEntity(entity) ?: return
        val dur = (duration as? Number)?.toLong() ?: return
        CooldownManager.setCooldown(e.uniqueId, key?.toString() ?: return, dur)
    }

    // symphony.growth.*
    fun growthGetLevel(entity: Any?): Int {
        val p = unwrapEntity(entity) as? Player ?: return 0
        return PlayerDataManager.getData(p.uniqueId)?.persistent?.level ?: 1
    }

    fun growthSetLevel(entity: Any?, level: Any?) {
        val p = unwrapEntity(entity) as? Player ?: return
        val lvl = (level as? Number)?.toInt() ?: return
        PlayerDataManager.getData(p.uniqueId)?.persistent?.level = lvl
        AttributeCache.markDirty(p.uniqueId)
    }

    fun growthAddExp(entity: Any?, amount: Any?, source: Any?) {
        val p = unwrapEntity(entity) as? Player ?: return
        val amt = (amount as? Number)?.toLong() ?: return
        val src = source?.toString() ?: "script"
        PlayerDataManager.getData(p.uniqueId)?.let {
            it.persistent.exp += amt
        }
    }

    fun growthGetEnhanceLevel(item: Any?): Int {
        val stack = item as? ItemStack ?: return 0
        return SymphonyItemData.getInt(stack, "enhance_level") ?: 0
    }

    fun growthSetEnhanceLevel(item: Any?, level: Any?) {
        val stack = item as? ItemStack ?: return
        val lvl = (level as? Number)?.toInt() ?: return
        SymphonyItemData.setInt(stack, "enhance_level", lvl)
    }

    fun growthActivateRune(entity: Any?, runeId: Any?, level: Any?): Boolean {
        val p = unwrapEntity(entity) as? Player ?: return false
        val id = runeId?.toString() ?: return false
        val lvl = (level as? Number)?.toInt() ?: 1
        val data = PlayerDataManager.getData(p.uniqueId) ?: return false
        data.persistent.runes[id] = RuneData(runeId = id, level = lvl, active = true)
        AttributeCache.markDirty(p.uniqueId)
        return true
    }

    fun growthAddFragments(entity: Any?, runeId: Any?, amount: Any?) {
        val p = unwrapEntity(entity) as? Player ?: return
        val id = runeId?.toString() ?: return
        val amt = (amount as? Number)?.toInt() ?: return
        val data = PlayerDataManager.getData(p.uniqueId) ?: return
        data.persistent.runeFragments[id] = (data.persistent.runeFragments[id] ?: 0) + amt
    }

    fun growthGetFragments(entity: Any?, runeId: Any?): Int {
        val p = unwrapEntity(entity) as? Player ?: return 0
        val id = runeId?.toString() ?: return 0
        return PlayerDataManager.getData(p.uniqueId)?.persistent?.runeFragments?.get(id) ?: 0
    }

    fun growthInsertGem(item: Any?, slotIndex: Any?, gemId: Any?, gemLevel: Any?): Boolean {
        val stack = item as? ItemStack ?: return false
        val idx = (slotIndex as? Number)?.toInt() ?: return false
        val id = gemId?.toString() ?: return false
        val lvl = (gemLevel as? Number)?.toInt() ?: 1
        return GemManager().insertGem(stack, idx, id, lvl)
    }

    fun growthEnhance(entity: Any?, item: Any?): String {
        val p = unwrapEntity(entity) as? Player ?: return "INVALID"
        val stack = item as? ItemStack ?: return "INVALID"
        val api = SymphonyAPI.getInstance()
        val result = api.getGrowthManager().enhance(p, stack, emptyList())
        return result.name
    }

    fun growthRemoveGem(item: Any?, slotIndex: Any?): Boolean {
        val stack = item as? ItemStack ?: return false
        val idx = (slotIndex as? Number)?.toInt() ?: return false
        return GemManager().removeGem(stack, idx)
    }

    // ═══════════════════════════════════════
    // symphony.element.* — 元素系统
    // ═══════════════════════════════════════
    fun elementApplyAura(entity: Any?, element: Any?, gauge: Any?) {
        val e = unwrapEntity(entity) as? LivingEntity ?: return
        val elem = element?.toString() ?: return
        val g = (gauge as? Number)?.toDouble() ?: 1.0
        ElementAuraSystem.applyAura(e, elem, g)
    }

    fun elementGetAura(entity: Any?, element: Any?): Double {
        val e = unwrapEntity(entity) as? LivingEntity ?: return 0.0
        val aura = ElementAuraSystem.getAura(e, element?.toString() ?: return 0.0)
        return aura?.gauge ?: 0.0
    }

    fun elementRemoveAura(entity: Any?, element: Any?) {
        val e = unwrapEntity(entity) as? LivingEntity ?: return
        ElementAuraSystem.removeAura(e, element?.toString() ?: return)
    }

    fun elementGetAllAuras(entity: Any?): Map<String, Double> {
        val e = unwrapEntity(entity) as? LivingEntity ?: return emptyMap()
        return ElementAuraSystem.getAllAuras(e).mapValues { it.value.gauge }
    }

    fun elementTryReaction(attacker: Any?, target: Any?, element: Any?): Boolean {
        val a = unwrapEntity(attacker) as? LivingEntity ?: return false
        val t = unwrapEntity(target) as? LivingEntity ?: return false
        val elem = element?.toString() ?: return false
        val auras = ElementAuraSystem.getAllAuras(t)
        if (auras.isEmpty()) return false
        for ((auraElem, _) in auras) {
            ReactionSystem.tryReaction(t, elem, auraElem, a.uniqueId)
        }
        return true
    }

    // ═══════════════════════════════════════
    // symphony.status.* — 状态层系统
    // ═══════════════════════════════════════
    fun statusAddStacks(entity: Any?, statusId: Any?, stacks: Any?, source: Any?) {
        val e = unwrapEntity(entity) as? LivingEntity ?: return
        val appliedBy = (unwrapEntity(source) as? LivingEntity)?.uniqueId
        StatusLayerSystem.addStacks(
            e, statusId?.toString() ?: return, (stacks as? Number)?.toInt() ?: 1, appliedBy
        )
    }

    fun statusGetStacks(entity: Any?, statusId: Any?): Int {
        val e = unwrapEntity(entity) as? LivingEntity ?: return 0
        return StatusLayerSystem.getStacks(e, statusId?.toString() ?: return 0)
    }

    fun statusClearStacks(entity: Any?, statusId: Any?) {
        val e = unwrapEntity(entity) as? LivingEntity ?: return
        StatusLayerSystem.clearStacks(e, statusId?.toString() ?: return)
    }

    fun statusSetImmune(entity: Any?, statusId: Any?, duration: Any?) {
        val e = unwrapEntity(entity) as? LivingEntity ?: return
        StatusLayerSystem.setImmune(
            e, statusId?.toString() ?: return, (duration as? Number)?.toLong() ?: 5000L
        )
    }

    // ═══════════════════════════════════════
    // symphony.resonance.* — 词条共鸣
    // ═══════════════════════════════════════
    fun resonanceGetActive(entity: Any?): Set<String> {
        val p = unwrapEntity(entity) as? Player ?: return emptySet()
        return ResonanceManager.getActiveResonances(p)
    }

    fun resonanceCheck(entity: Any?) {
        val p = unwrapEntity(entity) as? Player ?: return
        ResonanceManager.checkResonances(p)
    }

    // ═══════════════════════════════════════
    // symphony.talent.* — 天赋门
    // ═══════════════════════════════════════
    fun talentIsUnlocked(entity: Any?, talentId: Any?): Boolean {
        val p = unwrapEntity(entity) as? Player ?: return false
        return TalentManager.isUnlocked(p, talentId?.toString() ?: return false)
    }

    fun talentCheck(entity: Any?) {
        val p = unwrapEntity(entity) as? Player ?: return
        TalentManager.checkTalents(p)
    }

    // ═══════════════════════════════════════
    // symphony.environment.* — 环境系统
    // ═══════════════════════════════════════
    fun environmentGetActive(entity: Any?): List<String> {
        val p = unwrapEntity(entity) as? Player ?: return emptyList()
        return EnvironmentSystem.getActiveModifiers(p).toList()
    }

    fun environmentRegister(id: Any?, displayName: Any?, type: Any?) {
        val envType = runCatching {
            EnvironmentType.valueOf(type?.toString()?.uppercase() ?: "DIMENSION")
        }.getOrDefault(EnvironmentType.DIMENSION)
        EnvironmentSystem.register(
            EnvironmentModifier(
                id = id?.toString() ?: return,
                displayName = displayName?.toString() ?: id.toString(),
                type = envType
            )
        )
    }

    fun environmentList(): List<String> {
        return EnvironmentSystem.getAll().map { it.id }
    }

    // ═══════════════════════════════════════
    // symphony.interaction.* — 属性交互网络
    // ═══════════════════════════════════════
    fun interactionRegister(id: Any?, type: Any?, source: Any?, target: Any?, threshold: Any?, ratio: Any?, multiplier: Any?) {
        val defId = id?.toString() ?: return
        val interType = runCatching { InteractionType.valueOf(type?.toString()?.uppercase() ?: return) }.getOrNull() ?: return
        InteractionNetwork.register(
            InteractionDefinition(
                id = defId, type = interType,
                source = source?.toString(), target = target?.toString(),
                threshold = (threshold as? Number)?.toDouble() ?: 0.0,
                ratio = (ratio as? Number)?.toDouble() ?: 0.0,
                multiplier = (multiplier as? Number)?.toDouble() ?: 1.0
            )
        )
    }

    fun interactionRemove(id: Any?) {
        InteractionNetwork.unregister(id?.toString() ?: return)
    }

    fun interactionList(): List<String> {
        return InteractionNetwork.getAll().map { it.id }
    }

    // ═══════════════════════════════════════
    // symphony.world.* — 世界信息
    // ═══════════════════════════════════════
    fun worldGetTime(entity: Any?): Long {
        val e = unwrapEntity(entity) ?: return 0L
        return e.world.time
    }

    fun worldIsRaining(entity: Any?): Boolean {
        val e = unwrapEntity(entity) ?: return false
        return e.world.hasStorm()
    }

    fun worldIsThundering(entity: Any?): Boolean {
        val e = unwrapEntity(entity) ?: return false
        return e.world.isThundering
    }

    fun worldGetDimension(entity: Any?): String {
        val e = unwrapEntity(entity) ?: return "NORMAL"
        return e.world.environment.name
    }

    fun worldGetBiome(entity: Any?): String {
        val e = unwrapEntity(entity) ?: return "PLAINS"
        return e.location.block.biome.name
    }

    fun worldIsOutdoor(entity: Any?): Boolean {
        val e = unwrapEntity(entity) as? LivingEntity ?: return false
        val loc = e.location
        return (loc.world?.getHighestBlockYAt(loc) ?: 0) <= loc.blockY
    }

    // ═══════════════════════════════════════
    // symphony.status.* — 状态层系统（补充）
    // ═══════════════════════════════════════
    fun statusRegister(id: Any?, displayName: Any?, maxStacks: Any?, stackDuration: Any?, decayMode: Any?) {
        StatusLayerSystem.register(
            StatusDefinition(
                id = id?.toString() ?: return,
                displayName = displayName?.toString() ?: id.toString(),
                maxStacks = (maxStacks as? Number)?.toInt() ?: 10,
                stackDuration = (stackDuration as? Number)?.toLong() ?: 8000,
                decayMode = if (decayMode?.toString()?.uppercase() == "REFRESH")
                    DecayMode.REFRESH
                else DecayMode.INDIVIDUAL
            )
        )
    }

    fun statusList(): List<String> {
        return StatusLayerSystem.getAll().map { it.id }
    }

    // ═══════════════════════════════════════
    // symphony.resonance.* — 词条共鸣（补充）
    // ═══════════════════════════════════════
    fun resonanceRegister(id: Any?, displayName: Any?, conditionType: Any?, tag: Any?, count: Any?) {
        val condType = runCatching {
            ResonanceConditionType.valueOf(conditionType?.toString()?.uppercase() ?: "AFFIX_TAG_COUNT")
        }.getOrDefault(ResonanceConditionType.AFFIX_TAG_COUNT)
        ResonanceManager.register(
            ResonanceDefinition(
                id = id?.toString() ?: return,
                displayName = displayName?.toString() ?: id.toString(),
                condition = ResonanceCondition(
                    type = condType, tag = tag?.toString(), count = (count as? Number)?.toInt() ?: 0
                )
            )
        )
    }

    fun resonanceList(): List<String> {
        return ResonanceManager.getAll().map { it.id }
    }

    // ═══════════════════════════════════════
    // symphony.talent.* — 天赋门（补充）
    // ═══════════════════════════════════════
    fun talentRegister(id: Any?, displayName: Any?, gateAttr: Any?, gateThreshold: Any?, gateOp: Any?) {
        TalentManager.register(
            TalentDefinition(
                id = id?.toString() ?: return,
                displayName = displayName?.toString() ?: id.toString(),
                gate = TalentGate(
                    type = "SINGLE",
                    attribute = gateAttr?.toString(),
                    threshold = (gateThreshold as? Number)?.toDouble() ?: 0.0,
                    operator = gateOp?.toString() ?: ">="
                )
            )
        )
    }

    fun talentGetStatus(entity: Any?): Map<String, Boolean> {
        val p = unwrapEntity(entity) as? Player ?: return emptyMap()
        return TalentManager.getStatus(p)
            .mapValues { it.value.unlocked }
    }

    fun talentList(): List<String> {
        return TalentManager.getAll().map { it.id }
    }

    // 辅助：脚本对象 → Bukkit 类型
    private fun unwrapEntity(v: Any?): Entity? = when (v) {
        null -> null
        is Entity -> v
        is UUID -> Bukkit.getEntity(v)
        is String -> runCatching { Bukkit.getEntity(UUID.fromString(v)) }.getOrNull()
        else -> {
            // Aria 引擎包装对象：尝试提取内部 Java 对象
            val jvmValue = runCatching {
                v.javaClass.getMethod("jvmValue").invoke(v)
            }.getOrNull()
            when (jvmValue) {
                is Entity -> jvmValue
                is UUID -> Bukkit.getEntity(jvmValue)
                is String -> runCatching { Bukkit.getEntity(UUID.fromString(jvmValue)) }.getOrNull()
                else -> null
            }
        }
    }

    private fun unwrapLocation(v: Any?): Location? = when (v) {
        null -> null
        is Location -> v
        is Entity -> v.location
        else -> null
    }

    private fun isHostile(origin: LivingEntity, target: LivingEntity): Boolean {
        if (origin is Player && target is Player) return true
        if (origin is Player && target is Mob) return true
        if (origin is Mob && target is Player) return true
        if (origin is Mob && target is Mob) return origin.type != target.type
        return false
    }
}
