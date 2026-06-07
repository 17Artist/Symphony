package priv.seventeen.artist.symphony.plugin.listener

import org.bukkit.Bukkit
import org.bukkit.entity.Player
import priv.seventeen.artist.blink.BlinkLog
import priv.seventeen.artist.blink.bukkitPlugin
import priv.seventeen.artist.symphony.core.attribute.AttributeCache
import priv.seventeen.artist.symphony.core.attribute.AttributeCalculator
import priv.seventeen.artist.symphony.core.attribute.VanillaAttributeBridge
import priv.seventeen.artist.symphony.plugin.SymphonyPlugin
import java.util.UUID
import java.util.concurrent.ConcurrentHashMap

/**
 * Epic Fight 动画事件监听器（反射实现版本）。
 *
 * 用于追踪玩家的动画状态，当玩家正在播放动画时（inaction），
 * 延迟属性重算操作，避免动画卡住的问题。
 *
 * 使用反射访问 Epic Fight API，避免直接依赖。
 *
 * 解决方案：
 * 1. 监听 ANIMATION_BEGIN_EVENT 和 ANIMATION_END_EVENT 来追踪动画状态
 * 2. 在 markPendingRecalc 中检查玩家是否正在播放动画（事件标志 或 inaction 反射）
 * 3. 如果正在播放动画，延迟属性重算到动画结束
 *
 * 配置开关：compatibility.epicfight-enabled（关闭时回落到立即 markDirty，与无 Epic Fight 一致）。
 */
object EpicFightAnimationListener {

    /** 与 EF PlayerPatch.PLAYER_EVENT_UUID 区分，避免冲突 */
    private val SYMPHONY_EVENT_UUID: UUID = UUID.fromString("a1b2c3d4-e5f6-4789-a012-3456789abcde")

    // 追踪玩家是否正在播放动画（inaction 状态）
    private val animationState = ConcurrentHashMap<UUID, Boolean>()

    // 待重算的玩家队列（当动画结束时触发）
    private val pendingRecalc = ConcurrentHashMap.newKeySet<UUID>()

    // 延迟重算完成后需执行的后处理（如同步原版属性）
    private val pendingAfterRecalc = ConcurrentHashMap<UUID, () -> Unit>()

    /** 换世界后一段时间内不因 inaction 推迟同步（EF 常误报 inaction） */
    private val postWorldGraceUntil = ConcurrentHashMap<UUID, Long>()

    /** 持续 inaction 的起始时间，用于兜底解除卡动作 */
    private val inactionSinceMs = ConcurrentHashMap<UUID, Long>()

    /** Teleport + ChangedWorld 短时间去重（毫秒） */
    private val worldChangeLastMs = ConcurrentHashMap<UUID, Long>()

    @Volatile
    private var loggedCraftPlayerClass = false

    // 是否启用：配置开启 && Epic Fight 类存在
    @Volatile
    private var enabled = false

    private fun efLog(message: String) {
        if (!enabled) return
        BlinkLog.info("§7[Symphony/EF]§f $message")
    }

    private fun efDebug(message: String) {
        if (!enabled) return
        val debugOn = try {
            SymphonyPlugin.config.debug
        } catch (_: Exception) {
            false
        }
        if (debugOn) {
            BlinkLog.info("§8[Symphony/EF/D]§f $message")
        }
    }

    private fun playerTag(player: Player): String =
        "${player.name}(${player.uniqueId.toString().take(8)})"

    private fun snapshot(player: Player): String {
        val uuid = player.uniqueId
        val anim = animationState[uuid] == true
        val inaction = try {
            isInInactionState(player)
        } catch (_: Exception) {
            false
        }
        val grace = inPostWorldGrace(uuid)
        val dirty = AttributeCache.isDirty(uuid)
        val pending = uuid in pendingRecalc
        val world = player.world.name
        return "world=$world anim=$anim inaction=$inaction grace=$grace dirty=$dirty pending=$pending"
    }

    // ========== 反射相关字段 ==========

    // 安全检查类是否存在（避免加载不存在的类）
    private fun classExists(className: String): Boolean {
        return try {
            Class.forName(className, false, EpicFightAnimationListener::class.java.classLoader)
            true
        } catch (e: ClassNotFoundException) {
            false
        }
    }

    // 检查 Epic Fight 是否可用
    private val isEpicFightAvailable: Boolean by lazy {
        classExists("yesman.epicfight.world.capabilities.EpicFightCapabilities") &&
        classExists("yesman.epicfight.world.capabilities.entitypatch.player.ServerPlayerPatch")
    }

    /** 供启动日志判断：Epic Fight 反射环境是否就绪 */
    val available: Boolean get() = isEpicFightAvailable

    private fun epicClassLoader(): ClassLoader =
        EpicFightAnimationListener::class.java.classLoader

    /** Mohist/Forge 上 getMethod/getMethods 会解析客户端类导致 NoClassDefFoundError */
    private fun isMohistUnsafeReflection(t: Throwable): Boolean =
        t is NoClassDefFoundError || t is LinkageError ||
            (t is Exception && t.cause is NoClassDefFoundError)

    private fun findDeclaredMethod(clazz: Class<*>, name: String, vararg paramTypes: Class<*>): java.lang.reflect.Method? {
        return try {
            clazz.getDeclaredMethod(name, *paramTypes)
        } catch (_: NoSuchMethodException) {
            null
        } catch (t: Throwable) {
            if (isMohistUnsafeReflection(t)) null else throw t
        }
    }

