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

import java.sql.SQLException
import javax.sql.DataSource

internal class MysqlHealthStateRepository(
    private val dataSource: DataSource,
    private val clock: () -> Long = System::currentTimeMillis
) {
    fun initialize() {
        dataSource.connection.use { connection ->
            connection.createStatement().use { statement ->
                statement.executeUpdate(
                    """
                    CREATE TABLE IF NOT EXISTS symphony_health_state (
                        cluster_id VARCHAR(64) NOT NULL,
                        player_uuid CHAR(36) NOT NULL,
                        character_key CHAR(64) NOT NULL,
                        health DOUBLE NOT NULL,
                        maximum_health DOUBLE NOT NULL,
                        session_id CHAR(36) NOT NULL,
                        state VARCHAR(8) NOT NULL,
                        revision BIGINT NOT NULL,
                        updated_at BIGINT NOT NULL,
                        PRIMARY KEY (cluster_id, player_uuid, character_key)
                    )
                    """.trimIndent()
                )
            }
        }
    }

    /**
     * 认领玩家记录。旧服务器仍在线时先短暂等待其最终提交；等待结束后以比较并交换方式接管，
     * 使旧会话随后到达的写入失效。
     */
    fun claim(
        token: HealthSessionToken,
        current: StoredHealth,
        waitMillis: Long = 2_500L,
        retryMillis: Long = 100L
    ): StoredHealth? {
        val deadline = clock() + waitMillis.coerceAtLeast(0L)
        while (true) {
            val row = read(token.key)
            if (row == null) {
                if (insert(token, current)) return null
                continue
            }
            if (row.sessionId == token.sessionId.toString()) return row.health
            if (row.state == READY) {
                if (claimReady(token, row.revision)) return row.health
                continue
            }
            val remaining = deadline - clock()
            if (remaining <= 0L) {
                if (takeOverActive(token, row)) return row.health
                continue
            }
            try {
                Thread.sleep(minOf(retryMillis.coerceAtLeast(1L), remaining))
            } catch (interrupted: InterruptedException) {
                Thread.currentThread().interrupt()
                throw IllegalStateException("等待旧服务器提交生命值时线程被中断", interrupted)
            }
        }
    }

    fun checkpoint(token: HealthSessionToken, health: StoredHealth): Boolean =
        dataSource.connection.use { connection ->
            connection.prepareStatement(
                """
                UPDATE symphony_health_state
                SET health = ?, maximum_health = ?, revision = revision + 1, updated_at = ?
                WHERE cluster_id = ? AND player_uuid = ? AND character_key = ?
                  AND session_id = ? AND state = ?
                """.trimIndent()
            ).use { statement ->
                statement.setDouble(1, health.health)
                statement.setDouble(2, health.maximumHealth)
                statement.setLong(3, clock())
                bindKey(statement, 4, token.key)
                statement.setString(7, token.sessionId.toString())
                statement.setString(8, ACTIVE)
                statement.executeUpdate() == 1
            }
        }

    fun release(token: HealthSessionToken, health: StoredHealth?): Boolean =
        dataSource.connection.use { connection ->
            val sql = if (health == null) {
                """
                UPDATE symphony_health_state
                SET state = ?, revision = revision + 1, updated_at = ?
                WHERE cluster_id = ? AND player_uuid = ? AND character_key = ?
                  AND session_id = ? AND state = ?
                """.trimIndent()
            } else {
                """
                UPDATE symphony_health_state
                SET health = ?, maximum_health = ?, state = ?, revision = revision + 1, updated_at = ?
                WHERE cluster_id = ? AND player_uuid = ? AND character_key = ?
                  AND session_id = ? AND state = ?
                """.trimIndent()
            }
            connection.prepareStatement(sql).use { statement ->
                var index = 1
                if (health != null) {
                    statement.setDouble(index++, health.health)
                    statement.setDouble(index++, health.maximumHealth)
                }
                statement.setString(index++, READY)
                statement.setLong(index++, clock())
                bindKey(statement, index, token.key)
                index += 3
                statement.setString(index++, token.sessionId.toString())
                statement.setString(index, ACTIVE)
                statement.executeUpdate() == 1
            }
        }

    private fun read(key: HealthStateKey): Row? = dataSource.connection.use { connection ->
        connection.prepareStatement(
            """
            SELECT health, maximum_health, session_id, state, revision
            FROM symphony_health_state
            WHERE cluster_id = ? AND player_uuid = ? AND character_key = ?
            """.trimIndent()
        ).use { statement ->
            bindKey(statement, 1, key)
            statement.executeQuery().use { result ->
                if (!result.next()) {
                    null
                } else {
                    Row(
                        StoredHealth(result.getDouble("health"), result.getDouble("maximum_health")),
                        result.getString("session_id"),
                        result.getString("state"),
                        result.getLong("revision")
                    )
                }
            }
        }
    }

    private fun insert(token: HealthSessionToken, health: StoredHealth): Boolean =
        try {
            dataSource.connection.use { connection ->
                connection.prepareStatement(
                    """
                    INSERT INTO symphony_health_state
                        (cluster_id, player_uuid, character_key, health, maximum_health,
                         session_id, state, revision, updated_at)
                    VALUES (?, ?, ?, ?, ?, ?, ?, 1, ?)
                    """.trimIndent()
                ).use { statement ->
                    bindKey(statement, 1, token.key)
                    statement.setDouble(4, health.health)
                    statement.setDouble(5, health.maximumHealth)
                    statement.setString(6, token.sessionId.toString())
                    statement.setString(7, ACTIVE)
                    statement.setLong(8, clock())
                    statement.executeUpdate() == 1
                }
            }
        } catch (error: SQLException) {
            if (error.sqlState?.startsWith("23") == true) false else throw error
        }

    private fun claimReady(token: HealthSessionToken, revision: Long): Boolean =
        dataSource.connection.use { connection ->
            connection.prepareStatement(
                """
                UPDATE symphony_health_state
                SET session_id = ?, state = ?, revision = revision + 1, updated_at = ?
                WHERE cluster_id = ? AND player_uuid = ? AND character_key = ?
                  AND state = ? AND revision = ?
                """.trimIndent()
            ).use { statement ->
                statement.setString(1, token.sessionId.toString())
                statement.setString(2, ACTIVE)
                statement.setLong(3, clock())
                bindKey(statement, 4, token.key)
                statement.setString(7, READY)
                statement.setLong(8, revision)
                statement.executeUpdate() == 1
            }
        }

    private fun takeOverActive(token: HealthSessionToken, row: Row): Boolean =
        dataSource.connection.use { connection ->
            connection.prepareStatement(
                """
                UPDATE symphony_health_state
                SET session_id = ?, revision = revision + 1, updated_at = ?
                WHERE cluster_id = ? AND player_uuid = ? AND character_key = ?
                  AND state = ? AND session_id = ? AND revision = ?
                """.trimIndent()
            ).use { statement ->
                statement.setString(1, token.sessionId.toString())
                statement.setLong(2, clock())
                bindKey(statement, 3, token.key)
                statement.setString(6, ACTIVE)
                statement.setString(7, row.sessionId)
                statement.setLong(8, row.revision)
                statement.executeUpdate() == 1
            }
        }

    private fun bindKey(statement: java.sql.PreparedStatement, start: Int, key: HealthStateKey) {
        statement.setString(start, key.clusterId)
        statement.setString(start + 1, key.playerId.toString())
        statement.setString(start + 2, key.characterKey)
    }

    private data class Row(
        val health: StoredHealth,
        val sessionId: String,
        val state: String,
        val revision: Long
    )

    private companion object {
        const val ACTIVE = "ACTIVE"
        const val READY = "READY"
    }
}
