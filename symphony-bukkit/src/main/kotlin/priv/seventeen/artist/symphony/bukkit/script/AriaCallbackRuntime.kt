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

package priv.seventeen.artist.symphony.bukkit.script

import org.bukkit.entity.LivingEntity
import priv.seventeen.artist.aria.Aria
import priv.seventeen.artist.aria.api.AriaCompiledRoutine
import priv.seventeen.artist.aria.context.VariableKey
import priv.seventeen.artist.aria.interop.JavaObjectMirror
import priv.seventeen.artist.aria.value.IValue
import priv.seventeen.artist.aria.value.ObjectValue
import priv.seventeen.artist.blink.BlinkLog
import priv.seventeen.artist.blink.script.AriaScriptManager
import priv.seventeen.artist.symphony.api.attribute.AttributeDefinition
import priv.seventeen.artist.symphony.api.attribute.AttributeService
import priv.seventeen.artist.symphony.api.damage.DamageService
import priv.seventeen.artist.symphony.api.trigger.SymphonyTrigger
import priv.seventeen.artist.symphony.engine.definition.CallbackDefinition
import priv.seventeen.artist.symphony.engine.definition.DefinitionSnapshot
import priv.seventeen.artist.symphony.engine.definition.ScriptDefinition
import priv.seventeen.artist.symphony.engine.trigger.BuiltInTriggers
import priv.seventeen.artist.symphony.engine.trigger.EntityTriggerContext
import priv.seventeen.artist.symphony.engine.trigger.RegisteredCallback
import priv.seventeen.artist.symphony.bukkit.runtime.SymphonyRuntime
import priv.seventeen.artist.symphony.engine.definition.GenericDefinition
import priv.seventeen.artist.symphony.engine.trigger.TriggerCallback
import java.nio.charset.StandardCharsets
import java.nio.file.Files
import java.nio.file.Path
import java.util.*
import java.util.concurrent.atomic.AtomicReference
import java.util.concurrent.ConcurrentHashMap
import kotlin.collections.ArrayList

enum class CallbackOwnerKind {
    ATTRIBUTE,
    SET,
    AFFIX,
    SKILL,
    STATUS,
    RESONANCE,
    TALENT,
    ENVIRONMENT
}

data class CallbackOwner(
    val kind: CallbackOwnerKind,
    val id: String,
    val qualifier: String? = null
)

fun interface CallbackActivationResolver {
    /** 所有者对该实体生效时返回回调变量，否则返回 null。 */
    fun variables(owner: CallbackOwner, context: EntityTriggerContext): Map<String, Any?>?
}

interface ConfiguredCallbackRuntime {
    fun validateConditions(ownerId: String, conditions: List<Map<String, Any?>>)
    fun validateActions(ownerId: String, actions: List<Map<String, Any?>>)
    fun test(ownerId: String, conditions: List<Map<String, Any?>>, context: EntityTriggerContext): Boolean
    fun execute(ownerId: String, actions: List<Map<String, Any?>>, context: EntityTriggerContext)
}

data class PreparedScriptBundle internal constructor(
    internal val callbacks: List<RegisteredCallback<*>>,
    internal val skills: Map<String, CompiledSkillScript>,
    internal val calculatedAttributeIds: Set<String> = emptySet(),
    internal val intervalGates: List<CallbackIntervalGate> = emptyList()
)

data class CompiledSkillScript internal constructor(
    internal val routine: AriaCompiledRoutine?,
    val actions: List<Map<String, Any?>>
)

internal class CallbackIntervalGate(private val intervalMillis: Long) {
    private val nextRuns = ConcurrentHashMap<UUID, Long>()

    fun tryAcquire(entityId: UUID, now: Long): Boolean {
        var acquired = false
        nextRuns.compute(entityId) { _, allowedAt ->
            if (allowedAt == null || now >= allowedAt) {
                acquired = true
                Math.addExact(now, intervalMillis)
            } else allowedAt
        }
        return acquired
    }

    fun forget(entityId: UUID) {
        nextRuns.remove(entityId)
    }

    fun pruneExpired(now: Long) {
        nextRuns.entries.removeIf { it.value <= now }
    }

    internal fun size(): Int = nextRuns.size
}

/**
 * 在提交定义修订前，编译完整且不可变的脚本与回调候选项。
 * 触发器和伤害热点路径不会执行任何编译工作。
 */
