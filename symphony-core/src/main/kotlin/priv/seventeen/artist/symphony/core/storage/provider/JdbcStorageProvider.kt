package priv.seventeen.artist.symphony.core.storage.provider

import com.google.gson.Gson
import priv.seventeen.artist.blink.BlinkLog
import priv.seventeen.artist.symphony.core.data.PersistentData
import priv.seventeen.artist.symphony.core.storage.StorageProvider
import java.sql.Connection
import java.sql.DriverManager
import java.sql.SQLException
import java.util.UUID

abstract class JdbcStorageProvider : StorageProvider {
    protected val gson = Gson()
    protected var connection: Connection? = null

    protected abstract fun connect(): Connection
    protected abstract val backendName: String

    override fun initialize() {
        try {
            connection = connect()
            connection!!.createStatement().use { st ->
                st.executeUpdate(
                    "CREATE TABLE IF NOT EXISTS symphony_players (" +
                        "uuid VARCHAR(36) PRIMARY KEY, " +
                        "data TEXT NOT NULL" +
                    ")"
                )
            }
            BlinkLog.info("$backendName 存储已初始化")
        } catch (e: Exception) {
            BlinkLog.error("$backendName 存储初始化失败: ${e.message}")
            throw e
        }
    }

    override fun shutdown() {
        try { connection?.close() } catch (_: SQLException) {}
        BlinkLog.info("$backendName 存储已关闭")
    }

    override fun loadPlayer(uuid: UUID): PersistentData? {
        val conn = connection ?: return null
        return try {
            conn.prepareStatement("SELECT data FROM symphony_players WHERE uuid = ?").use { ps ->
                ps.setString(1, uuid.toString())
                val rs = ps.executeQuery()
                if (rs.next()) gson.fromJson(rs.getString("data"), PersistentData::class.java) else null
            }
        } catch (e: Exception) {
            BlinkLog.warn("加载玩家数据失败 $uuid: ${e.message}")
            null
        }
    }

    override fun savePlayer(uuid: UUID, data: PersistentData) {
        val conn = connection ?: return
        try {
            conn.prepareStatement(upsertSql()).use { ps ->
                ps.setString(1, uuid.toString())
                ps.setString(2, gson.toJson(data))
                bindUpsertExtras(ps, data)
                ps.executeUpdate()
            }
        } catch (e: Exception) {
            BlinkLog.warn("保存玩家数据失败 $uuid: ${e.message}")
        }
    }

    override fun saveAll(dataMap: Map<UUID, PersistentData>) {
        val conn = connection ?: return
        try {
            conn.autoCommit = false
            conn.prepareStatement(upsertSql()).use { ps ->
                for ((uuid, data) in dataMap) {
                    ps.setString(1, uuid.toString())
                    ps.setString(2, gson.toJson(data))
                    bindUpsertExtras(ps, data)
                    ps.addBatch()
                }
                ps.executeBatch()
            }
            conn.commit()
        } catch (e: Exception) {
            BlinkLog.warn("批量保存失败: ${e.message}")
            try { conn.rollback() } catch (_: SQLException) {}
        } finally {
            try { conn.autoCommit = true } catch (_: SQLException) {}
        }
    }

    override fun deletePlayer(uuid: UUID) {
        val conn = connection ?: return
        try {
            conn.prepareStatement("DELETE FROM symphony_players WHERE uuid = ?").use {
                it.setString(1, uuid.toString())
                it.executeUpdate()
            }
        } catch (e: Exception) {
            BlinkLog.warn("删除玩家数据失败 $uuid: ${e.message}")
        }
    }

    override fun exists(uuid: UUID): Boolean {
        val conn = connection ?: return false
        return try {
            conn.prepareStatement("SELECT 1 FROM symphony_players WHERE uuid = ?").use {
                it.setString(1, uuid.toString())
                it.executeQuery().next()
            }
        } catch (e: Exception) { false }
    }

    protected abstract fun upsertSql(): String
    protected open fun bindUpsertExtras(ps: java.sql.PreparedStatement, data: PersistentData) {}
}

class SqliteStorageProvider(private val dbFile: java.io.File) : JdbcStorageProvider() {
    override val backendName = "SQLite"

    override fun connect(): Connection {
        Class.forName("org.sqlite.JDBC")
        dbFile.parentFile?.mkdirs()
        return DriverManager.getConnection("jdbc:sqlite:${dbFile.absolutePath}")
    }

    override fun upsertSql(): String =
        "INSERT INTO symphony_players(uuid, data) VALUES(?, ?) " +
        "ON CONFLICT(uuid) DO UPDATE SET data = excluded.data"
}

class MysqlStorageProvider(
    private val host: String,
    private val port: Int,
    private val database: String,
    private val username: String,
    private val password: String
) : JdbcStorageProvider() {
    override val backendName = "MySQL"

    override fun connect(): Connection {
        Class.forName("com.mysql.cj.jdbc.Driver")
        val url = "jdbc:mysql://$host:$port/$database?useSSL=false&serverTimezone=UTC&characterEncoding=utf8"
        return DriverManager.getConnection(url, username, password)
    }

    override fun upsertSql(): String =
        "INSERT INTO symphony_players(uuid, data) VALUES(?, ?) " +
        "ON DUPLICATE KEY UPDATE data = VALUES(data)"
}
