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

import java.lang.reflect.Proxy
import java.util.UUID
import kotlin.reflect.KClass
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue
import org.bukkit.NamespacedKey
import org.bukkit.entity.LivingEntity
import priv.seventeen.artist.symphony.api.trigger.ResultPolicy
import priv.seventeen.artist.symphony.api.trigger.SymphonyTrigger
import priv.seventeen.artist.symphony.api.trigger.TriggerContext
import priv.seventeen.artist.symphony.api.trigger.TriggerPhase

class TriggerDispatcherTest {
    data class Context(
        override val transactionId: UUID = UUID.randomUUID(),
        override val self: LivingEntity = entity(),
        override val target: LivingEntity? = null,
        override val createdAtMillis: Long = 1
    ) : TriggerContext

    @Test
    fun `dispatch is deterministic and boolean callbacks cancel`() {
        val order = mutableListOf<String>()
        val trigger = trigger(ResultPolicy.BOOLEAN, cancellable = true)
        val dispatcher = TriggerDispatcher(CallbackCircuitBreaker(60_000, 3))
        dispatcher.replaceCallbacks(listOf(
            callback("late", trigger.id, 20) { order += "late"; true },
            callback("early", trigger.id, 10) { order += "early"; false }
        ))
        val result = dispatcher.dispatch(trigger, Context())
        assertEquals(listOf("early", "late"), order)
        assertEquals(false, result.result)
        assertTrue(result.cancelled)
        assertEquals(2, result.invokedCallbacks)
    }

    @Test
    fun `failure isolation trips breaker and manual enable recovers`() {
        var attempts = 0
        val trigger = trigger(ResultPolicy.IGNORE, false)
        val breaker = CallbackCircuitBreaker(60_000, 2)
        val dispatcher = TriggerDispatcher(breaker)
        dispatcher.replaceCallbacks(listOf(callback("bad", trigger.id, 0) { attempts++; error("boom") }))
        assertEquals(1, dispatcher.dispatch(trigger, Context()).failedCallbacks)
        assertEquals(1, dispatcher.dispatch(trigger, Context()).failedCallbacks)
        assertEquals(0, dispatcher.dispatch(trigger, Context()).failedCallbacks)
        assertEquals(2, attempts)
        assertFalse(breaker.states().single().enabled)
        assertTrue(breaker.enable("bad"))
        assertEquals(1, dispatcher.dispatch(trigger, Context()).failedCallbacks)
        assertEquals(3, attempts)
    }

    @Test
    fun `finite number policy returns the last deterministic callback result`() {
        val trigger = trigger(ResultPolicy.FINITE_NUMBER, false)
        val dispatcher = TriggerDispatcher(CallbackCircuitBreaker(60_000, 3))
        dispatcher.replaceCallbacks(listOf(
            callback("late", trigger.id, 20) { 25.0 },
            callback("early", trigger.id, 10) { 10.0 }
        ))
        val result = dispatcher.dispatch(trigger, Context())
        assertEquals(25.0, result.result)
        assertEquals(2, result.invokedCallbacks)
    }

    @Test
    fun `non finite callback result is isolated and counted as failure`() {
        val trigger = trigger(ResultPolicy.FINITE_NUMBER, false)
        val failures = mutableListOf<String>()
        val dispatcher = TriggerDispatcher(CallbackCircuitBreaker(60_000, 3), onFailure = { id, _ -> failures += id })
        dispatcher.replaceCallbacks(listOf(callback("nan", trigger.id, 0) { Double.NaN }))
        val result = dispatcher.dispatch(trigger, Context())
        assertEquals(0, result.invokedCallbacks)
        assertEquals(1, result.failedCallbacks)
        assertEquals(listOf("nan"), failures)
    }

    @Test
    fun `recursive callback is skipped rather than invoked twice`() {
        val trigger = trigger(ResultPolicy.IGNORE, false)
        lateinit var dispatcher: TriggerDispatcher
        var attempts = 0
        dispatcher = TriggerDispatcher(CallbackCircuitBreaker(60_000, 3))
        dispatcher.replaceCallbacks(listOf(callback("recursive", trigger.id, 0) {
            attempts++
            dispatcher.dispatch(trigger, it)
        }))
        val result = dispatcher.dispatch(trigger, Context())
        assertEquals(1, attempts)
        assertEquals(1, result.invokedCallbacks)
    }

    @Test
    fun `duplicate callback ids are rejected during atomic replacement`() {
        val trigger = trigger(ResultPolicy.IGNORE, false)
        val dispatcher = TriggerDispatcher(CallbackCircuitBreaker(60_000, 3))
        assertFailsWith<IllegalArgumentException> {
            dispatcher.replaceCallbacks(listOf(
                callback("duplicate", trigger.id, 0) { null },
                callback("duplicate", trigger.id, 1) { null }
            ))
        }
    }

    @Test
    fun `callback replacement releases removed circuit breaker state`() {
        val trigger = trigger(ResultPolicy.IGNORE, false)
        val breaker = CallbackCircuitBreaker(60_000, 1)
        val dispatcher = TriggerDispatcher(breaker)
        dispatcher.replaceCallbacks(listOf(callback("removed", trigger.id, 0) { error("boom") }))
        dispatcher.dispatch(trigger, Context())
        assertEquals(listOf("removed"), breaker.states().map { it.callbackId })

        dispatcher.replaceCallbacks(listOf(callback("retained", trigger.id, 0) { null }))

        assertTrue(breaker.states().isEmpty())
        assertTrue(dispatcher.hasCallbacks(trigger.id))
        dispatcher.clear()
        assertFalse(dispatcher.hasCallbacks(trigger.id))
    }

    private fun trigger(policy: ResultPolicy, cancellable: Boolean) = object : SymphonyTrigger<Context> {
        override val id = NamespacedKey("symphony", "test")
        override val phase = TriggerPhase.PREPARE
        override val contextType: KClass<Context> = Context::class
        override val resultPolicy = policy
        override val cancellable = cancellable
    }

    private fun callback(id: String, trigger: NamespacedKey, priority: Int, body: (Context) -> Any?) =
        RegisteredCallback(id, trigger, priority, "test", TriggerCallback(body))

    companion object {
        private fun entity(): LivingEntity = Proxy.newProxyInstance(
            LivingEntity::class.java.classLoader,
            arrayOf(LivingEntity::class.java)
        ) { _, method, _ ->
            when (method.returnType) {
                java.lang.Boolean.TYPE -> false
                java.lang.Integer.TYPE -> 0
                java.lang.Long.TYPE -> 0L
                java.lang.Double.TYPE -> 0.0
                java.lang.Float.TYPE -> 0f
                else -> null
            }
        } as LivingEntity
    }
}
