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

package priv.seventeen.artist.symphony.bukkit.persistence

import com.zaxxer.hikari.HikariConfig
import com.zaxxer.hikari.HikariDataSource
import org.bukkit.Bukkit
import org.bukkit.NamespacedKey
import org.bukkit.entity.Player
import org.bukkit.persistence.PersistentDataType
import org.bukkit.plugin.Plugin
import org.bukkit.scheduler.BukkitTask
import priv.seventeen.artist.blink.BlinkLog
import priv.seventeen.artist.symphony.api.level.LevelSnapshot
import priv.seventeen.artist.symphony.bukkit.compat.BukkitAttributeTypes
import priv.seventeen.artist.symphony.engine.config.HealthPersistenceSettings
import priv.seventeen.artist.symphony.engine.config.HealthStorageMode
import java.util.UUID
import java.util.concurrent.CompletableFuture
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicInteger

class HealthPersistenceRuntime private constructor(
    private val plugin: Plugin,
    private val settings: HealthPersistenceSettings,
    private val levelReader: (Player) -> LevelSnapshot?,
    private val restoreDelayTicks: Long,
    private val database: MysqlDatabase?
) : AutoCloseable {
    private val sessions = linkedMapOf<UUID, Session>()
    private val pendingWrites = ConcurrentHashMap.newKeySet<CompletableFuture<*>>()
    private val executor = database?.let {
        Executors.newFixedThreadPool(minOf(it.settings.poolSize, 4)) { runnable ->
            Thread(runnable, "Symphony-Health-${THREAD_COUNTER.incrementAndGet()}").apply { isDaemon = true }
        }
    }
    private var checkpointTask: BukkitTask? = null
    private var closed = false

    fun start() {
        check(Bukkit.isPrimaryThread()) { "生命值持久化必须在 Bukkit 主线程启动" }
        if (settings.checkpointSeconds <= 0L || checkpointTask != null) return
        val period = settings.checkpointSeconds * 20L
        checkpointTask = Bukkit.getScheduler().runTaskTimer(plugin, Runnable(::checkpoint), period, period)
    }

    fun onJoin(player: Player) {
        check(Bukkit.isPrimaryThread()) { "玩家生命值加载必须在 Bukkit 主线程发起" }
        if (closed || sessions.containsKey(player.uniqueId)) return
        begin(player, levelReader(player))
    }

    fun onQuit(player: Player) {
        check(Bukkit.isPrimaryThread()) { "玩家生命值保存必须在 Bukkit 主线程发起" }
        finish(player)
    }

    fun onDeath(player: Player) {
        check(Bukkit.isPrimaryThread()) { "玩家死亡状态处理必须在 Bukkit 主线程执行" }
        sessions[player.uniqueId]?.let { session ->
            session.restoreTask?.cancel()
            session.restoreTask = null
            session.restoring = false
            val maximum = player.getAttribute(BukkitAttributeTypes.maxHealth)?.value ?: return@let
            val death = StoredHealth(0.0, maximum.coerceAtLeast(1.0))
            session.terminalHealth = death
            when (settings.storage) {
                HealthStorageMode.PDC -> writePdc(player, session.token.key.characterKey, death)
                HealthStorageMode.MYSQL -> if (!session.failed && session.inFlight == null) {
                    checkpointMysql(player.uniqueId, session, death)
                }
            }
        }
    }

    fun onRespawn(player: Player) {
        check(Bukkit.isPrimaryThread()) { "玩家重生状态处理必须在 Bukkit 主线程执行" }
        Bukkit.getScheduler().runTask(plugin, Runnable {
            val session = sessions[player.uniqueId] ?: return@Runnable
            session.terminalHealth = null
            checkpoint()
        })
    }

    fun onCharacterChanged(player: Player, current: LevelSnapshot?) {
        check(Bukkit.isPrimaryThread()) { "玩家角色切换必须在 Bukkit 主线程处理" }
        val session = sessions[player.uniqueId] ?: return
        val nextCharacterKey = HealthCharacterKey.from(current)
        if (session.token.key.characterKey == nextCharacterKey) return
        finish(player)
        Bukkit.getScheduler().runTask(plugin, Runnable {
            if (!closed && player.isOnline) begin(player, current)
        })
    }

    fun isRestorePending(player: Player): Boolean = sessions[player.uniqueId]?.restoring == true

    private fun begin(player: Player, level: LevelSnapshot?) {
        val initial = capture(player) ?: return
        val token = HealthSessionToken(
            HealthStateKey(settings.clusterId, player.uniqueId, HealthCharacterKey.from(level)),
            UUID.randomUUID()
        )
        val claim = when (settings.storage) {
            HealthStorageMode.PDC -> CompletableFuture.completedFuture(readPdc(player, token.key.characterKey))
            HealthStorageMode.MYSQL -> CompletableFuture.supplyAsync(
                { requireNotNull(database).repository.claim(token, initial) },
                requireNotNull(executor)
            )
        }
        val session = Session(token, player.health, initial, claim)
        sessions[player.uniqueId] = session
        session.restoreTask = Bukkit.getScheduler().runTaskLater(plugin, Runnable {
            val current = sessions[player.uniqueId]
            if (current !== session) return@Runnable
            session.settled = true
            tryRestore(player, session)
        }, restoreDelayTicks)
        claim.whenComplete { stored, error ->
            runOnMainThread {
                val current = sessions[player.uniqueId]
                if (current !== session) return@runOnMainThread
                session.claimCompleted = true
                if (error != null) {
                    session.failed = true
                    session.restoring = false
                    BlinkLog.error(
                        text("console.health-persistence-load-failed", "player" to player.uniqueId),
                        unwrap(error)
                    )
                    return@runOnMainThread
                }
                session.loaded = stored
                tryRestore(player, session)
            }
        }
    }

    private fun tryRestore(player: Player, session: Session) {
        if (!session.claimCompleted || !session.settled || !session.restoring) return
        session.restoreTask = null
        val stored = session.loaded
        if (stored != null && player.isOnline && !player.isDead) {
            runCatching {
                val current = capture(player) ?: return@runCatching
                if (HealthRestorePolicy.mayRestore(session.joinHealth, current.health, stored)) {
                    player.health = HealthRestorePolicy.restoredHealth(stored, current.maximumHealth)
                }
            }.onFailure { error ->
                BlinkLog.error(
                    text("console.health-persistence-restore-failed", "player" to player.uniqueId),
                    error
                )
            }
        }
        session.persisted = stored ?: session.initial
        session.restoring = false
    }

    private fun checkpoint() {
        if (closed) return
        sessions.entries.toList().forEach { (playerId, session) ->
            if (session.restoring || session.failed || session.inFlight != null) return@forEach
            val player = Bukkit.getPlayer(playerId) ?: return@forEach
            val current = capture(player) ?: return@forEach
            val persisted = session.persisted
            if (persisted != null && !HealthRestorePolicy.hasChanged(persisted, current)) return@forEach
            when (settings.storage) {
                HealthStorageMode.PDC -> {
                    writePdc(player, session.token.key.characterKey, current)
                    session.persisted = current
                }
                HealthStorageMode.MYSQL -> checkpointMysql(playerId, session, current)
            }
        }
    }

    private fun checkpointMysql(playerId: UUID, session: Session, current: StoredHealth) {
        session.inFlight = current
        val future = session.claim.handleAsync({ _, claimError ->
            if (claimError != null) throw unwrap(claimError)
            requireNotNull(database).repository.checkpoint(session.token, current)
        }, requireNotNull(executor))
        track(future)
        future.whenComplete { written, error ->
            runOnMainThread {
                val active = sessions[playerId]
                if (active !== session) return@runOnMainThread
                session.inFlight = null
                if (error != null) {
                    BlinkLog.error(
                        text("console.health-persistence-checkpoint-failed", "player" to playerId),
                        unwrap(error)
                    )
                } else if (written == true) {
                    session.persisted = current
                }
            }
        }
    }

    private fun finish(player: Player) {
        val session = sessions.remove(player.uniqueId) ?: return
        session.restoreTask?.cancel()
        session.restoreTask = null
        val finalHealth = finalHealth(player, session)
        when (settings.storage) {
            HealthStorageMode.PDC -> finalHealth?.let {
                writePdc(player, session.token.key.characterKey, it)
            }
            HealthStorageMode.MYSQL -> scheduleRelease(session, finalHealth)
        }
    }

    /**
     * 玩家刚加入就退出或服务器立即关闭时，原版可能已经把生命值按尚未同步完成的最大值截断。
     * 若加载期间没有发生真实生命值变化，只结束本次会话并保留旧记录；死亡或实际变化仍按当前值保存。
     */
    private fun finalHealth(player: Player, session: Session): StoredHealth? {
        session.terminalHealth?.let { return it }
        val current = capture(player) ?: return null
        if (
            session.restoring &&
            !HealthRestorePolicy.healthChangedSinceJoin(session.joinHealth, current.health)
        ) return null
        return current
    }

    private fun scheduleRelease(session: Session, health: StoredHealth?): CompletableFuture<Boolean> {
        val future = session.claim.handleAsync({ _, claimError ->
            if (claimError != null) throw unwrap(claimError)
            requireNotNull(database).repository.release(session.token, health)
        }, requireNotNull(executor))
        track(future)
        future.whenComplete { _, error ->
            if (error != null) {
                BlinkLog.error(
                    text("console.health-persistence-save-failed", "player" to session.token.key.playerId),
                    unwrap(error)
                )
            }
        }
        return future
    }

    private fun capture(player: Player): StoredHealth? {
        if (player.isDead || player.health <= 0.0) return null
        val maximum = player.getAttribute(BukkitAttributeTypes.maxHealth)?.value ?: return null
        if (!maximum.isFinite() || maximum <= 0.0 || !player.health.isFinite()) return null
        return StoredHealth(player.health.coerceAtMost(maximum), maximum)
    }

    private fun readPdc(player: Player, characterKey: String): StoredHealth? {
        val key = pdcKey(characterKey)
        val bytes = player.persistentDataContainer.get(key, PersistentDataType.BYTE_ARRAY) ?: return null
        val decoded = StoredHealthCodec.decode(bytes)
        if (decoded == null) player.persistentDataContainer.remove(key)
        return decoded
    }

    private fun writePdc(player: Player, characterKey: String, health: StoredHealth) {
        player.persistentDataContainer.set(
            pdcKey(characterKey),
            PersistentDataType.BYTE_ARRAY,
            StoredHealthCodec.encode(health)
        )
    }

    private fun pdcKey(characterKey: String): NamespacedKey =
        NamespacedKey(plugin, "health_$characterKey")

    private fun runOnMainThread(action: () -> Unit) {
        if (closed) return
        runCatching { Bukkit.getScheduler().runTask(plugin, Runnable(action)) }
    }

    private fun track(future: CompletableFuture<*>) {
        pendingWrites += future
        future.whenComplete { _, _ -> pendingWrites -= future }
    }

    private fun text(key: String, vararg variables: Pair<String, Any?>): String =
        priv.seventeen.artist.symphony.bukkit.runtime.SymphonyRuntime.language().text(key, *variables)

    override fun close() {
        check(Bukkit.isPrimaryThread()) { "生命值持久化必须在 Bukkit 主线程关闭" }
        if (closed) return
        checkpointTask?.cancel()
        checkpointTask = null
        val releases = mutableListOf<CompletableFuture<*>>()
        val online = Bukkit.getOnlinePlayers().associateBy(Player::getUniqueId)
        sessions.values.toList().forEach { session ->
            session.restoreTask?.cancel()
            val player = online[session.token.key.playerId]
            val health = player?.let { finalHealth(it, session) }
            when (settings.storage) {
                HealthStorageMode.PDC -> if (player != null && health != null) {
                    writePdc(player, session.token.key.characterKey, health)
                }
                HealthStorageMode.MYSQL -> releases += scheduleRelease(session, health)
            }
        }
        sessions.clear()
        closed = true
        if (settings.storage == HealthStorageMode.MYSQL) {
            val all = (pendingWrites.toList() + releases).distinct().toTypedArray()
            runCatching {
                CompletableFuture.allOf(*all).get(SHUTDOWN_WAIT_SECONDS, TimeUnit.SECONDS)
            }.onFailure { error ->
                BlinkLog.error(text("console.health-persistence-shutdown-incomplete"), unwrap(error))
            }
            executor?.shutdown()
            if (executor?.awaitTermination(SHUTDOWN_WAIT_SECONDS, TimeUnit.SECONDS) == false) {
                executor.shutdownNow()
            }
            database?.close()
        }
        pendingWrites.clear()
    }

    private class Session(
        val token: HealthSessionToken,
        val joinHealth: Double,
        val initial: StoredHealth,
        val claim: CompletableFuture<StoredHealth?>,
        var loaded: StoredHealth? = null,
        var persisted: StoredHealth? = null,
        var inFlight: StoredHealth? = null,
        var claimCompleted: Boolean = false,
        var settled: Boolean = false,
        var restoring: Boolean = true,
        var failed: Boolean = false,
        var terminalHealth: StoredHealth? = null,
        var restoreTask: BukkitTask? = null
    )

    internal class MysqlDatabase(
        val settings: MysqlHealthDatabaseSettings,
        private val dataSource: HikariDataSource,
        val repository: MysqlHealthStateRepository
    ) : AutoCloseable {
        override fun close() = dataSource.close()
    }

    companion object {
        private val THREAD_COUNTER = AtomicInteger()
        private const val SHUTDOWN_WAIT_SECONDS = 5L

        fun create(
            plugin: Plugin,
            settings: HealthPersistenceSettings,
            levelReader: (Player) -> LevelSnapshot?,
            restoreDelayTicks: Long
        ): HealthPersistenceRuntime {
            val database = if (settings.storage == HealthStorageMode.MYSQL) {
                val mysql = HealthDatabaseSettingsLoader.load(plugin.dataFolder.toPath().resolve("database.yml"))
                val hikari = HikariDataSource(HikariConfig().apply {
                    jdbcUrl = "jdbc:mysql://${mysql.host}:${mysql.port}/${mysql.database}" +
                        "?useSSL=${mysql.useSsl}" +
                        "&allowPublicKeyRetrieval=${mysql.allowPublicKeyRetrieval}" +
                        "&useUnicode=true&characterEncoding=UTF-8&serverTimezone=UTC"
                    username = mysql.username
                    password = mysql.password
                    maximumPoolSize = mysql.poolSize
                    minimumIdle = minOf(1, mysql.poolSize)
                    connectionTimeout = 10_000L
                    validationTimeout = 5_000L
                    idleTimeout = 600_000L
                    maxLifetime = 1_800_000L
                    keepaliveTime = 120_000L
                    initializationFailTimeout = 10_000L
                    poolName = "Symphony-Health"
                    addDataSourceProperty("cachePrepStmts", true)
                    addDataSourceProperty("prepStmtCacheSize", 250)
                    addDataSourceProperty("prepStmtCacheSqlLimit", 2048)
                })
                val repository = MysqlHealthStateRepository(hikari)
                try {
                    repository.initialize()
                    BlinkLog.success(
                        priv.seventeen.artist.symphony.bukkit.runtime.SymphonyRuntime.language()
                            .text("console.health-persistence-mysql-ready")
                    )
                    MysqlDatabase(mysql, hikari, repository)
                } catch (error: Throwable) {
                    hikari.close()
                    throw IllegalStateException("MySQL 生命值存储初始化失败", error)
                }
            } else {
                null
            }
            return HealthPersistenceRuntime(
                plugin,
                settings,
                levelReader,
                restoreDelayTicks.coerceAtLeast(1L),
                database
            )
        }
    }
}

private fun unwrap(error: Throwable): Throwable =
    (error as? java.util.concurrent.CompletionException)?.cause ?: error
