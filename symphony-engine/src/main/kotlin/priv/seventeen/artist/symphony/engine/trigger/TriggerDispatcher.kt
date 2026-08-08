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

package priv.seventeen.artist.symphony.engine.trigger

import org.bukkit.NamespacedKey
import priv.seventeen.artist.symphony.api.trigger.ResultPolicy
import priv.seventeen.artist.symphony.api.trigger.SymphonyTrigger
import priv.seventeen.artist.symphony.api.trigger.TriggerContext
import priv.seventeen.artist.symphony.api.trigger.TriggerDispatchResult
import java.util.ArrayDeque
import java.util.concurrent.ConcurrentHashMap

fun interface TriggerCallback<C : TriggerContext> {
    fun invoke(context: C): Any?
}

data class RegisteredCallback<C : TriggerContext>(
    val id: String,
    val triggerId: NamespacedKey,
    val priority: Int,
    val ownerDefinitionId: String,
    val callback: TriggerCallback<C>
)

data class CallbackFailureState(
    val callbackId: String,
    val enabled: Boolean,
    val recentFailures: Int,
    val lastFailureMillis: Long?,
    val lastMessage: String?
)

class CallbackCircuitBreaker(
    private val failureWindowMillis: Long,
    private val disableAfterFailures: Int,
    private val clock: () -> Long = System::currentTimeMillis
) {
    private data class State(
        val failures: ArrayDeque<Pair<Long, String>> = ArrayDeque(),
        var manuallyDisabled: Boolean = false,
        var tripped: Boolean = false
    )

    private val states = ConcurrentHashMap<String, State>()

    fun canExecute(callbackId: String): Boolean {
        val state = states[callbackId] ?: return true
        synchronized(state) {
            prune(state, clock())
            return !state.manuallyDisabled && !state.tripped
        }
    }

    fun recordSuccess(callbackId: String) {
        val state = states[callbackId] ?: return
        synchronized(state) { prune(state, clock()) }
    }

    fun recordFailure(callbackId: String, error: Throwable): CallbackFailureState {
        val state = states.computeIfAbsent(callbackId) { State() }
        synchronized(state) {
            val now = clock()
            prune(state, now)
            state.failures.addLast(now to (error.message ?: error::class.simpleName.orEmpty()))
            if (state.failures.size >= disableAfterFailures) state.tripped = true
            return snapshot(callbackId, state)
        }
    }

    fun enable(callbackId: String): Boolean {
        val state = states.computeIfAbsent(callbackId) { State() }
        synchronized(state) {
            val changed = state.manuallyDisabled || state.tripped
            state.manuallyDisabled = false
            state.tripped = false
            state.failures.clear()
            return changed
        }
    }

    fun disable(callbackId: String): Boolean {
        val state = states.computeIfAbsent(callbackId) { State() }
        synchronized(state) {
            val changed = !state.manuallyDisabled
            state.manuallyDisabled = true
            return changed
        }
    }

    fun states(): List<CallbackFailureState> = states.entries.map { (id, state) ->
        synchronized(state) {
            prune(state, clock())
            snapshot(id, state)
        }
    }.sortedBy { it.callbackId }

    fun retain(callbackIds: Set<String>) {
        states.keys.removeIf { it !in callbackIds }
    }

    fun clear() {
        states.clear()
    }

    private fun prune(state: State, now: Long) {
        while (state.failures.isNotEmpty() && now - state.failures.first().first > failureWindowMillis) {
            state.failures.removeFirst()
        }
    }

    private fun snapshot(id: String, state: State): CallbackFailureState {
        val last = state.failures.lastOrNull()
        return CallbackFailureState(id, !state.manuallyDisabled && !state.tripped, state.failures.size, last?.first, last?.second)
    }
}