class AriaCallbackRuntime(
    private val scriptsRoot: Path,
    private val attributes: AttributeService,
    private val damageService: DamageService,
    private val callbacks: ConfiguredCallbackRuntime,
    private val activationResolver: CallbackActivationResolver,
    private val slowWarningMillis: Long,
    private val inCombat: (LivingEntity) -> Boolean = { false },
    private val scriptCompiler: (String, String) -> AriaCompiledRoutine = { id, source ->
        check(AriaScriptManager.isAvailable) { "Aria 引擎不可用，无法编译 $id" }
        Aria.compile(id, source)
    }
) {
    private val committed = AtomicReference(PreparedScriptBundle(emptyList(), emptyMap()))
    private val triggers = BuiltInTriggers.all.associateBy { it.id.toString() }

    fun prepare(snapshot: DefinitionSnapshot): PreparedScriptBundle {
        val callbackBindings = ArrayList<RegisteredCallback<*>>()
        val intervalGates = ArrayList<CallbackIntervalGate>()
        snapshot.attributes.toSortedMap().forEach { (key, compiled) ->
            compiled.callbacks.forEach { definition ->
                callbackBindings += compileCallback(
                    CallbackOwner(CallbackOwnerKind.ATTRIBUTE, key.value),
                    definition,
                    intervalGates
                )
            }
        }
        snapshot.sets.toSortedMap().forEach { (setId, set) ->
            set.bonuses.toSortedMap().forEach { (threshold, bonus) ->
                bonus.callbacks.forEach { definition ->
                    callbackBindings += compileCallback(
                        CallbackOwner(CallbackOwnerKind.SET, setId, threshold.toString()),
                        definition,
                        intervalGates
                    )
                }
            }
        }

        callbackBindings += genericCallbacks(snapshot.affixes, CallbackOwnerKind.AFFIX, intervalGates)
        callbackBindings += genericCallbacks(snapshot.skills, CallbackOwnerKind.SKILL, intervalGates)
        callbackBindings += genericCallbacks(snapshot.statuses, CallbackOwnerKind.STATUS, intervalGates)
        callbackBindings += genericCallbacks(snapshot.resonances, CallbackOwnerKind.RESONANCE, intervalGates)
        callbackBindings += genericCallbacks(snapshot.talents, CallbackOwnerKind.TALENT, intervalGates)
        callbackBindings += genericCallbacks(snapshot.environments, CallbackOwnerKind.ENVIRONMENT, intervalGates)

        val skills = snapshot.skills.toSortedMap().mapValues { (id, definition) ->
            val inline = definition.values["script"] as? String
            val file = definition.values["file"] as? String
            require(inline == null || file == null) { "$id 不能同时定义 script 与 file" }
            val actions = mapList(definition.values["actions"], "$id.actions")
            callbacks.validateActions(id, actions)
            val routine = if (inline != null || file != null) {
                compileScript(ScriptDefinition("skill.$id", inline, file))
            } else null
            require(routine != null || actions.isNotEmpty()) { "$id 必须定义 script/file 或 actions" }
            CompiledSkillScript(routine, actions)
        }
        val calculatedAttributeIds = callbackBindings.asSequence()
            .filter { it.ownerDefinitionId.startsWith("attribute:") }
            .mapTo(linkedSetOf()) { it.ownerDefinitionId.removePrefix("attribute:") }
        return PreparedScriptBundle(callbackBindings, skills, calculatedAttributeIds, intervalGates)
    }

    fun commit(bundle: PreparedScriptBundle) {
        committed.set(bundle)
    }

    fun committedCallbacks(): List<RegisteredCallback<*>> = committed.get().callbacks

    fun forget(entityId: UUID) {
        committed.get().intervalGates.forEach { it.forget(entityId) }
    }

    fun maintenance(now: Long) {
        committed.get().intervalGates.forEach { it.pruneExpired(now) }
    }

    fun invokeSkill(skillId: String, context: EntityTriggerContext): Boolean {
        val skill = committed.get().skills[skillId] ?: return false
        val scriptResult = skill.routine?.let { execute(it, context) }
        if (scriptResult == false) return false
        callbacks.execute("skill:$skillId", skill.actions, context)
        return scriptResult as? Boolean ?: true
    }

    fun calculate(
        entity: LivingEntity,
        definition: AttributeDefinition,
        standardValue: Double,
        resolved: Map<String, Double>
    ): Double {
        if (definition.key.value !in committed.get().calculatedAttributeIds) return standardValue
        val trigger = requireNotNull(triggers["symphony:attribute.calculate"])
        val context = EntityTriggerContext(
            transactionId = UUID.randomUUID(),
            self = entity,
            target = null,
            createdAtMillis = System.currentTimeMillis(),
            values = mapOf(
                "attribute" to definition.key.value,
                "standardValue" to standardValue,
                "resolved" to resolved.toMap(),
                "inCombat" to inCombat(entity)
            )
        )
        val dispatcher = dispatchCallback ?: return standardValue
        val result = dispatcher(trigger, context)
        return (result as? Number)?.toDouble() ?: standardValue
    }


    var dispatchCallback: ((SymphonyTrigger<EntityTriggerContext>, EntityTriggerContext) -> Any?)? = null

    private fun genericCallbacks(
        definitions: Map<String, GenericDefinition>,
        kind: CallbackOwnerKind,
        intervalGates: MutableList<CallbackIntervalGate>
    ): List<RegisteredCallback<*>> = definitions.toSortedMap().flatMap { (id, definition) ->
        parseGenericCallbacks(definition.values["callbacks"], "$id.callbacks").map {
            compileCallback(CallbackOwner(kind, id), it, intervalGates)
        }
    }

    private fun compileCallback(
        owner: CallbackOwner,
        definition: CallbackDefinition,
        intervalGates: MutableList<CallbackIntervalGate>
    ): RegisteredCallback<EntityTriggerContext> {
        val trigger = triggers[definition.trigger]
            ?: throw IllegalArgumentException("${owner.id}.${definition.id} 引用了未知或非实体触发器 ${definition.trigger}")
        callbacks.validateConditions("${owner.id}.${definition.id}", definition.conditions)
        callbacks.validateActions("${owner.id}.${definition.id}", definition.actions)
        val routine = definition.script?.let(::compileScript)
        val callbackId = "${owner.kind.name.lowercase()}:${owner.id}${owner.qualifier?.let { ":$it" }.orEmpty()}#${definition.id}"
        val intervalTicks = (definition.metadata["interval"] as? Number)?.toLong()?.also {
            require(it > 0L) { "$callbackId.interval 必须是正整数游戏刻" }
        }
        val intervalGate = intervalTicks?.let { CallbackIntervalGate(Math.multiplyExact(it, 50L)) }
            ?.also(intervalGates::add)
        return RegisteredCallback(
            id = callbackId,
            triggerId = trigger.id,
            priority = definition.priority,
            ownerDefinitionId = "${owner.kind.name.lowercase()}:${owner.id}",
            callback = TriggerCallback { original ->
                val variables = activationResolver.variables(owner, original) ?: return@TriggerCallback null
                val runtimeValues = if ("inCombat" in original.values) emptyMap() else mapOf("inCombat" to inCombat(original.self))
                val context = if (variables.isEmpty() && runtimeValues.isEmpty()) original
                    else original.copy(values = original.values + runtimeValues + variables)
                if (!matchesWhen(definition.metadata["when"], context)) return@TriggerCallback null
                if (intervalTicks != null && !intervalGate!!.tryAcquire(original.self.uniqueId, original.createdAtMillis)) {
                    return@TriggerCallback null
                }
                if (!callbacks.test(callbackId, definition.conditions, context)) return@TriggerCallback null
                val result = routine?.let { execute(it, context) }
                callbacks.execute(callbackId, definition.actions, context)
                result
            }
        )
    }

    private fun compileScript(definition: ScriptDefinition): AriaCompiledRoutine {
        val source = definition.inline ?: readScript(requireNotNull(definition.file), definition.id)
        require(source.toByteArray(StandardCharsets.UTF_8).size <= MAX_SCRIPT_BYTES) {
            "${definition.id} 超过 $MAX_SCRIPT_BYTES 字节限制"
        }
        return scriptCompiler(definition.id, source)
    }

    private fun readScript(relative: String, id: String): String {
        val candidate = scriptsRoot.resolve(relative).normalize()
        require(candidate.startsWith(scriptsRoot.normalize())) { "$id 的脚本路径越出 scripts 目录" }
        require(candidate.fileName.toString().endsWith(".aria", true)) { "$id 的外部脚本必须使用 .aria 扩展名" }
        require(Files.isRegularFile(candidate)) { "$id 引用的脚本不存在: $relative" }
        require(Files.size(candidate) <= MAX_SCRIPT_BYTES) { "$id 的脚本超过 $MAX_SCRIPT_BYTES 字节限制" }
        return Files.readString(candidate, StandardCharsets.UTF_8)
    }

    private fun execute(routine: AriaCompiledRoutine, triggerContext: EntityTriggerContext): Any? {
        val started = System.nanoTime()
        val context = Aria.createContext()
        @Suppress("UNCHECKED_CAST")
        val resolved = triggerContext.values["resolved"] as? Map<String, Double> ?: emptyMap()
        context.self = ObjectValue(JavaObjectMirror(
            ScriptSelfFacade(triggerContext.self, attributes, damageService, triggerContext.transactionId, resolved)
        ))
        context.pushScope()
        context.getScopeVariable(VariableKey.of("ctx")).setValue(ObjectValue(JavaObjectMirror(ScriptContextFacade(triggerContext))))
        return try {
            unwrap(routine.execute(context))
        } finally {
            val elapsedMillis = (System.nanoTime() - started) / 1_000_000.0
            if (elapsedMillis >= slowWarningMillis) {
                BlinkLog.warn(SymphonyRuntime.language().text(
                    "console.slow-callback",
                    "routine" to routine.name,
                    "millis" to "%.3f".format(elapsedMillis)
                ))
            }
            context.popScope()
        }
    }

    private fun unwrap(value: IValue<*>): Any? = when (val raw = value.jvmValue()) {
        is JavaObjectMirror -> raw.javaObject
        else -> raw
    }

    private fun matchesWhen(raw: Any?, context: EntityTriggerContext): Boolean {
        val whenMap = raw as? Map<*, *> ?: return true
        val role = whenMap["role"]?.toString()
        if (role != null) {
            val expected = when (role) {
                "attacker" -> context.values["attacker"]
                "victim" -> context.values["victim"]
                "self" -> context.self
                else -> return false
            }
            if (expected != context.self) return false
        }
        val channels = (whenMap["channels"] as? Collection<*>)?.map { it.toString() }?.toSet()
        if (!channels.isNullOrEmpty()) {
            val actual = (context.values["channels"] as? Collection<*>)?.map { it.toString() }?.toSet().orEmpty()
            if (actual.intersect(channels).isEmpty()) return false
        }
        val inCombat = whenMap["in_combat"] as? Boolean
        if (inCombat != null && context.values["inCombat"] != inCombat) return false
        return true
    }

    private fun parseGenericCallbacks(raw: Any?, path: String): List<CallbackDefinition> {
        val root = raw as? Map<*, *> ?: return emptyList()
        return root.entries.sortedBy { it.key.toString() }.map { (rawId, rawValue) ->
            val id = rawId.toString()
            val values = stringMap(rawValue, "$path.$id")
            val trigger = values["trigger"]?.toString()
                ?: throw IllegalArgumentException("$path.$id.trigger 缺失")
            val inline = values["script"] as? String
            val file = values["file"] as? String
            require(inline == null || file == null) { "$path.$id 不能同时定义 script 与 file" }
            val known = setOf("trigger", "priority", "script", "file", "conditions", "actions", "when", "interval")
            require((values.keys - known).isEmpty()) { "$path.$id 包含未知字段 ${(values.keys - known).sorted()}" }
            CallbackDefinition(
                id = id,
                trigger = if (':' in trigger) trigger else "symphony:$trigger",
                priority = (values["priority"] as? Number)?.toInt() ?: 0,
                script = if (inline != null || file != null) ScriptDefinition("$path.$id", inline, file) else null,
                conditions = mapList(values["conditions"], "$path.$id.conditions"),
                actions = mapList(values["actions"], "$path.$id.actions"),
                metadata = buildMap {
                    values["when"]?.let { put("when", stringMap(it, "$path.$id.when")) }
                    values["interval"]?.let { put("interval", it) }
                }
            )
        }
    }

    private fun mapList(raw: Any?, path: String): List<Map<String, Any?>> = when (raw) {
        null -> emptyList()
        is List<*> -> raw.mapIndexed { index, value -> stringMap(value, "$path[$index]") }
        else -> throw IllegalArgumentException("$path 必须是列表")
    }

    private fun stringMap(raw: Any?, path: String): Map<String, Any?> {
        val map = raw as? Map<*, *> ?: throw IllegalArgumentException("$path 必须是 YAML 映射")
        return map.entries.associate { (key, value) -> key.toString() to value }
    }

    companion object {
        private const val MAX_SCRIPT_BYTES = 1_048_576L
    }
}