    private fun findDeclaredNoArgMethod(clazz: Class<*>, name: String): java.lang.reflect.Method? =
        findDeclaredMethod(clazz, name)

    private fun findStaticDeclaredMethod(
        clazz: Class<*>,
        name: String,
        paramType: Class<*>,
    ): java.lang.reflect.Method? {
        return try {
            clazz.declaredMethods.firstOrNull { m ->
                m.name == name &&
                    java.lang.reflect.Modifier.isStatic(m.modifiers) &&
                    m.parameterCount == 1 &&
                    m.parameterTypes[0].isAssignableFrom(paramType)
            } ?: clazz.declaredMethods.firstOrNull { m ->
                m.name == name &&
                    java.lang.reflect.Modifier.isStatic(m.modifiers) &&
                    m.parameterCount == 1 &&
                    paramType.isAssignableFrom(m.parameterTypes[0])
            }
        } catch (t: Throwable) {
            if (isMohistUnsafeReflection(t)) null else throw t
        }
    }

    /** eventListeners 在 PlayerPatch（protected），非 ServerPlayerPatch 公有字段 */
    private fun getEventListenersField(): java.lang.reflect.Field? {
        if (!isEpicFightAvailable) return null
        val playerPatchClass = try {
            Class.forName(
                "yesman.epicfight.world.capabilities.entitypatch.player.PlayerPatch",
                false,
                epicClassLoader()
            )
        } catch (_: Exception) {
            return null
        }
        return findFieldOnClassHierarchy(playerPatchClass, "eventListeners")
    }

    private fun findFieldOnClassHierarchy(start: Class<*>, name: String): java.lang.reflect.Field? {
        var c: Class<*>? = start
        while (c != null && c != Any::class.java) {
            try {
                val f = c.getDeclaredField(name)
                f.isAccessible = true
                return f
            } catch (_: NoSuchFieldException) {
            }
            c = c.superclass
        }
        return null
    }

    // 获取 animator 字段（仅类型提示，实际读取走 findFieldValue）
    @Suppress("UNUSED")
    private fun getAnimatorField(): java.lang.reflect.Field? = null

    /** EF 1.20.1: yesman.epicfight.api.animation.types.EntityState#inaction() */
    private fun getInActionMethod(): java.lang.reflect.Method? {
        if (!classExists("yesman.epicfight.api.animation.types.EntityState")) return null
        return try {
            val entityStateClass = Class.forName(
                "yesman.epicfight.api.animation.types.EntityState",
                false,
                epicClassLoader()
            )
            findDeclaredNoArgMethod(entityStateClass, "inaction")
        } catch (_: Exception) {
            null
        }
    }

    /** EF 1.20.1: PlayerEventListener.EventType 为静态字段，非 enum */
    private fun getEventTypeField(fieldName: String): Any? {
        if (!classExists("yesman.epicfight.world.entity.eventlistener.PlayerEventListener")) return null
        return try {
            val outer = Class.forName(
                "yesman.epicfight.world.entity.eventlistener.PlayerEventListener",
                false,
                epicClassLoader()
            )
            val eventTypeClass = Class.forName(
                "yesman.epicfight.world.entity.eventlistener.PlayerEventListener\$EventType",
                false,
                epicClassLoader()
            )
            try {
                val f = eventTypeClass.getDeclaredField(fieldName)
                f.isAccessible = true
                f.get(null)
            } catch (_: Exception) {
                val f = outer.getDeclaredField(fieldName)
                f.isAccessible = true
                f.get(null)
            }
        } catch (e: Exception) {
            efDebug("getEventTypeField $fieldName: ${e.message}")
            null
        }
    }

    private fun getEventTypeAnimateBegin(): Any? = getEventTypeField("ANIMATION_BEGIN_EVENT")

    private fun getEventTypeAnimateEnd(): Any? = getEventTypeField("ANIMATION_END_EVENT")

    /** EF 1.20.1: addEventListener(EventType, UUID, Consumer, int) */
    private fun resolveAddListenerMethod(eventListeners: Any, sampleEventType: Any?): java.lang.reflect.Method? {
        val managerClass = eventListeners.javaClass
        if (sampleEventType != null) {
            for (m in managerClass.declaredMethods) {
                if (m.name != "addEventListener" || m.parameterCount !in 3..4) continue
                if (!m.parameterTypes[0].isInstance(sampleEventType)) continue
                return m
            }
        }
        return managerClass.declaredMethods.firstOrNull { m ->
            m.name == "addEventListener" && m.parameterCount in 3..4
        }
    }

    private fun invokeAddEventListener(
        method: java.lang.reflect.Method,
        eventListeners: Any,
        eventType: Any,
        player: Player,
        onRun: Runnable,
    ): Boolean {
        method.isAccessible = true
        val consumer = java.util.function.Consumer<Any> { onRun.run() }
        return try {
            when (method.parameterCount) {
                3 -> {
                    method.invoke(eventListeners, eventType, SYMPHONY_EVENT_UUID, consumer)
                    true
                }
                4 -> {
                    method.invoke(eventListeners, eventType, SYMPHONY_EVENT_UUID, consumer, 0)
                    true
                }
                else -> false
            }
        } catch (e: Exception) {
            efLog("invokeAddEventListener 失败 ${playerTag(player)}: ${e.message}")
            false
        }
    }

