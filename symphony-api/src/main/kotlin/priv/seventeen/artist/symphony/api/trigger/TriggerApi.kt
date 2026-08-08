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

package priv.seventeen.artist.symphony.api.trigger

import org.bukkit.NamespacedKey
import org.bukkit.entity.LivingEntity
import org.bukkit.plugin.Plugin
import priv.seventeen.artist.symphony.api.registration.RegistrationHandle
import java.util.UUID
import kotlin.reflect.KClass

enum class TriggerPhase {
    PREPARE,
    APPLY,
    CONFIRMED,
    NOTIFY
}

enum class ResultPolicy {
    IGNORE,
    BOOLEAN,
    FINITE_NUMBER,
    DAMAGE_ADJUSTMENT
}

interface TriggerContext {
    val transactionId: UUID
    val self: LivingEntity
    val target: LivingEntity?
    val createdAtMillis: Long
}

interface SymphonyTrigger<C : TriggerContext> {
    val id: NamespacedKey
    val phase: TriggerPhase
    val contextType: KClass<C>
    val resultPolicy: ResultPolicy
    val cancellable: Boolean
}

data class TriggerDispatchResult(
    val trigger: NamespacedKey,
    val invokedCallbacks: Int,
    val failedCallbacks: Int,
    val cancelled: Boolean,
    val result: Any? = null
)

interface TriggerService {
    fun <C : TriggerContext> dispatch(trigger: SymphonyTrigger<C>, context: C): TriggerDispatchResult
    fun registerTrigger(owner: Plugin, trigger: SymphonyTrigger<*>, priority: Int = 0): RegistrationHandle
    fun registeredTriggers(): List<SymphonyTrigger<*>>
    fun enableCallback(callbackId: String): Boolean
    fun disableCallback(callbackId: String): Boolean
}

