/*
 * Copyright 2026 17Artist
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package priv.seventeen.artist.symphony.integrations.epicfight

import org.bukkit.Bukkit
import org.bukkit.entity.Player
import org.bukkit.plugin.Plugin
import org.bukkit.scheduler.BukkitTask
import priv.seventeen.artist.symphony.engine.attribute.AttributeCommitBarrier
import priv.seventeen.artist.symphony.engine.attribute.LatestAttributeCommitQueue
import priv.seventeen.artist.symphony.engine.config.EpicFightCompatibilitySettings
import priv.seventeen.artist.symphony.engine.config.LanguageBundle
import java.lang.reflect.Field
import java.lang.reflect.Method
import java.lang.reflect.Modifier
import java.util.UUID
import java.util.concurrent.ConcurrentHashMap
import java.util.function.Consumer
import java.util.logging.Level

/**
 * Epic Fight 服务端模组兼容层。
 *
 * Epic Fight 通过其模组类进行检测。
 * 玩家处于 Epic Fight 的 `inaction` 动画时，Symphony 仍可提交逻辑快照，但 Bukkit 属性写入
 * 会被合并，并在动画允许安全修改后统一应用，避免打断模组动画状态。
 */
class EpicFightIntegration(
    private val plugin: Plugin,
    private val settings: EpicFightCompatibilitySettings,
    private val language: () -> LanguageBundle,
) : AttributeCommitBarrier, AutoCloseable {
    private val queue = LatestAttributeCommitQueue()
    private val animationState = ConcurrentHashMap<UUID, Boolean>()
    private val postWorldGraceUntil = ConcurrentHashMap<UUID, Long>()
    private val inactionSince = ConcurrentHashMap<UUID, Long>()
    private val nonInactionPolls = ConcurrentHashMap<UUID, Int>()
    private val worldChangeLastMillis = ConcurrentHashMap<UUID, Long>()
    private val flushScheduled = ConcurrentHashMap.newKeySet<UUID>()

    private var capabilityClass: Class<*>? = null
    private var playerPatchClass: Class<*>? = null
    private var entityStateClass: Class<*>? = null
    private var task: BukkitTask? = null
    private var elapsedTicks = 0L

    @Volatile
    private var active = false

    val available: Boolean
        get() = capabilityClass != null && playerPatchClass != null && entityStateClass != null

    fun start(): Boolean {
        if (!settings.enabled) {
            plugin.logger.info(text("console.epic-fight.disabled"))
            return false
        }
        if (task != null) return active

        capabilityClass = loadClass(CAPABILITIES)
        playerPatchClass = loadClass(SERVER_PLAYER_PATCH)
        entityStateClass = loadClass(ENTITY_STATE)
        if (!available) {
            clearReflection()
            plugin.logger.info(text("console.epic-fight.unavailable"))
            return false
        }

        active = true
        Bukkit.getOnlinePlayers().forEach(::registerListener)
        task = Bukkit.getScheduler().runTaskTimer(
            plugin,
            Runnable(::poll),
            settings.fallbackPollTicks,
            settings.fallbackPollTicks
        )
        plugin.logger.info(text("console.epic-fight.enabled"))
        return true
    }

    override fun submit(entityId: UUID, revision: Long, action: () -> Unit) {
        val player = Bukkit.getPlayer(entityId)
        if (!active || player == null || !player.isOnline) {
            queue.forget(entityId)
            action()
            return
        }
        if (shouldDefer(player)) {
            queue.defer(entityId, revision, action)
            return
        }

        val queuedRevision = queue.revision(entityId)
        if (queuedRevision != null && queuedRevision > revision) {
            scheduleFlush(player)
            return
        }
        queue.forget(entityId)
        action()
    }

    fun onJoin(player: Player) {
        if (!active) return
        Bukkit.getScheduler().runTaskLater(plugin, Runnable {
            if (active && player.isOnline) registerListener(player)
        }, 5L)
    }

    fun onQuit(player: Player) {
        unregisterListener(player)
        forget(player.uniqueId)
    }

    fun onWorldChange(player: Player) {
        if (!active) return
        val entityId = player.uniqueId
        val now = System.currentTimeMillis()
        val previous = worldChangeLastMillis.put(entityId, now) ?: 0L
        if (now - previous < WORLD_CHANGE_DEDUPLICATION_MILLIS) return

        animationState[entityId] = false
        nonInactionPolls.remove(entityId)
        inactionSince.remove(entityId)
        postWorldGraceUntil[entityId] = now + settings.postWorldGraceMillis
        forceClearStuckState(player)

        Bukkit.getScheduler().runTaskLater(plugin, Runnable {
            if (!active || !player.isOnline) return@Runnable
            registerListener(player)
            forceClearStuckState(player)
        }, 1L)
        Bukkit.getScheduler().runTaskLater(plugin, Runnable {
            if (!active || !player.isOnline) return@Runnable
            forceClearStuckState(player)
            flushWhenSafe(player)
        }, 5L)
    }

    fun isDeferring(player: Player): Boolean = active && shouldDefer(player)

    override fun close() {
        active = false
        task?.cancel()
        task = null
        Bukkit.getOnlinePlayers().forEach(::unregisterListener)
        queue.clear()
        animationState.clear()
        postWorldGraceUntil.clear()
        inactionSince.clear()
        nonInactionPolls.clear()
        worldChangeLastMillis.clear()
        flushScheduled.clear()
        clearReflection()
    }

    private fun poll() {
        if (!active) return
        elapsedTicks += settings.fallbackPollTicks

        queue.entityIds().forEach { entityId ->
            val player = Bukkit.getPlayer(entityId)
            if (player == null || !player.isOnline) forget(entityId) else pollPending(player)
        }

        if (elapsedTicks % STUCK_CHECK_TICKS < settings.fallbackPollTicks) {
            Bukkit.getOnlinePlayers().forEach(::checkStuckState)
            val online = Bukkit.getOnlinePlayers().mapTo(hashSetOf()) { it.uniqueId }
            animationState.keys.filterNot(online::contains).forEach(::forget)
        }
    }

    private fun pollPending(player: Player) {
        val entityId = player.uniqueId
        val inaction = isInaction(player)
        if (inaction) {
            nonInactionPolls.remove(entityId)
            return
        }
        val safePolls = nonInactionPolls.merge(entityId, 1, Int::plus) ?: 1
        if (safePolls < SAFE_FALLBACK_POLLS) return

        // 不受支持的模组小版本结构可能导致 ANIMATION_END 丢失。连续两次安全轮询作为
        // 回退边界，可防止单刻误判造成属性过早写入。
        animationState[entityId] = false
        nonInactionPolls.remove(entityId)
        scheduleFlush(player)
    }

    private fun checkStuckState(player: Player) {
        val entityId = player.uniqueId
        if (!isInaction(player)) {
            inactionSince.remove(entityId)
            return
        }
        val now = System.currentTimeMillis()
        val since = inactionSince.putIfAbsent(entityId, now) ?: inactionSince.getValue(entityId)
        val graceUntil = postWorldGraceUntil[entityId] ?: 0L
        val graceJustEnded = graceUntil > 0L && now >= graceUntil && now - graceUntil < 3_000L
        if (now - since < settings.stuckInactionMillis && !graceJustEnded) return

        animationState[entityId] = false
        forceClearStuckState(player)
        inactionSince.remove(entityId)
        scheduleFlush(player)
    }

    private fun shouldDefer(player: Player): Boolean {
        if (System.currentTimeMillis() < (postWorldGraceUntil[player.uniqueId] ?: 0L)) return false
        return animationState[player.uniqueId] == true || isInaction(player)
    }

    private fun scheduleFlush(player: Player) {
        val entityId = player.uniqueId
        if (!flushScheduled.add(entityId)) return
        Bukkit.getScheduler().runTaskLater(plugin, Runnable {
            flushScheduled.remove(entityId)
            if (active && player.isOnline) flushWhenSafe(player) else forget(entityId)
        }, 1L)
    }

    private fun flushWhenSafe(player: Player) {
        if (shouldDefer(player)) return
        runCatching { queue.flush(player.uniqueId) }
            .onFailure {
                plugin.logger.log(Level.SEVERE, text("console.epic-fight.flush-failed", "player" to player.name), it)
            }
    }

    private fun text(key: String, vararg variables: Pair<String, Any?>): String =
        language().text(key, *variables)

    private fun onAnimationBegin(player: Player) {
        runOnPrimary {
            if (active && player.isOnline) {
                animationState[player.uniqueId] = true
                nonInactionPolls.remove(player.uniqueId)
            }
        }
    }

    private fun onAnimationEnd(player: Player) {
        runOnPrimary {
            if (active && player.isOnline) {
                animationState[player.uniqueId] = false
                nonInactionPolls.remove(player.uniqueId)
                scheduleFlush(player)
            }
        }
    }

    private fun registerListener(player: Player) {
        if (!active) return
        val eventListeners = eventListenerManager(player) ?: return
        val beginType = eventType("ANIMATION_BEGIN_EVENT")
        val endType = eventType("ANIMATION_END_EVENT")
        val method = safeDeclaredMethods(eventListeners.javaClass).firstOrNull { candidate ->
            candidate.name == "addEventListener" && candidate.parameterCount in 3..4 &&
                (beginType == null || candidate.parameterTypes[0].isInstance(beginType))
        } ?: return

        if (beginType != null) addEventListener(method, eventListeners, beginType) { onAnimationBegin(player) }
        if (endType != null) addEventListener(method, eventListeners, endType) { onAnimationEnd(player) }
    }

    private fun unregisterListener(player: Player) {
        val eventListeners = eventListenerManager(player) ?: return
        val methods = safeDeclaredMethods(eventListeners.javaClass).filter { candidate ->
            candidate.name == "removeListener" && candidate.parameterCount in 2..3
        }
        listOfNotNull(eventType("ANIMATION_BEGIN_EVENT"), eventType("ANIMATION_END_EVENT")).forEach { eventType ->
            methods.forEach { method ->
                runCatching {
                    method.isAccessible = true
                    when (method.parameterCount) {
                        2 -> method.invoke(eventListeners, eventType, SYMPHONY_EVENT_UUID)
                        3 -> {
                            method.invoke(eventListeners, eventType, SYMPHONY_EVENT_UUID, -1)
                            method.invoke(eventListeners, eventType, SYMPHONY_EVENT_UUID, 0)
                        }
                        else -> Unit
                    }
                }
            }
        }
    }

    private fun eventListenerManager(player: Player): Any? {
        val patch = serverPlayerPatch(player) ?: return null
        return findFieldOnHierarchy(
            loadClass(PLAYER_PATCH) ?: patch.javaClass,
            "eventListeners"
        )?.getSafely(patch)
    }

    private fun addEventListener(method: Method, manager: Any, eventType: Any, callback: () -> Unit): Boolean =
        runCatching {
            method.isAccessible = true
            val consumer = Consumer<Any> { callback() }
            when (method.parameterCount) {
                3 -> method.invoke(manager, eventType, SYMPHONY_EVENT_UUID, consumer)
                4 -> method.invoke(manager, eventType, SYMPHONY_EVENT_UUID, consumer, 0)
                else -> return false
            }
            true
        }.getOrDefault(false)

    private fun eventType(name: String): Any? {
        val nested = loadClass(PLAYER_EVENT_LISTENER_TYPE)
        val outer = loadClass(PLAYER_EVENT_LISTENER)
        return findStaticField(nested, name)?.getSafely(null) ?: findStaticField(outer, name)?.getSafely(null)
    }

    private fun isInaction(player: Player): Boolean = runCatching {
        val patch = serverPlayerPatch(player) ?: return false
        val state = findNoArgOnHierarchy(patch.javaClass, "getEntityState")?.invoke(patch)
            ?: findFieldOnHierarchy(patch.javaClass, "entityState")?.getSafely(patch)
            ?: findAnimatorState(patch)
            ?: return false
        val method = findNoArgOnHierarchy(state.javaClass, "inaction")
            ?: findNoArgOnHierarchy(entityStateClass ?: return false, "inaction")
            ?: return false
        method.invoke(state) as? Boolean ?: false
    }.getOrDefault(false)

    private fun serverPlayerPatch(player: Player): Any? {
        val handle = getHandle(player) ?: return null
        val capabilities = capabilityClass ?: return null
        // 混合服务端上的 `declaredMethods` 可能解析到无关的客户端专用签名。
        // 优先解析已知的单参数重载，避免某个不可用客户端类使服务端玩家路径整体失效；
        // 旧版 Symphony 也曾针对同类故障进行防护。
        val exact = nmsPlayerParameterTypes(handle).firstNotNullOfOrNull { parameter ->
            runCatching { capabilities.getDeclaredMethod("getServerPlayerPatch", parameter) }.getOrNull()
        }
        val method = exact ?: safeDeclaredMethods(capabilities).firstOrNull { candidate ->
            candidate.name == "getServerPlayerPatch" && Modifier.isStatic(candidate.modifiers) &&
                candidate.parameterCount == 1 && candidate.parameterTypes[0].isInstance(handle)
        } ?: safeDeclaredMethods(capabilities).firstOrNull { candidate ->
            candidate.name == "getServerPlayerPatch" && Modifier.isStatic(candidate.modifiers) &&
                candidate.parameterCount == 1 &&
                (candidate.parameterTypes[0].isAssignableFrom(handle.javaClass) ||
                    handle.javaClass.isAssignableFrom(candidate.parameterTypes[0]))
        } ?: return null
        return runCatching {
            method.isAccessible = true
            method.invoke(null, handle)
        }.getOrNull()
    }

    private fun nmsPlayerParameterTypes(handle: Any): List<Class<*>> {
        val types = linkedSetOf<Class<*>>()
        var current: Class<*>? = handle.javaClass
        while (current != null && current != Any::class.java) {
            types += current
            types += current.interfaces
            current = current.superclass
        }
        listOf(
            "net.minecraft.server.level.ServerPlayer",
            "net.minecraft.world.entity.player.Player",
            "net.minecraft.entity.player.EntityPlayerMP",
            "net.minecraft.entity.player.EntityPlayer"
        ).mapNotNullTo(types, ::loadClass)
        return types.toList()
    }

    private fun getHandle(player: Player): Any? {
        findNoArgOnHierarchy(player.javaClass, "getHandle")?.let { method ->
            runCatching { method.invoke(player) }.getOrNull()?.let { return it }
        }
        for (fieldName in listOf("handle", "entity")) {
            findFieldOnHierarchy(player.javaClass, fieldName)?.getSafely(player)?.let { return it }
        }

        val asteroid = loadClass("priv.seventeen.artist.asteroid.AsteroidAPI") ?: return null
        val receiver = runCatching { asteroid.getField("INSTANCE").get(null) }.getOrNull()
        for (name in listOf("getNmsPlayer", "getServerPlayer", "toNms", "getHandle")) {
            val method = safeMethods(asteroid).firstOrNull { it.name == name && it.parameterCount == 1 } ?: continue
            runCatching {
                method.invoke(if (Modifier.isStatic(method.modifiers)) null else receiver, player)
            }.getOrNull()?.let { return it }
        }
        return null
    }

    private fun findAnimatorState(patch: Any): Any? {
        val animator = findNoArgOnHierarchy(patch.javaClass, "getAnimator")?.let {
            runCatching { it.invoke(patch) }.getOrNull()
        } ?: findNoArgOnHierarchy(patch.javaClass, "getServerAnimator")?.let {
            runCatching { it.invoke(patch) }.getOrNull()
        } ?: findFieldByTypeName(patch, "animator")
        return animator?.let {
            findFieldOnHierarchy(it.javaClass, "entityState")?.getSafely(it)
                ?: findFieldOnHierarchy(it.javaClass, "state")?.getSafely(it)
        }
    }

    /** 旧版的跨世界与卡死状态恢复逻辑，仅在检测到模组运行环境时启用。 */
    private fun forceClearStuckState(player: Player): Boolean {
        val patch = serverPlayerPatch(player) ?: return false
        var invoked = false
        invoked = invokeNoArg(patch, "resetHolding") || invoked
        invoked = invokeNoArg(patch, "updateEntityState") || invoked
        val animator = findNoArgOnHierarchy(patch.javaClass, "getAnimator")?.let {
            runCatching { it.invoke(patch) }.getOrNull()
        } ?: findNoArgOnHierarchy(patch.javaClass, "getServerAnimator")?.let {
            runCatching { it.invoke(patch) }.getOrNull()
        } ?: findFieldByTypeName(patch, "animator")
        if (animator != null) {
            invoked = invokeBoolean(animator, "setSoftPause", false) || invoked
            invoked = invokeBoolean(animator, "setHardPause", false) || invoked
            invoked = terminateAnimation(animator, patch) || invoked
        }
        return invoked
    }

    private fun terminateAnimation(animator: Any, patch: Any): Boolean = runCatching {
        val animationPlayer = findFieldOnHierarchy(animator.javaClass, "animationPlayer")?.getSafely(animator)
            ?: return false
        val method = safeDeclaredMethods(animationPlayer.javaClass).firstOrNull {
            it.name == "terminate" && it.parameterCount == 1 && it.parameterTypes[0].isInstance(patch)
        } ?: return false
        method.isAccessible = true
        method.invoke(animationPlayer, patch)
        true
    }.getOrDefault(false)

    private fun invokeNoArg(target: Any, name: String): Boolean = runCatching {
        val method = findNoArgOnHierarchy(target.javaClass, name) ?: return false
        method.invoke(target)
        true
    }.getOrDefault(false)

    private fun invokeBoolean(target: Any, name: String, value: Boolean): Boolean = runCatching {
        val method = findMethodOnHierarchy(target.javaClass, name, java.lang.Boolean.TYPE)
            ?: findMethodOnHierarchy(target.javaClass, name, Boolean::class.java)
            ?: return false
        method.invoke(target, value)
        true
    }.getOrDefault(false)

    private fun findFieldByTypeName(target: Any, token: String): Any? {
        var type: Class<*>? = target.javaClass
        while (type != null && type != Any::class.java) {
            safeDeclaredFields(type).firstOrNull { token in it.type.name.lowercase() }?.let { field ->
                field.isAccessible = true
                field.getSafely(target)?.let { return it }
            }
            type = type.superclass
        }
        return null
    }

    private fun findNoArgOnHierarchy(start: Class<*>, name: String): Method? =
        findMethodOnHierarchy(start, name)

    private fun findMethodOnHierarchy(start: Class<*>, name: String, vararg parameters: Class<*>): Method? {
        var type: Class<*>? = start
        while (type != null && type != Any::class.java) {
            val current = type
            runCatching { current.getDeclaredMethod(name, *parameters) }.getOrNull()?.let { method ->
                method.isAccessible = true
                return method
            }
            type = type.superclass
        }
        return null
    }

    private fun findFieldOnHierarchy(start: Class<*>, name: String): Field? {
        var type: Class<*>? = start
        while (type != null && type != Any::class.java) {
            val current = type
            runCatching { current.getDeclaredField(name) }.getOrNull()?.let { field ->
                field.isAccessible = true
                return field
            }
            type = type.superclass
        }
        return null
    }

    private fun findStaticField(type: Class<*>?, name: String): Field? = type?.let {
        runCatching { it.getDeclaredField(name) }.getOrNull()?.also { field -> field.isAccessible = true }
    }

    private fun Field.getSafely(receiver: Any?): Any? = runCatching { get(receiver) }.getOrNull()

    private fun loadClass(name: String): Class<*>? {
        val loaders = listOfNotNull(
            plugin.javaClass.classLoader,
            Thread.currentThread().contextClassLoader,
            EpicFightIntegration::class.java.classLoader,
            ClassLoader.getSystemClassLoader()
        ).distinct()
        for (loader in loaders) {
            try {
                return Class.forName(name, false, loader)
            } catch (_: ClassNotFoundException) {
            } catch (_: LinkageError) {
            }
        }
        return null
    }

    private fun safeDeclaredMethods(type: Class<*>): Array<Method> = try {
        type.declaredMethods
    } catch (_: LinkageError) {
        emptyArray()
    }

    private fun safeMethods(type: Class<*>): Array<Method> = try {
        type.methods
    } catch (_: LinkageError) {
        emptyArray()
    }

    private fun safeDeclaredFields(type: Class<*>): Array<Field> = try {
        type.declaredFields
    } catch (_: LinkageError) {
        emptyArray()
    }

    private fun runOnPrimary(action: () -> Unit) {
        if (Bukkit.isPrimaryThread()) action() else Bukkit.getScheduler().runTask(plugin, Runnable(action))
    }

    private fun forget(entityId: UUID) {
        queue.forget(entityId)
        animationState.remove(entityId)
        postWorldGraceUntil.remove(entityId)
        inactionSince.remove(entityId)
        nonInactionPolls.remove(entityId)
        worldChangeLastMillis.remove(entityId)
        flushScheduled.remove(entityId)
    }

    private fun clearReflection() {
        capabilityClass = null
        playerPatchClass = null
        entityStateClass = null
    }

    companion object {
        private val SYMPHONY_EVENT_UUID: UUID = UUID.fromString("a1b2c3d4-e5f6-4789-a012-3456789abcde")
        private const val CAPABILITIES = "yesman.epicfight.world.capabilities.EpicFightCapabilities"
        private const val SERVER_PLAYER_PATCH =
            "yesman.epicfight.world.capabilities.entitypatch.player.ServerPlayerPatch"
        private const val PLAYER_PATCH = "yesman.epicfight.world.capabilities.entitypatch.player.PlayerPatch"
        private const val ENTITY_STATE = "yesman.epicfight.api.animation.types.EntityState"
        private const val PLAYER_EVENT_LISTENER = "yesman.epicfight.world.entity.eventlistener.PlayerEventListener"
        private const val PLAYER_EVENT_LISTENER_TYPE =
            "yesman.epicfight.world.entity.eventlistener.PlayerEventListener\$EventType"
        private const val WORLD_CHANGE_DEDUPLICATION_MILLIS = 250L
        private const val STUCK_CHECK_TICKS = 20L
        private const val SAFE_FALLBACK_POLLS = 2
    }
}