    /**
     * 初始化监听器（插件启用 / reload 时调用）。
     * @param configEnabled 配置项 compatibility.epicfight-enabled
     */
    fun init(configEnabled: Boolean) {
        enabled = configEnabled && isEpicFightAvailable
        if (!enabled) {
            BlinkLog.info(
                "§7[Symphony/EF]§f 兼容未启用 config=$configEnabled efClasses=$isEpicFightAvailable"
            )
            animationState.clear()
            pendingRecalc.clear()
            pendingAfterRecalc.clear()
            postWorldGraceUntil.clear()
            inactionSinceMs.clear()
            return
        }

        efLog("兼容已启用 available=$isEpicFightAvailable 在线玩家=${Bukkit.getOnlinePlayers().size}")
        for (player in Bukkit.getOnlinePlayers()) {
            registerListener(player)
        }
    }

    /**
     * 为单个玩家注册 Epic Fight 事件监听
     */
    fun registerListener(player: Player) {
        if (!isEpicFightAvailable) {
            efDebug("registerListener 跳过 ${playerTag(player)}: EF 类不可用")
            return
        }

        val handle = getHandle(player)
        if (handle == null) {
            efLog("registerListener 失败 ${playerTag(player)}: 无法获取 NMS handle")
            return
        }
            val patch = getServerPlayerPatch(handle)
            if (patch == null) {
                efLog(
                    "registerListener 失败 ${playerTag(player)}: getServerPlayerPatch=null " +
                        "nmsClass=${handle.javaClass.name}"
                )
                return
            }
            efLog("registerListener patch=${patch.javaClass.name} ${playerTag(player)}")

        val uuid = player.uniqueId

        // 获取反射方法
        val eventListeners = getEventListeners(patch)
        if (eventListeners == null) {
            efLog(
                "registerListener 失败 ${playerTag(player)}: eventListeners=null " +
                    "patchClass=${patch.javaClass.name} fields=${listPatchFieldNames(patch)}"
            )
            return
        }

        val beginEventType = getEventTypeAnimateBegin()
        val endEventType = getEventTypeAnimateEnd()
        val addListenerMethod = resolveAddListenerMethod(eventListeners, beginEventType ?: endEventType)

        var beginOk = false
        var endOk = false

        if (beginEventType != null && addListenerMethod != null) {
            beginOk = invokeAddEventListener(
                addListenerMethod,
                eventListeners,
                beginEventType,
                player,
                Runnable { onAnimationBegin(player) },
            )
        } else {
            efLog(
                "ANIMATION_BEGIN 未注册 ${playerTag(player)} beginType=$beginEventType " +
                    "addMethod=${addListenerMethod?.name} mgr=${eventListeners.javaClass.name}"
            )
        }

        if (endEventType != null && addListenerMethod != null) {
            endOk = invokeAddEventListener(
                addListenerMethod,
                eventListeners,
                endEventType,
                player,
                Runnable { onAnimationEnd(player) },
            )
        } else {
            efLog(
                "ANIMATION_END 未注册 ${playerTag(player)} endType=$endEventType " +
                    "addMethod=${addListenerMethod?.name}"
            )
        }

        efLog("registerListener ${playerTag(player)} begin=$beginOk end=$endOk | ${snapshot(player)}")
    }

    // ========== 反射辅助方法 ==========

    /**
     * 从 CraftBukkit/Paper/Mohist 等实现取出 NMS ServerPlayer。
     * 仅读 `handle` 字段在 Paper 1.20.5+ / 部分混合端会失败。
     */
    private fun getHandle(player: Player): Any? {
        val strategies = mutableListOf<String>()
        fun ok(value: Any?, via: String): Any? {
            if (value != null) {
                strategies.add(via)
                efDebug("getHandle OK via=$via nmsClass=${value.javaClass.name}")
                return value
            }
            return null
        }

        // 1) Asteroid（Symphony 自带 NMS 桥，若存在）
        try {
            val apiClass = Class.forName("priv.seventeen.artist.asteroid.AsteroidAPI", false, epicClassLoader())
            for (methodName in listOf("getNmsPlayer", "getServerPlayer", "toNms", "getHandle")) {
                val m = apiClass.methods.firstOrNull { it.name == methodName && it.parameterCount == 1 }
                if (m != null) {
                    val r = m.invoke(null, player)
                    ok(r, "AsteroidAPI.$methodName")?.let { return it }
                }
            }
        } catch (_: ClassNotFoundException) {
        } catch (e: Exception) {
            strategies.add("AsteroidAPI err=${e.message}")
        }

        var clazz: Class<*>? = player.javaClass
        while (clazz != null && clazz != Any::class.java) {
            for (fieldName in listOf("handle", "entity")) {
                try {
                    val f = clazz.getDeclaredField(fieldName)
                    f.isAccessible = true
                    val v = f.get(player)
                    ok(v, "${clazz.simpleName}.$fieldName")?.let { return it }
                } catch (_: Exception) {
                }
            }
            clazz = clazz.superclass
        }

        clazz = player.javaClass
        while (clazz != null && clazz != Any::class.java) {
            for (methodName in listOf("getHandle", "getEntity", "getNMSPlayer")) {
                try {
                    val m = clazz.getDeclaredMethod(methodName)
                    m.isAccessible = true
                    val v = m.invoke(player)
                    ok(v, "${clazz.simpleName}.$methodName()")?.let { return it }
                } catch (_: Exception) {
                }
            }
            clazz = clazz.superclass
        }

        if (!loggedCraftPlayerClass) {
            loggedCraftPlayerClass = true
            efLog(
                "getHandle 全部失败 craftClass=${player.javaClass.name} " +
                    "super=${player.javaClass.superclass?.name} tried=${strategies.joinToString()}"
            )
        }
        return null
    }

