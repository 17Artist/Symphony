package priv.seventeen.artist.symphony.plugin.listener

import org.bukkit.Bukkit
import org.bukkit.entity.Player
import priv.seventeen.artist.blink.BlinkLog
import priv.seventeen.artist.blink.bukkitPlugin
import priv.seventeen.artist.symphony.core.attribute.AttributeCalculator
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

    // 追踪玩家是否正在播放动画（inaction 状态）
    private val animationState = ConcurrentHashMap<UUID, Boolean>()

    // 待重算的玩家队列（当动画结束时触发）
    private val pendingRecalc = ConcurrentHashMap.newKeySet<UUID>()

    // 是否启用：配置开启 && Epic Fight 类存在
    @Volatile
    private var enabled = false

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

    // 获取 getServerPlayerPatch 方法
    private fun getGetServerPlayerPatchMethod(): java.lang.reflect.Method? {
        if (!isEpicFightAvailable) return null
        return try {
            val clazz = Class.forName("yesman.epicfight.world.capabilities.EpicFightCapabilities", false,
                EpicFightAnimationListener::class.java.classLoader)
            clazz.getMethod("getServerPlayerPatch", Class.forName("net.minecraft.world.entity.player.Player", false,
                EpicFightAnimationListener::class.java.classLoader))
        } catch (e: Exception) {
            null
        }
    }

    // 获取 eventListeners 字段
    private fun getEventListenersField(): java.lang.reflect.Field? {
        if (!isEpicFightAvailable) return null
        val clazz = try {
            Class.forName("yesman.epicfight.world.capabilities.entitypatch.player.ServerPlayerPatch", false,
                EpicFightAnimationListener::class.java.classLoader)
        } catch (e: Exception) {
            return null
        }
        return try {
            clazz.getField("eventListeners")
        } catch (e: Exception) {
            try {
                clazz.getDeclaredField("eventListeners")
            } catch (e2: Exception) {
                null
            }
        }
    }

    // 获取 animator 字段
    private fun getAnimatorField(): java.lang.reflect.Field? {
        if (!isEpicFightAvailable) return null
        val clazz = try {
            Class.forName("yesman.epicfight.world.capabilities.entitypatch.player.ServerPlayerPatch", false,
                EpicFightAnimationListener::class.java.classLoader)
        } catch (e: Exception) {
            return null
        }
        return try {
            clazz.getField("animator")
        } catch (e: Exception) {
            try {
                clazz.getDeclaredField("animator")
            } catch (e2: Exception) {
                null
            }
        }
    }

    // 获取 entityState 的 inaction 方法
    private fun getInActionMethod(): java.lang.reflect.Method? {
        if (!classExists("yesman.epicfight.world.capabilities.entitypatch.EntityState")) return null
        return try {
            val entityStateClass = Class.forName("yesman.epicfight.world.capabilities.entitypatch.EntityState", false,
                EpicFightAnimationListener::class.java.classLoader)
            try {
                entityStateClass.getMethod("inaction")
            } catch (e: Exception) {
                try {
                    entityStateClass.getMethod("inaction", Int::class.javaPrimitiveType)
                } catch (e2: Exception) {
                    null
                }
            }
        } catch (e: Exception) {
            null
        }
    }

    // EventType 枚举 - ANIMATION_BEGIN
    private fun getEventTypeAnimateBegin(): Any? {
        if (!classExists("yesman.epicfight.world.entity.eventlistener.PlayerEventListener")) return null
        return try {
            val clazz = Class.forName("yesman.epicfight.world.entity.eventlistener.PlayerEventListener", false,
                EpicFightAnimationListener::class.java.classLoader)
            clazz.enumConstants?.filterIsInstance<Any>()?.find {
                it.toString().contains("ANIMATION_BEGIN", ignoreCase = true)
            }
        } catch (e: Exception) {
            null
        }
    }

    // EventType 枚举 - ANIMATION_END
    private fun getEventTypeAnimateEnd(): Any? {
        if (!classExists("yesman.epicfight.world.entity.eventlistener.PlayerEventListener")) return null
        return try {
            val clazz = Class.forName("yesman.epicfight.world.entity.eventlistener.PlayerEventListener", false,
                EpicFightAnimationListener::class.java.classLoader)
            clazz.enumConstants?.filterIsInstance<Any>()?.find {
                it.toString().contains("ANIMATION_END", ignoreCase = true)
            }
        } catch (e: Exception) {
            null
        }
    }

    // addEventListener 方法
    private fun getAddEventListenerMethod(): java.lang.reflect.Method? {
        val field = getEventListenersField() ?: return null
        if (!classExists("yesman.epicfight.world.entity.eventlistener.PlayerEventListener")) return null

        return try {
            val listenerClass = Class.forName("yesman.epicfight.world.entity.eventlistener.PlayerEventListener", false,
                EpicFightAnimationListener::class.java.classLoader)
            try {
                field.type.getMethod("addEventListener",
                    listenerClass,
                    String::class.java,
                    Runnable::class.java,
                    Int::class.javaPrimitiveType
                )
            } catch (e: Exception) {
                try {
                    field.type.getMethod("addEventListener",
                        listenerClass,
                        String::class.java,
                        Runnable::class.java
                    )
                } catch (e2: Exception) {
                    null
                }
            }
        } catch (e: Exception) {
            null
        }
    }

    /**
     * 初始化监听器（插件启用 / reload 时调用）。
     * @param configEnabled 配置项 compatibility.epicfight-enabled
     */
    fun init(configEnabled: Boolean) {
        enabled = configEnabled && isEpicFightAvailable
        if (!enabled) {
            animationState.clear()
            pendingRecalc.clear()
            return
        }

        for (player in Bukkit.getOnlinePlayers()) {
            registerListener(player)
        }
    }

    /**
     * 为单个玩家注册 Epic Fight 事件监听
     */
    fun registerListener(player: Player) {
        if (!isEpicFightAvailable) return

        val handle = getHandle(player) ?: return
        val patch = getServerPlayerPatch(handle) ?: return

        val uuid = player.uniqueId

        // 获取反射方法
        val beginEventType = getEventTypeAnimateBegin()
        val endEventType = getEventTypeAnimateEnd()
        val addListenerMethod = getAddEventListenerMethod()
        val eventListeners = getEventListeners(patch) ?: return

        // 监听动画开始事件
        if (beginEventType != null && addListenerMethod != null) {
            try {
                addListenerMethod.invoke(
                    eventListeners,
                    beginEventType,
                    ANIMATION_LISTENER_UUID,
                    Runnable { onAnimationBegin(player) },
                    0
                )
            } catch (e: Exception) {
                // 忽略注册失败
            }
        }

        // 监听动画结束事件
        if (endEventType != null && addListenerMethod != null) {
            try {
                addListenerMethod.invoke(
                    eventListeners,
                    endEventType,
                    ANIMATION_LISTENER_UUID,
                    Runnable { onAnimationEnd(player) },
                    0
                )
            } catch (e: Exception) {
                // 忽略注册失败
            }
        }
    }

    // ========== 反射辅助方法 ==========

    private fun getHandle(player: Player): Any? {
        return try {
            val craftPlayerClass = player.javaClass
            val handleField = craftPlayerClass.getDeclaredField("handle")
            handleField.isAccessible = true
            handleField.get(player)
        } catch (e: Exception) {
            null
        }
    }

    private fun getServerPlayerPatch(handle: Any): Any? {
        return try {
            getGetServerPlayerPatchMethod()?.invoke(null, handle)
        } catch (e: Exception) {
            null
        }
    }

    private fun getEventListeners(patch: Any): Any? {
        return try {
            getEventListenersField()?.get(patch)
        } catch (e: Exception) {
            null
        }
    }

    private fun getAnimator(patch: Any): Any? {
        return try {
            getAnimatorField()?.get(patch)
        } catch (e: Exception) {
            null
        }
    }

    private fun isInAction(animator: Any): Boolean {
        return try {
            val animatorClass = animator.javaClass
            val entityStateField = animatorClass.getDeclaredField("entityState")
            entityStateField.isAccessible = true
            val entityState = entityStateField.get(animator) ?: return false

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
    }

    private fun onAnimationEnd(player: Player) {
        val uuid = player.uniqueId
        animationState.put(uuid, false)

        // 触发待重算
        if (pendingRecalc.remove(uuid)) {
            Bukkit.getScheduler().runTaskLater(bukkitPlugin, Runnable {
                if (!isAnimating(player)) {
                    AttributeCalculator.recalculate(player)
                }
            }, 1L)
        }
    }

    /**
     * 检查玩家是否正在播放动画
     */
    fun isAnimating(player: Player): Boolean {
        return animationState.getOrDefault(player.uniqueId, false)
    }

    /**
     * 检查玩家是否正在播放需要延迟重算的动画
     * （inaction 状态的动画，如攻击动画）
     */
    fun isInInactionState(player: Player): Boolean {
        if (!isEpicFightAvailable) return false

        return try {
            val handle = getHandle(player) ?: return false
            val patch = getServerPlayerPatch(handle) ?: return false
            val animator = getAnimator(patch) ?: return false
            isInAction(animator)
        } catch (e: Exception) {
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
        if (isAnimating(player) || isInInactionState(player)) {
            pendingRecalc.add(player.uniqueId)
        } else {
            AttributeCalculator.recalculate(player)
        }
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
        }
    }

    private const val ANIMATION_LISTENER_UUID = "symphony-animation-listener"
}
