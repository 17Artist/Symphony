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

import org.bukkit.attribute.Attribute
import org.bukkit.entity.LivingEntity
import org.bukkit.entity.Player
import priv.seventeen.artist.aria.interop.JavaObjectMirror
import priv.seventeen.artist.aria.value.IValue
import priv.seventeen.artist.aria.value.NoneValue
import priv.seventeen.artist.aria.value.ObjectValue
import priv.seventeen.artist.symphony.api.attribute.AttributeKey
import priv.seventeen.artist.symphony.api.attribute.AttributeService
import priv.seventeen.artist.symphony.api.damage.DamageChannelAmount
import priv.seventeen.artist.symphony.api.damage.DamageRequest
import priv.seventeen.artist.symphony.api.damage.DamageResult
import priv.seventeen.artist.symphony.api.damage.DamageService
import priv.seventeen.artist.symphony.engine.trigger.EntityTriggerContext
import java.util.UUID
import kotlin.math.min

/** 面向受信任管理员脚本的 Aria 门面；有意保留对原始 Bukkit 实体的访问能力。 */
class ScriptSelfFacade(
    private val entity: LivingEntity,
    private val attributes: AttributeService,
    private val damageService: DamageService,
    private val parentTransactionId: UUID?,
    private val resolvedAttributes: Map<String, Double> = emptyMap()
) {
    fun getName(): String = entity.name
    fun getUniqueId(): String = entity.uniqueId.toString()
    fun getHealth(): Double = entity.health
    fun getBukkit(): IValue<*> = mirror(entity)
    fun isPlayer(): Boolean = entity is Player

    fun getAttribute(rawKey: String): Double {
        val key = attributeKey(rawKey)
        return resolvedAttributes[key.value] ?: attributes.value(entity, key)
    }

    fun sendMessage(message: String) {
        entity.sendMessage(message)
    }

    fun heal(amount: Double): Double {
        require(amount.isFinite() && amount >= 0.0) { "治疗量必须是有限非负数" }
        val maximum = entity.getAttribute(Attribute.GENERIC_MAX_HEALTH)?.value ?: entity.health
        val before = entity.health
        entity.health = min(maximum, before + amount)
        return entity.health - before
    }

    fun damage(target: LivingEntity, amount: Double, channel: String): DamageResult {
        require(amount.isFinite() && amount >= 0.0) { "伤害量必须是有限非负数" }
        return damageService.damage(
            DamageRequest(
                attacker = entity,
                victim = target,
                channels = listOf(DamageChannelAmount(channel, amount)),
                cause = "aria:callback",
                parentTransactionId = parentTransactionId
            )
        )
    }

    private fun attributeKey(raw: String): AttributeKey =
        AttributeKey(if (':' in raw) raw else "symphony:$raw")
}

/** 以 Aria 裸变量 `ctx` 暴露的只读强类型上下文。 */
class ScriptContextFacade(private val context: EntityTriggerContext) {
    fun getTransactionId(): String = context.transactionId.toString()
    fun getCreatedAtMillis(): Long = context.createdAtMillis
    fun getTarget(): IValue<*> = mirrorOrNone(context.target)
    fun getAttacker(): IValue<*> = mirrorOrNone(context.values["attacker"] as? LivingEntity)
    fun getVictim(): IValue<*> = mirrorOrNone(context.values["victim"] as? LivingEntity)
    fun getStandardValue(): Double = (context.values["standardValue"] as? Number)?.toDouble() ?: 0.0
    fun getAttribute(): String? = context.values["attribute"] as? String
    fun getChannel(): String? = context.values["channel"] as? String
    fun getChannels(): List<String> = (context.values["channels"] as? Collection<*>)?.map { it.toString() }.orEmpty()
    fun getAmount(): Double = (context.values["amount"] as? Number)?.toDouble() ?: 0.0
    fun getLevel(): Int = (context.values["level"] as? Number)?.toInt() ?: 0
    fun getStacks(): Int = (context.values["stacks"] as? Number)?.toInt() ?: 0
    fun get(key: String): Any? = context.values[key]
    fun values(): Map<String, Any?> = context.values.toMap()
}

internal fun mirror(value: Any): ObjectValue<JavaObjectMirror> = ObjectValue(JavaObjectMirror(value))
internal fun mirrorOrNone(value: Any?): IValue<*> = value?.let(::mirror) ?: NoneValue.NONE