    private fun getServerPlayerPatch(handle: Any): Any? {
        if (!isEpicFightAvailable) return null
        return try {
            val capClass = Class.forName(
                "yesman.epicfight.world.capabilities.EpicFightCapabilities",
                false,
                epicClassLoader()
            )
            val tried = mutableListOf<String>()
            val handleClass = handle.javaClass
            for (paramType in nmsPlayerParamTypes(handle)) {
                try {
                    val m = findStaticDeclaredMethod(capClass, "getServerPlayerPatch", paramType)
                        ?: findStaticDeclaredMethod(capClass, "getServerPlayerPatch", handleClass)
                    if (m == null) {
                        tried.add("${paramType.simpleName}:noMethod")
                        continue
                    }
                    m.isAccessible = true
                    val patch = m.invoke(null, handle)
                    if (patch != null) {
                        efDebug("getServerPlayerPatch OK param=${m.parameterTypes[0].name}")
                        return patch
                    }
                } catch (e: Throwable) {
                    if (isMohistUnsafeReflection(e)) {
                        tried.add("${paramType.simpleName}:unsafe")
                        continue
                    }
                    tried.add("${paramType.simpleName}:${e.javaClass.simpleName}")
                }
            }
            try {
                for (m in capClass.declaredMethods) {
                    if (m.name != "getServerPlayerPatch" || m.parameterCount != 1) continue
                    if (!java.lang.reflect.Modifier.isStatic(m.modifiers)) continue
                    try {
                        m.isAccessible = true
                        val patch = m.invoke(null, handle)
                        if (patch != null) {
                            efDebug("getServerPlayerPatch OK declared param=${m.parameterTypes[0].name}")
                            return patch
                        }
                    } catch (e: Throwable) {
                        if (!isMohistUnsafeReflection(e)) {
                            tried.add("decl:${e.message}")
                        }
                    }
                }
            } catch (t: Throwable) {
                if (!isMohistUnsafeReflection(t)) {
                    tried.add("scan:${t.message}")
                }
            }
            efLog("getServerPlayerPatch=null nms=${handle.javaClass.name} tried=$tried")
            null
        } catch (e: Throwable) {
            if (!isMohistUnsafeReflection(e)) {
                efLog("getServerPlayerPatch 异常: ${e.message}")
            }
            null
        }
    }

    private fun nmsPlayerParamTypes(handle: Any): List<Class<*>> {
        val out = LinkedHashSet<Class<*>>()
        var c: Class<*>? = handle.javaClass
        while (c != null && c != Any::class.java) {
            out.add(c)
            for (iface in c.interfaces) {
                out.add(iface)
            }
            c = c.superclass
        }
        for (name in listOf(
            "net.minecraft.world.entity.player.Player",
            "net.minecraft.server.level.ServerPlayer",
            "net.minecraft.entity.player.EntityPlayer",
            "net.minecraft.entity.player.EntityPlayerMP",
        )) {
            try {
                out.add(Class.forName(name, false, epicClassLoader()))
            } catch (_: Exception) {
            }
        }
        return out.toList()
    }

    private fun listPatchFieldNames(patch: Any, limit: Int = 24): String {
        val names = mutableListOf<String>()
        var c: Class<*>? = patch.javaClass
        while (c != null && c != Any::class.java && names.size < limit) {
            try {
                for (f in c.declaredFields) {
                    if (names.size >= limit) break
                    names.add("${c.simpleName}.${f.name}")
                }
            } catch (_: Throwable) {
            }
            c = c.superclass
        }
        return names.joinToString()
    }

    private fun getFieldValueByNames(holder: Any, vararg names: String): Any? {
        for (name in names) {
            findFieldValue(holder, name)?.let { return it }
        }
        return null
    }

    private fun invokeNoArgGetter(holder: Any, vararg methodNames: String): Any? {
        for (name in methodNames) {
            try {
                val m = findDeclaredNoArgMethod(holder.javaClass, name)
                    ?: continue
                m.isAccessible = true
                val v = m.invoke(holder)
                if (v != null) return v
            } catch (_: Throwable) {
            }
        }
        var c: Class<*>? = holder.javaClass.superclass
        while (c != null && c != Any::class.java) {
            for (name in methodNames) {
                try {
                    val m = findDeclaredNoArgMethod(c, name) ?: continue
                    m.isAccessible = true
                    val v = m.invoke(holder)
                    if (v != null) return v
                } catch (_: Throwable) {
                }
            }
            c = c.superclass
        }
        return null
    }

    private fun getEventListeners(patch: Any): Any? {
        try {
            val playerPatchClass = Class.forName(
                "yesman.epicfight.world.capabilities.entitypatch.player.PlayerPatch",
                false,
                epicClassLoader()
            )
            findFieldOnClassHierarchy(playerPatchClass, "eventListeners")?.get(patch)?.let { return it }
        } catch (_: Exception) {
        }
        return try {
            getEventListenersField()?.get(patch)
        } catch (_: Exception) {
            null
        }
    }

