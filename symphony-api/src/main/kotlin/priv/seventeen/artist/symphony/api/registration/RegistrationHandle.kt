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

package priv.seventeen.artist.symphony.api.registration

import org.bukkit.NamespacedKey
import org.bukkit.plugin.Plugin

/** 一项归属于指定所有者的扩展注册；重复关闭句柄不会产生额外影响。 */
interface RegistrationHandle : AutoCloseable {
    val owner: Plugin
    val key: NamespacedKey
    val type: String
    val isRegistered: Boolean

    fun unregister(): Boolean

    override fun close() {
        unregister()
    }
}

class RegistrationConflictException(message: String) : IllegalStateException(message)
