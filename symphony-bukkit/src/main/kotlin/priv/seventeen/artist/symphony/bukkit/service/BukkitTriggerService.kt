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

import org.bukkit.plugin.Plugin
import priv.seventeen.artist.symphony.api.registration.RegistrationHandle
import priv.seventeen.artist.symphony.api.trigger.SymphonyTrigger
import priv.seventeen.artist.symphony.api.trigger.TriggerContext
import priv.seventeen.artist.symphony.api.trigger.TriggerDispatchResult
import priv.seventeen.artist.symphony.api.trigger.TriggerService
import priv.seventeen.artist.symphony.engine.trigger.BuiltInTriggers
import priv.seventeen.artist.symphony.engine.trigger.CallbackCircuitBreaker
import priv.seventeen.artist.symphony.engine.trigger.TriggerDispatcher

class BukkitTriggerService(
    failureWindowSeconds: Long,
    disableAfterFailures: Int,
    onFailure: (String, Throwable) -> Unit
) : TriggerService {
    private val registry = OwnedPriorityRegistry<SymphonyTrigger<*>>("trigger")
    private val breaker = CallbackCircuitBreaker(failureWindowSeconds * 1000L, disableAfterFailures)
    private val dispatcher = TriggerDispatcher(breaker, onFailure = onFailure)

    override fun <C : TriggerContext> dispatch(
        trigger: SymphonyTrigger<C>,
        context: C
    ): TriggerDispatchResult {
        require(trigger.contextType.isInstance(context)) {
            "上下文 ${context::class.qualifiedName} 与 ${trigger.contextType.qualifiedName} 不匹配"
        }
        return dispatcher.dispatch(trigger, context)
    }

    override fun registerTrigger(
        owner: Plugin,
        trigger: SymphonyTrigger<*>,
        priority: Int
    ): RegistrationHandle = registry.register(owner, trigger.id, priority, trigger) {}

    override fun registeredTriggers(): List<SymphonyTrigger<*>> {
        val external = registry.active().associateBy { it.key }
        val builtIns = BuiltInTriggers.all.filterNot { external.containsKey(it.id) }
        return (builtIns + external.values.map { it.value }).sortedBy { it.id.toString() }
    }

    override fun enableCallback(callbackId: String): Boolean = breaker.enable(callbackId)
    override fun disableCallback(callbackId: String): Boolean = breaker.disable(callbackId)

    fun dispatcher(): TriggerDispatcher = dispatcher
    fun failures() = breaker.states()
    fun hasCallbacks(trigger: SymphonyTrigger<*>): Boolean = dispatcher.hasCallbacks(trigger.id)
    fun removeOwner(owner: Plugin) = registry.closeOwner(owner)
    fun clear() {
        registry.clear()
        dispatcher.clear()
    }
}