    private fun findAnimatorOnPatch(patch: Any): Any? {
        invokeNoArgGetter(patch, "getAnimator", "getServerAnimator")?.let { return it }

        val livingPatch = try {
            Class.forName(
                "yesman.epicfight.world.capabilities.entitypatch.LivingEntityPatch",
                false,
                epicClassLoader()
            )
        } catch (_: Exception) {
            null
        }
        if (livingPatch != null) {
            findFieldOnClassHierarchy(livingPatch, "animator")?.let { f ->
                try {
                    f.get(patch)?.let { return it }
                } catch (_: Exception) {
                }
            }
        }

        getFieldValueByNames(
            patch,
            "animator",
            "playerAnimator",
            "entityAnimator",
            "animationManager",
        )?.let { return it }

        var c: Class<*>? = patch.javaClass
        while (c != null && c != Any::class.java) {
            try {
                for (f in c.declaredFields) {
                    val tn = f.type.name.lowercase()
                    if ("animator" in tn || "armature" in tn ||
                        ("animation" in tn && "event" !in tn)
                    ) {
                        f.isAccessible = true
                        val v = f.get(patch)
                        if (v != null) return v
                    }
                }
            } catch (_: Throwable) {
            }
            c = c.superclass
        }
        return null
    }

    private fun getAnimator(patch: Any): Any? = findAnimatorOnPatch(patch)

    private fun isInAction(animator: Any): Boolean {
        return try {
            val entityState = getFieldValueByNames(animator, "entityState", "state") ?: return false
            val inActionMethod = getInActionMethod() ?: return false
            val result = inActionMethod.invoke(entityState)
            result as? Boolean ?: false
        } catch (e: Exception) {
            false
        }
    }

    // ========== 事件处理 ==========

    private fun onAnimationBegin(player: Player) {
        val uuid = player.uniqueId
        animationState.put(uuid, true)
        efDebug("ANIMATION_BEGIN ${playerTag(player)} | ${snapshot(player)}")
    }

    private fun onAnimationEnd(player: Player) {
        val uuid = player.uniqueId
        animationState.put(uuid, false)
        efDebug("ANIMATION_END ${playerTag(player)} | ${snapshot(player)}")
        flushPendingRecalc(player)
    }

    private fun flushPendingRecalc(player: Player) {
        val uuid = player.uniqueId
        if (!pendingRecalc.remove(uuid)) return
        val after = pendingAfterRecalc.remove(uuid)
        Bukkit.getScheduler().runTaskLater(bukkitPlugin, Runnable {
            if (!player.isOnline) return@Runnable
            if (isAnimating(player) || isInInactionState(player)) {
                pendingRecalc.add(uuid)
                if (after != null) pendingAfterRecalc.put(uuid, after)
                return@Runnable
            }
            AttributeCalculator.recalculate(player)
            after?.invoke()
        }, 1L)
    }

    /**
     * 检查玩家是否正在播放动画
     */
    fun isAnimating(player: Player): Boolean {
        return animationState.getOrDefault(player.uniqueId, false)
    }

    fun hasPendingRecalc(player: Player): Boolean =
        player.uniqueId in pendingRecalc

    /** 是否应推迟重算（兼容开启且处于动画/inaction） */
    fun shouldDeferRecalc(player: Player): Boolean {
        if (!enabled) return false
        if (inPostWorldGrace(player.uniqueId)) return false
        return isAnimating(player) || isInInactionState(player)
    }

    private fun inPostWorldGrace(uuid: UUID): Boolean =
        System.currentTimeMillis() < (postWorldGraceUntil[uuid] ?: 0L)

    /**
     * 检查玩家是否正在播放需要延迟重算的动画
     * （inaction 状态的动画，如攻击动画）
     */
    fun isInInactionState(player: Player): Boolean {
        if (!isEpicFightAvailable) return false

        return try {
            val handle = getHandle(player) ?: return false
            val patch = getServerPlayerPatch(handle) ?: return false
            val getState = findDeclaredNoArgMethod(patch.javaClass, "getEntityState")
                ?: findDeclaredNoArgMethod(
                    Class.forName(
                        "yesman.epicfight.world.capabilities.entitypatch.LivingEntityPatch",
                        false,
                        epicClassLoader()
                    ),
                    "getEntityState"
                )
            if (getState != null) {
                getState.isAccessible = true
                val state = getState.invoke(patch) ?: return false
                val inaction = getInActionMethod()?.invoke(state) as? Boolean ?: false
                return inaction
            }
            val animator = getAnimator(patch) ?: return false
            isInAction(animator)
        } catch (e: Throwable) {
            if (!isMohistUnsafeReflection(e)) {
                efDebug("isInInactionState 异常 ${playerTag(player)}: ${e.message}")
            }
            false
        }
    }

    /**
     * 标记玩家需要延迟属性重算
     * 当玩家在播放动画时调用此方法，重算会被延迟到动画结束。
     * 关闭兼容时回落到立即 markDirty（与无 Epic Fight 行为一致）。
     */
    fun markPendingRecalc(player: Player) {
        if (!enabled) {
            AttributeCalculator.markDirty(player)
            return
        }
        AttributeCalculator.markDirty(player)
        if (!inPostWorldGrace(player.uniqueId) &&
            (isAnimating(player) || isInInactionState(player))
        ) {
            pendingRecalc.add(player.uniqueId)
        } else {
            AttributeCalculator.recalculate(player)
        }
    }

