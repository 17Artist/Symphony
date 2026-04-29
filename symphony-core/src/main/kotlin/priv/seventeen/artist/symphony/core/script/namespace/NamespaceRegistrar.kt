package priv.seventeen.artist.symphony.core.script.namespace

import priv.seventeen.artist.blink.BlinkLog
import priv.seventeen.artist.blink.script.AriaScriptManager

/**
 * 将 symphony.* API 注入 Aria 全局作用域（GlobalStorage 跨脚本共享）。
 *
 * 通过一段 bootstrap 脚本把 SymphonyBridge 实例的方法包装成嵌套对象
 * `global.symphony.attribute.* / entity.* / effect.*`，
 * 让所有 .aria 文件都能直接调用。
 *
 * 注意：属性的「定义」改用 @attribute 注解式脚本，
 * 这里只暴露查询/运行时操作，不再有 register / registerAll。
 */
object NamespaceRegistrar {

    fun registerAll() {
        if (!AriaScriptManager.isAvailable) return

        // 将 Kotlin 单例注册为 Aria 全局变量，脚本通过 Java 互操作调用
        val bootstrapCode = """
            val.SymphonyBridge = use('priv.seventeen.artist.symphony.core.script.namespace.SymphonyBridge')
            val.bridge = SymphonyBridge()

            global.symphony = {
                'attribute': {
                    'unregister': -> { bridge.attributeUnregister(args[0]) },
                    'list': -> { return bridge.attributeList() },
                    'exists': -> { return bridge.attributeExists(args[0]) },
                    'getInfo': -> { return bridge.attributeGetInfo(args[0]) },
                    'listByCategory': -> { return bridge.attributeListByCategory(args[0]) },
                    'listByTag': -> { return bridge.attributeListByTag(args[0]) },
                    'get': -> { return bridge.attributeGet(args[0], args[1]) },
                    'getRaw': -> { return bridge.attributeGetRaw(args[0], args[1]) },
                    'snapshot': -> { return bridge.attributeSnapshot(args[0]) },
                    'buff': -> { bridge.attributeBuff(args[0], args[1], args[2], args[3], args[4]) },
                    'modify': -> { bridge.attributeModify(args[0], args[1], args[2], args[3], args[4]) },
                    'remove': -> { bridge.attributeRemove(args[0], args[1]) },
                    'recalculate': -> { bridge.attributeRecalculate(args[0]) }
                },
                'entity': {
                    'damage': -> { bridge.entityDamage(args[0], args[1], args[2]) },
                    'heal': -> { bridge.entityHeal(args[0], args[1]) },
                    'getHealth': -> { return bridge.entityGetHealth(args[0]) },
                    'getMaxHealth': -> { return bridge.entityGetMaxHealth(args[0]) },
                    'setHealth': -> { bridge.entitySetHealth(args[0], args[1]) },
                    'getMana': -> { return bridge.entityGetMana(args[0]) },
                    'setMana': -> { bridge.entitySetMana(args[0], args[1]) },
                    'costMana': -> { return bridge.entityCostMana(args[0], args[1]) },
                    'getNearby': -> { return bridge.entityGetNearby(args[0], args[1]) },
                    'getNearbyPlayers': -> { return bridge.entityGetNearbyPlayers(args[0], args[1]) },
                    'addPotion': -> { bridge.entityAddPotion(args[0], args[1], args[2], args[3]) },
                    'removePotion': -> { bridge.entityRemovePotion(args[0], args[1]) },
                    'hasPotion': -> { return bridge.entityHasPotion(args[0], args[1]) },
                    'getLocation': -> { return bridge.entityGetLocation(args[0]) },
                    'getDirection': -> { return bridge.entityGetDirection(args[0]) },
                    'teleport': -> { bridge.entityTeleport(args[0], args[1]) },
                    'setVelocity': -> { bridge.entitySetVelocity(args[0], args[1], args[2], args[3]) },
                    'getNearbyHostile': -> { return bridge.entityGetNearbyHostile(args[0], args[1]) }
                },
                'effect': {
                    'particle': -> { bridge.effectParticle(args[0], args[1], args[2]) },
                    'sound': -> { bridge.effectSound(args[0], args[1], args[2], args[3]) },
                    'particleAt': -> { bridge.effectParticleAt(args[0], args[1], args[2], args[3], args[4], args[5]) },
                    'soundAt': -> { bridge.effectSoundAt(args[0], args[1], args[2], args[3], args[4], args[5], args[6]) },
                    'actionbar': -> { bridge.effectActionbar(args[0], args[1]) },
                    'title': -> { bridge.effectTitle(args[0], args[1], args[2], args[3], args[4], args[5]) },
                    'message': -> { bridge.effectMessage(args[0], args[1]) },
                    'line': -> { bridge.effectLine(args[0], args[1], args[2], args[3], args[4]) },
                    'circle': -> { bridge.effectCircle(args[0], args[1], args[2], args[3], args[4]) },
                    'sphere': -> { bridge.effectSphere(args[0], args[1], args[2], args[3], args[4]) },
                    'helix': -> { bridge.effectHelix(args[0], args[1], args[2], args[3], args[4], args[5]) }
                },
                'item': {
                    'getMainHand': -> { return bridge.itemGetMainHand(args[0]) },
                    'getOffHand': -> { return bridge.itemGetOffHand(args[0]) },
                    'getAffixes': -> { return bridge.itemGetAffixes(args[0]) },
                    'getEnhanceLevel': -> { return bridge.itemGetEnhanceLevel(args[0]) },
                    'getSetId': -> { return bridge.itemGetSetId(args[0]) },
                    'hasAffix': -> { return bridge.itemHasAffix(args[0], args[1]) },
                    'getEquipment': -> { return bridge.itemGetEquipment(args[0]) },
                    'getGems': -> { return bridge.itemGetGems(args[0]) },
                    'getRarity': -> { return bridge.itemGetRarity(args[0]) },
                    'getAttributes': -> { return bridge.itemGetAttributes(args[0]) }
                },
                'trigger': {
                    'dispatch': -> { bridge.triggerDispatch(args[0], args[1]) },
                    'isOnCooldown': -> { return bridge.triggerIsOnCooldown(args[0], args[1]) },
                    'getCooldown': -> { return bridge.triggerGetCooldown(args[0], args[1]) },
                    'setCooldown': -> { bridge.triggerSetCooldown(args[0], args[1], args[2]) }
                },
                'growth': {
                    'getLevel': -> { return bridge.growthGetLevel(args[0]) },
                    'setLevel': -> { bridge.growthSetLevel(args[0], args[1]) },
                    'addExp': -> { bridge.growthAddExp(args[0], args[1], args[2]) },
                    'getEnhanceLevel': -> { return bridge.growthGetEnhanceLevel(args[0]) },
                    'setEnhanceLevel': -> { bridge.growthSetEnhanceLevel(args[0], args[1]) },
                    'activateRune': -> { return bridge.growthActivateRune(args[0], args[1], args[2]) },
                    'addFragments': -> { bridge.growthAddFragments(args[0], args[1], args[2]) },
                    'getFragments': -> { return bridge.growthGetFragments(args[0], args[1]) },
                    'insertGem': -> { return bridge.growthInsertGem(args[0], args[1], args[2], args[3]) },
                    'removeGem': -> { return bridge.growthRemoveGem(args[0], args[1]) },
                    'addGem': -> { return bridge.growthInsertGem(args[0], args[1], args[2], args[3]) },
                    'enhance': -> { return bridge.growthEnhance(args[0], args[1]) }
                },
                'element': {
                    'applyAura': -> { bridge.elementApplyAura(args[0], args[1], args[2]) },
                    'getAura': -> { return bridge.elementGetAura(args[0], args[1]) },
                    'removeAura': -> { bridge.elementRemoveAura(args[0], args[1]) },
                    'getAllAuras': -> { return bridge.elementGetAllAuras(args[0]) },
                    'tryReaction': -> { return bridge.elementTryReaction(args[0], args[1], args[2]) }
                },
                'status': {
                    'register': -> { bridge.statusRegister(args[0], args[1], args[2], args[3], args[4]) },
                    'addStacks': -> { bridge.statusAddStacks(args[0], args[1], args[2], args[3]) },
                    'getStacks': -> { return bridge.statusGetStacks(args[0], args[1]) },
                    'clearStacks': -> { bridge.statusClearStacks(args[0], args[1]) },
                    'setImmune': -> { bridge.statusSetImmune(args[0], args[1], args[2]) },
                    'list': -> { return bridge.statusList() }
                },
                'resonance': {
                    'register': -> { bridge.resonanceRegister(args[0], args[1], args[2], args[3], args[4]) },
                    'getActive': -> { return bridge.resonanceGetActive(args[0]) },
                    'check': -> { bridge.resonanceCheck(args[0]) },
                    'list': -> { return bridge.resonanceList() }
                },
                'talent': {
                    'register': -> { bridge.talentRegister(args[0], args[1], args[2], args[3], args[4]) },
                    'isUnlocked': -> { return bridge.talentIsUnlocked(args[0], args[1]) },
                    'check': -> { bridge.talentCheck(args[0]) },
                    'getStatus': -> { return bridge.talentGetStatus(args[0]) },
                    'list': -> { return bridge.talentList() }
                },
                'environment': {
                    'register': -> { bridge.environmentRegister(args[0], args[1], args[2]) },
                    'getActive': -> { return bridge.environmentGetActive(args[0]) },
                    'list': -> { return bridge.environmentList() }
                },
                'interaction': {
                    'register': -> { bridge.interactionRegister(args[0], args[1], args[2], args[3], args[4], args[5], args[6]) },
                    'remove': -> { bridge.interactionRemove(args[0]) },
                    'list': -> { return bridge.interactionList() }
                },
                'world': {
                    'getTime': -> { return bridge.worldGetTime(args[0]) },
                    'isRaining': -> { return bridge.worldIsRaining(args[0]) },
                    'isThundering': -> { return bridge.worldIsThundering(args[0]) },
                    'getDimension': -> { return bridge.worldGetDimension(args[0]) },
                    'getBiome': -> { return bridge.worldGetBiome(args[0]) },
                    'isOutdoor': -> { return bridge.worldIsOutdoor(args[0]) }
                }
            }
        """.trimIndent()

        try {
            AriaScriptManager.eval(bootstrapCode)
            BlinkLog.detail("  §7已注册 §fsymphony.* §7命名空间")
        } catch (e: Exception) {
            BlinkLog.error("命名空间注册失败: ${e.message}")
            e.printStackTrace()
        }
    }
}