class TriggerDispatcher(
    private val breaker: CallbackCircuitBreaker,
    private val maxDepth: Int = 8,
    private val maxActions: Int = 256,
    private val onFailure: (String, Throwable) -> Unit = { _, _ -> }
) {
    private val callbacks = AtomicCallbackIndex()
    private val callStack = ThreadLocal.withInitial { ArrayDeque<String>() }
    private val actionCount = ThreadLocal.withInitial { 0 }

    fun replaceCallbacks(newCallbacks: Collection<RegisteredCallback<*>>) {
        callbacks.replace(newCallbacks)
        breaker.retain(newCallbacks.mapTo(hashSetOf(), RegisteredCallback<*>::id))
    }

    fun hasCallbacks(triggerId: NamespacedKey): Boolean = callbacks.hasCallbacks(triggerId)

    fun clear() {
        callbacks.replace(emptyList())
        breaker.clear()
        callStack.remove()
        actionCount.remove()
    }

    fun <C : TriggerContext> dispatch(trigger: SymphonyTrigger<C>, context: C): TriggerDispatchResult {
        val stack = callStack.get()
        val root = stack.isEmpty()
        if (root) actionCount.set(0)
        require(stack.size < maxDepth) { "触发器事务超过最大深度 $maxDepth" }

        var invoked = 0
        var failed = 0
        var cancelled = false
        var aggregate: Any? = null

        @Suppress("UNCHECKED_CAST")
        val entries = callbacks.forTrigger(trigger.id) as List<RegisteredCallback<C>>
        try {
            entries.forEach { entry ->
                if (!breaker.canExecute(entry.id) || stack.contains(entry.id)) return@forEach
                val actions = actionCount.get() + 1
                require(actions <= maxActions) { "触发器事务超过最大动作数 $maxActions" }
                actionCount.set(actions)
                stack.addLast(entry.id)
                try {
                    val result = entry.callback.invoke(context)
                    aggregate = applyPolicy(trigger.resultPolicy, aggregate, result)
                    if (trigger.cancellable && result == false) cancelled = true
                    invoked++
                    breaker.recordSuccess(entry.id)
                } catch (error: Throwable) {
                    failed++
                    breaker.recordFailure(entry.id, error)
                    onFailure(entry.id, error)
                } finally {
                    stack.removeLast()
                }
            }
        } finally {
            if (root) {
                stack.clear()
                actionCount.remove()
                callStack.remove()
            }
        }
        return TriggerDispatchResult(trigger.id, invoked, failed, cancelled, aggregate)
    }

    private fun applyPolicy(policy: ResultPolicy, current: Any?, result: Any?): Any? = when (policy) {
        ResultPolicy.IGNORE -> current
        ResultPolicy.BOOLEAN -> when (result) {
            null -> current
            is Boolean -> (current as? Boolean ?: true) && result
            else -> throw IllegalArgumentException("回调必须返回布尔值")
        }
        ResultPolicy.FINITE_NUMBER -> when (result) {
            null -> current
            is Number -> result.toDouble().also { require(it.isFinite()) { "回调返回了非有限数" } }
            else -> throw IllegalArgumentException("回调必须返回有限数")
        }
        ResultPolicy.DAMAGE_ADJUSTMENT -> result ?: current
    }
}

private class AtomicCallbackIndex {
    @Volatile
    private var index: Map<NamespacedKey, List<RegisteredCallback<*>>> = emptyMap()

    fun replace(entries: Collection<RegisteredCallback<*>>) {
        val duplicate = entries.groupingBy { it.id }.eachCount().filterValues { it > 1 }.keys
        require(duplicate.isEmpty()) { "存在重复的回调 ID：$duplicate" }
        index = entries.groupBy { it.triggerId }.mapValues { (_, callbacks) ->
            callbacks.sortedWith(compareBy({ it.priority }, { it.ownerDefinitionId }, { it.id }))
        }
    }

    fun forTrigger(key: NamespacedKey): List<RegisteredCallback<*>> = index[key].orEmpty()

    fun hasCallbacks(key: NamespacedKey): Boolean = index[key].isNullOrEmpty().not()
}