    /**
     * 在 dirty 时安全重算（供 RuntimeTickTask 等路径使用）。
     * 动画播放中仅排队，避免 [VanillaAttributeBridge] 在 inaction 期间打断 Epic Fight。
     */
    fun scheduleSafeRecalc(player: Player, onComplete: (() -> Unit)? = null) {
        if (!player.isOnline) return
        if (!AttributeCache.isDirty(player.uniqueId) && player.uniqueId !in pendingRecalc) {
            return
        }
        if (!enabled) {
            if (AttributeCache.isDirty(player.uniqueId)) {
                AttributeCalculator.recalculate(player)
            }
            onComplete?.invoke()
            return
        }
        if (!inPostWorldGrace(player.uniqueId) &&
            (isAnimating(player) || isInInactionState(player))
        ) {
            pendingRecalc.add(player.uniqueId)
            if (onComplete != null) {
                pendingAfterRecalc[player.uniqueId] = onComplete
            }
            efLog("scheduleSafeRecalc 推迟 ${playerTag(player)} | ${snapshot(player)}")
            return
        }
        if (AttributeCache.isDirty(player.uniqueId)) {
            AttributeCalculator.recalculate(player)
        }
        efDebug("scheduleSafeRecalc 已执行 ${playerTag(player)} | ${snapshot(player)}")
        onComplete?.invoke()
    }

    /**
     * 玩家切换世界后调用：传送可能打断动画且不会触发 ANIMATION_END，
     * 需清除本地追踪、强制解除 EF inaction，再延迟属性同步。
     */
    fun onWorldChange(player: Player) {
        val now = System.currentTimeMillis()
        val uuid = player.uniqueId
        val prevMs = worldChangeLastMs[uuid] ?: 0L
        if (now - prevMs < 250L) {
            efDebug("换世界 onWorldChange 去重 Δ=${now - prevMs}ms ${playerTag(player)}")
            return
        }
        worldChangeLastMs[uuid] = now

        efLog("换世界 onWorldChange ${playerTag(player)} | 前状态 ${snapshot(player)}")
        animationState.put(uuid, false)
        pendingRecalc.remove(uuid)
        pendingAfterRecalc.remove(uuid)
        inactionSinceMs.remove(uuid)
        postWorldGraceUntil[uuid] = System.currentTimeMillis() + POST_WORLD_GRACE_MS

        if (enabled && isEpicFightAvailable) {
            val cleared = forceClearEpicFightStuckState(player, "onWorldChange-immediate")
            efLog("换世界 立即 forceClear ${playerTag(player)} ok=$cleared | ${snapshot(player)}")
            Bukkit.getScheduler().runTaskLater(bukkitPlugin, Runnable {
                if (player.isOnline) {
                    registerListener(player)
                    val c2 = forceClearEpicFightStuckState(player, "onWorldChange+1t")
                    efLog("换世界 +1tick ${playerTag(player)} forceClear=$c2 | ${snapshot(player)}")
                }
            }, 1L)
        } else {
            efLog("换世界 跳过 EF 处理 ${playerTag(player)} enabled=$enabled available=$isEpicFightAvailable")
        }

        Bukkit.getScheduler().runTaskLater(bukkitPlugin, Runnable {
            if (!player.isOnline) return@Runnable
            if (enabled && isEpicFightAvailable) {
                val c3 = forceClearEpicFightStuckState(player, "onWorldChange+5t")
                efLog("换世界 +5tick ${playerTag(player)} forceClear=$c3 | ${snapshot(player)}")
            }
            val dirty = AttributeCache.isDirty(uuid)
            val after = {
                if (inPostWorldGrace(uuid)) {
                    efLog("换世界 跳过 syncToVanilla（宽限期内） ${playerTag(player)}")
                } else {
                    efLog("换世界 同步原版属性 ${playerTag(player)} | ${snapshot(player)}")
                    VanillaAttributeBridge.syncToVanilla(player)
                }
            }
            if (dirty) {
                efLog("换世界 scheduleSafeRecalc ${playerTag(player)} dirty=true")
                scheduleSafeRecalc(player, after)
            } else {
                efLog("换世界 直接 after dirty=false ${playerTag(player)}")
                after()
            }
        }, 5L)
    }

    /**
     * 每 tick 由 [RuntimeTickTask] 调用：换世界宽限期结束后仍 inaction 则强制解除。
     */
    fun tickPlayer(player: Player) {
        if (!enabled || !player.isOnline) return
        val uuid = player.uniqueId
        val inaction = isInInactionState(player)
        if (!inaction) {
            inactionSinceMs.remove(uuid)
            return
        }
        val now = System.currentTimeMillis()
        val since = inactionSinceMs.getOrPut(uuid) { now }
        val stuck = now - since >= STUCK_INACTION_MS
        val graceUntil = postWorldGraceUntil[uuid] ?: 0L
        val justAfterWorldGrace = graceUntil > 0 && now >= graceUntil && now - graceUntil < 3_000L
        if (stuck || justAfterWorldGrace) {
            val reason = if (stuck) "stuck>${STUCK_INACTION_MS}ms" else "afterWorldGrace"
            efLog("tick 兜底解除 $reason ${playerTag(player)} inaction持续=${now - since}ms | ${snapshot(player)}")
            animationState.put(uuid, false)
            forceClearEpicFightStuckState(player, "tick-$reason")
            inactionSinceMs.remove(uuid)
            if (AttributeCache.isDirty(uuid) || uuid in pendingRecalc) {
                scheduleSafeRecalc(player) {
                    VanillaAttributeBridge.syncToVanilla(player)
                }
            }
        }
    }

