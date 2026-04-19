package priv.seventeen.artist.symphony.core.attribute

import org.bukkit.Bukkit
import org.bukkit.entity.LivingEntity
import org.bukkit.plugin.Plugin
import priv.seventeen.artist.blink.BlinkLog
import priv.seventeen.artist.symphony.api.attribute.AttributeModifier
import java.util.UUID
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.Executors
import java.util.concurrent.ThreadFactory
import java.util.concurrent.atomic.AtomicInteger

/**
 * 异步属性重算调度器。
 *
 * 流程：
 *   1. 主线程：跑所有 `isAsync = false` 的 Provider，得到第一批 modifiers
 *      （这些 Provider 会读 Bukkit equipment / location / inventory）
 *   2. 异步池：跑 `isAsync = true` 的 Provider（仅访问 PlayerDataManager 等纯数据）
 *   3. 主线程：合并两批 modifiers → 调 [AttributeCalculator.applyModifiers]
 *      做后续计算（包含 @derive / @formula / 事件广播）
 *
 * 同一实体同时间只调度一次（由 [inFlight] 集合保证）。
 *
 * 由 [SymphonyConfig.asyncRecalcEnabled] 控制是否启用；关闭时调用 [recalculateAsync] 直接走同步路径。
 */
object AsyncRecalcScheduler {

    private val executor = Executors.newFixedThreadPool(2, NamedFactory("Symphony-Async-Recalc"))
    private val inFlight = ConcurrentHashMap.newKeySet<UUID>()
    @Volatile private var plugin: Plugin? = null
    @Volatile private var enabled = false

    fun init(plugin: Plugin, enabled: Boolean) {
        this.plugin = plugin
        this.enabled = enabled
    }

    fun setEnabled(value: Boolean) { enabled = value }

    fun shutdown() {
        executor.shutdownNow()
        inFlight.clear()
    }

    /**
     * 异步触发重算；非启用 / 已在飞行中时回退同步。
     * @return true = 已派发到异步通路；false = 已同步执行
     */
    fun recalculateAsync(entity: LivingEntity): Boolean {
        val plg = plugin
        if (!enabled || plg == null) {
            AttributeCalculator.recalculate(entity)
            return false
        }
        if (!inFlight.add(entity.uniqueId)) return false  // 已在排队

        // 主线程：跑同步 Provider + 抢拍快照
        val syncModifiers = collectSync(entity)

        executor.submit {
            try {
                val asyncModifiers = collectAsync(entity)
                Bukkit.getScheduler().runTask(plg, Runnable {
                    try {
                        AttributeCalculator.applyModifiers(entity, syncModifiers + asyncModifiers)
                    } finally {
                        inFlight.remove(entity.uniqueId)
                    }
                })
            } catch (e: Exception) {
                BlinkLog.warn("异步 recalc 失败 ${entity.uniqueId}: ${e.message}")
                inFlight.remove(entity.uniqueId)
            }
        }
        return true
    }

    private fun collectSync(entity: LivingEntity): List<AttributeModifier> {
        val out = mutableListOf<AttributeModifier>()
        for (p in AttributeProviderRegistry.getAll()) {
            if (p.isAsync) continue
            if (!p.appliesTo(entity)) continue
            try { out += p.provide(entity) } catch (e: Exception) {
                BlinkLog.warn("Provider '${p.id}' (sync) 异常: ${e.message}")
            }
        }
        return out
    }

    private fun collectAsync(entity: LivingEntity): List<AttributeModifier> {
        val out = mutableListOf<AttributeModifier>()
        for (p in AttributeProviderRegistry.getAll()) {
            if (!p.isAsync) continue
            if (!p.appliesTo(entity)) continue
            try { out += p.provide(entity) } catch (e: Exception) {
                BlinkLog.warn("Provider '${p.id}' (async) 异常: ${e.message}")
            }
        }
        return out
    }

    private class NamedFactory(private val baseName: String) : ThreadFactory {
        private val counter = AtomicInteger(0)
        override fun newThread(r: Runnable): Thread =
            Thread(r, "$baseName-${counter.incrementAndGet()}").apply { isDaemon = true }
    }
}
