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

package priv.seventeen.artist.symphony.bukkit.service

import org.bukkit.NamespacedKey
import org.bukkit.plugin.Plugin
import priv.seventeen.artist.symphony.api.registration.RegistrationHandle
import priv.seventeen.artist.symphony.api.registration.RegistrationConflictException
import java.util.concurrent.atomic.AtomicBoolean

internal class ManagedRegistrationHandle(
    override val owner: Plugin,
    override val key: NamespacedKey,
    override val type: String,
    private val unregisterAction: () -> Boolean
) : RegistrationHandle {
    private val registered = AtomicBoolean(true)
    override val isRegistered: Boolean get() = registered.get()

    override fun unregister(): Boolean {
        if (!registered.compareAndSet(true, false)) return false
        return unregisterAction()
    }
}

internal data class RegistryEntry<T>(
    val owner: Plugin,
    val key: NamespacedKey,
    val priority: Int,
    val sequence: Long,
    val value: T
)

internal class OwnedPriorityRegistry<T>(private val type: String) {
    private val entries = linkedMapOf<NamespacedKey, MutableList<RegistryEntry<T>>>()
    private var sequence = 0L

    @Synchronized
    fun register(owner: Plugin, key: NamespacedKey, priority: Int, value: T, onChange: () -> Unit): RegistrationHandle {
        require(ownerNamespace(owner) == key.namespace) {
            "$type 的键 $key 必须使用所有者命名空间 ${ownerNamespace(owner)}"
        }
        val bucket = entries.getOrPut(key, ::mutableListOf)
        if (bucket.any { it.priority == priority }) {
            throw RegistrationConflictException("$type 的键 $key 已经存在优先级为 $priority 的注册项")
        }
        val entry = RegistryEntry(owner, key, priority, ++sequence, value)
        bucket += entry
        onChange()
        return ManagedRegistrationHandle(owner, key, type) {
            synchronized(this) {
                val changed = entries[key]?.remove(entry) == true
                if (entries[key].isNullOrEmpty()) entries.remove(key)
                if (changed) onChange()
                changed
            }
        }
    }

    @Synchronized
    fun active(): List<RegistryEntry<T>> = entries.values.mapNotNull { bucket ->
        bucket.maxWithOrNull(compareBy({ it.priority }, { it.sequence }))
    }.sortedBy { it.key.toString() }

    @Synchronized
    fun closeOwner(owner: Plugin, onChange: () -> Unit = {}) {
        var changed = false
        entries.values.forEach { changed = it.removeIf { entry -> entry.owner === owner } || changed }
        entries.entries.removeIf { it.value.isEmpty() }
        if (changed) onChange()
    }

    @Synchronized
    fun clear() {
        entries.clear()
    }

    private fun ownerNamespace(owner: Plugin): String = owner.name.lowercase().replace(Regex("[^a-z0-9._-]"), "_")
}