    /** 安全同步原版属性（动画中则排队） */
    fun syncVanillaWhenSafe(player: Player) {
        if (!player.isOnline) return
        if (!enabled) {
            VanillaAttributeBridge.syncToVanilla(player)
            return
        }
        if (shouldDeferRecalc(player)) {
            efDebug("syncVanilla 推迟 ${playerTag(player)} | ${snapshot(player)}")
            pendingRecalc.add(player.uniqueId)
            pendingAfterRecalc[player.uniqueId] = {
                VanillaAttributeBridge.syncToVanilla(player)
            }
        } else {
            efDebug("syncVanilla 立即 ${playerTag(player)} | ${snapshot(player)}")
            VanillaAttributeBridge.syncToVanilla(player)
        }
    }

    /**
     * 反射尝试解除 Epic Fight 卡住的 inaction / 动画状态（换世界、长时间 inaction 时调用）。
     */
    fun forceClearEpicFightStuckState(player: Player, phase: String = "manual"): Boolean {
        if (!isEpicFightAvailable) {
            efDebug("forceClear 跳过 ${playerTag(player)} phase=$phase: EF 不可用")
            return false
        }
        animationState.put(player.uniqueId, false)
        val invoked = mutableListOf<String>()
        var inactionBefore = false
        var inactionAfter = false
        try {
            inactionBefore = isInInactionState(player)
            val handle = getHandle(player)
            if (handle == null) {
                efLog("forceClear 失败 phase=$phase ${playerTag(player)}: handle=null")
                return false
            }
            val patch = getServerPlayerPatch(handle)
            if (patch == null) {
                efLog("forceClear 失败 phase=$phase ${playerTag(player)}: patch=null")
                return false
            }

            if (invokeNoArgOn(patch, "resetHolding")) invoked += "patch.resetHolding"
            if (invokeNoArgOn(patch, "updateEntityState")) invoked += "patch.updateEntityState"
            if (invokeSetSoftPauseOnAnimator(patch, false)) invoked += "anim.setSoftPause(false)"
            if (invokeSetHardPauseOnAnimator(patch, false)) invoked += "anim.setHardPause(false)"
            if (invokeTerminateAnimationPlayer(patch)) invoked += "anim.terminate"

            val animator = findAnimatorOnPatch(patch)
            if (animator != null) {
                invoked += "anim=${animator.javaClass.simpleName}"
            } else {
                invoked += "anim=null"
            }

            inactionAfter = isInInactionState(player)
            efLog(
                "forceClear phase=$phase ${playerTag(player)} " +
                    "inaction $inactionBefore->$inactionAfter invoked=[${invoked.joinToString()}]"
            )
            return !inactionAfter || invoked.isNotEmpty()
        } catch (e: Exception) {
            BlinkLog.warn("Epic Fight 解除卡住状态失败 ${player.name} phase=$phase: ${e.message}")
            return false
        }
    }

    private fun invokeSetSoftPauseOnAnimator(patch: Any, paused: Boolean): Boolean {
        val animator = findAnimatorOnPatch(patch) ?: return false
        return invokeBooleanArgOn(animator, "setSoftPause", paused)
    }

    private fun invokeSetHardPauseOnAnimator(patch: Any, paused: Boolean): Boolean {
        val animator = findAnimatorOnPatch(patch) ?: return false
        return invokeBooleanArgOn(animator, "setHardPause", paused)
    }

    private fun invokeTerminateAnimationPlayer(patch: Any): Boolean {
        val animator = findAnimatorOnPatch(patch) ?: return false
        return try {
            val serverAnimClass = Class.forName(
                "yesman.epicfight.api.animation.ServerAnimator",
                false,
                epicClassLoader()
            )
            val playerField = findFieldOnClassHierarchy(serverAnimClass, "animationPlayer") ?: return false
            val animPlayer = playerField.get(animator) ?: return false
            val livingPatchClass = Class.forName(
                "yesman.epicfight.world.capabilities.entitypatch.LivingEntityPatch",
                false,
                epicClassLoader()
            )
            val terminate = findDeclaredMethod(animPlayer.javaClass, "terminate", livingPatchClass) ?: return false
            terminate.isAccessible = true
            terminate.invoke(animPlayer, patch)
            true
        } catch (_: Throwable) {
            false
        }
    }

    private fun invokeBooleanArgOn(target: Any, methodName: String, value: Boolean): Boolean {
        return try {
            val m = findDeclaredMethod(target.javaClass, methodName, java.lang.Boolean.TYPE)
                ?: findDeclaredMethod(target.javaClass, methodName, java.lang.Boolean::class.java)
                ?: return false
            m.isAccessible = true
            m.invoke(target, value)
            true
        } catch (_: Throwable) {
            false
        }
    }

