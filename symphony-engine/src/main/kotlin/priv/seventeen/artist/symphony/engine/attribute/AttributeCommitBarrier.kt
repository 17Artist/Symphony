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

package priv.seventeen.artist.symphony.engine.attribute

import java.util.UUID
import java.util.concurrent.ConcurrentHashMap


fun interface AttributeCommitBarrier {
    fun submit(entityId: UUID, revision: Long, action: () -> Unit)

    object Immediate : AttributeCommitBarrier {
        override fun submit(entityId: UUID, revision: Long, action: () -> Unit) = action()
    }
}

/** 每个实体只保留最新的一次延迟提交。 */
class LatestAttributeCommitQueue {
    private data class PendingCommit(val revision: Long, val action: () -> Unit)

    private val pending = ConcurrentHashMap<UUID, PendingCommit>()

    fun defer(entityId: UUID, revision: Long, action: () -> Unit) {
        pending.compute(entityId) { _, current ->
            if (current == null || revision >= current.revision) PendingCommit(revision, action) else current
        }
    }

    fun flush(entityId: UUID): Boolean {
        val commit = pending.remove(entityId) ?: return false
        commit.action()
        return true
    }

    fun revision(entityId: UUID): Long? = pending[entityId]?.revision

    fun entityIds(): Set<UUID> = pending.keys.toSet()

    fun forget(entityId: UUID) {
        pending.remove(entityId)
    }

    fun clear() {
        pending.clear()
    }
}