    private fun invokePatchMethodsByKeyword(patch: Any, invoked: MutableList<String>, vararg keywords: String) {
        var c: Class<*>? = patch.javaClass
        while (c != null && c != Any::class.java) {
            try {
                for (m in c.declaredMethods) {
                    if (m.parameterCount != 0) continue
                    val n = m.name.lowercase()
                    if (keywords.none { n.contains(it) }) continue
                    if (invoked.any { it.endsWith(m.name) }) continue
                    try {
                        m.isAccessible = true
                        m.invoke(patch)
                        invoked += "patch.${m.name}"
                    } catch (_: Throwable) {
                    }
                }
            } catch (t: Throwable) {
                if (!isMohistUnsafeReflection(t)) break
            }
            c = c.superclass
        }
    }

    private fun invokeNoArgOn(target: Any, methodName: String): Boolean {
        return try {
            val m = findDeclaredNoArgMethod(target.javaClass, methodName)
                ?: run {
                    var c: Class<*>? = target.javaClass.superclass
                    var found: java.lang.reflect.Method? = null
                    while (c != null && c != Any::class.java) {
                        found = findDeclaredNoArgMethod(c, methodName)
                        if (found != null) break
                        c = c.superclass
                    }
                    found
                } ?: return false
            m.isAccessible = true
            m.invoke(target)
            true
        } catch (_: Throwable) {
            false
        }
    }

    private fun resetEntityStateFlags(holder: Any): Boolean {
        var changed = false
        try {
            val state = findFieldValue(holder, "entityState") ?: return false
            val stateClass = state.javaClass
            for (field in stateClass.declaredFields) {
                if (field.type != Boolean::class.javaPrimitiveType && field.type != java.lang.Boolean::class.java) continue
                val n = field.name.lowercase()
                if (n.contains("action") || n.contains("inaction") || n.contains("lock") || n == "attacking") {
                    field.isAccessible = true
                    field.setBoolean(state, false)
                    changed = true
                }
            }
            val inactionMethod = getInActionMethod()
            if (inactionMethod != null && inactionMethod.parameterCount == 1) {
                try {
                    inactionMethod.invoke(state, 0)
                    changed = true
                } catch (_: Exception) {
                }
            }
        } catch (_: Exception) {
        }
        return changed
    }

    private fun trySetEntityStateIdle(patch: Any): Boolean {
        if (!classExists("yesman.epicfight.world.capabilities.entitypatch.EntityState")) return false
        return try {
            val stateClass = Class.forName(
                "yesman.epicfight.world.capabilities.entitypatch.EntityState",
                false,
                EpicFightAnimationListener::class.java.classLoader
            )
            val idle = stateClass.enumConstants?.firstOrNull {
                it.toString().equals("IDLE", ignoreCase = true) ||
                    it.toString().contains("IDLE", ignoreCase = true)
            } ?: return false
            for (fieldName in listOf("entityState", "state")) {
                val field = patch.javaClass.declaredFields.firstOrNull { it.name == fieldName } ?: continue
                field.isAccessible = true
                field.set(patch, idle)
                val animator = getAnimator(patch)
                if (animator != null) {
                    val af = animator.javaClass.declaredFields.firstOrNull { it.name == fieldName }
                    if (af != null) {
                        af.isAccessible = true
                        af.set(animator, idle)
                    }
                }
                return true
            }
            false
        } catch (_: Exception) {
            false
        }
    }

    private fun findFieldValue(obj: Any, name: String): Any? {
        var clazz: Class<*>? = obj.javaClass
        while (clazz != null) {
            try {
                val f = clazz.getDeclaredField(name)
                f.isAccessible = true
                return f.get(obj)
            } catch (_: Exception) {
                clazz = clazz.superclass
            }
        }
        return null
    }

    /**
     * 强制触发属性重算（不考虑动画状态）
     * 用于某些必须立即生效的操作
     */
    fun forceRecalculate(player: Player) {
        pendingRecalc.remove(player.uniqueId)
        AttributeCalculator.recalculate(player)
    }

    /**
     * 清理玩家状态（玩家离线时调用）
     */
    fun cleanup(player: Player) {
        val uuid = player.uniqueId
        animationState.remove(uuid)
        pendingRecalc.remove(uuid)
        pendingAfterRecalc.remove(uuid)
        postWorldGraceUntil.remove(uuid)
        inactionSinceMs.remove(uuid)
    }

    /**
     * 获取当前正在播放动画的玩家数量
     */
    fun getAnimatingPlayerCount(): Int {
        return animationState.count { it.value }
    }

    /**
     * 清理已离线的玩家动画状态
     * 在 RuntimeTickTask 中定期调用
     */
    fun cleanupOfflinePlayers() {
        val onlineUUIDs = Bukkit.getOnlinePlayers().map { it.uniqueId }.toSet()
        val toRemove = animationState.keys.filter { it !in onlineUUIDs }
        toRemove.forEach { uuid ->
            animationState.remove(uuid)
            pendingRecalc.remove(uuid)
            pendingAfterRecalc.remove(uuid)
            postWorldGraceUntil.remove(uuid)
            inactionSinceMs.remove(uuid)
        }
    }

    private const val ANIMATION_LISTENER_UUID = "symphony-animation-listener"
    private const val POST_WORLD_GRACE_MS = 4_000L
    private const val STUCK_INACTION_MS = 6_000L
}
