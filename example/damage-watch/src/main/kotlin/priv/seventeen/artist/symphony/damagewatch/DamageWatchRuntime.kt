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

package priv.seventeen.artist.symphony.damagewatch

import net.md_5.bungee.api.ChatMessageType
import net.md_5.bungee.api.chat.TextComponent
import org.bukkit.Bukkit
import org.bukkit.entity.LivingEntity
import org.bukkit.entity.Player
import org.bukkit.plugin.java.JavaPlugin
import priv.seventeen.artist.blink.BlinkLog
import priv.seventeen.artist.symphony.api.damage.DamageAttributeUse
import priv.seventeen.artist.symphony.api.damage.DamageChannelResult
import priv.seventeen.artist.symphony.api.event.SymphonyDamageConfirmedEvent
import priv.seventeen.artist.symphony.api.event.SymphonyDamageEvent
import priv.seventeen.artist.symphony.api.event.SymphonyHitCheckEvent
import priv.seventeen.artist.symphony.api.service.SymphonyApi
import priv.seventeen.artist.symphony.damagewatch.config.DamageWatchSettings
import priv.seventeen.artist.symphony.damagewatch.text.Messages
import java.math.BigDecimal
import java.math.RoundingMode
import java.util.UUID

object DamageWatchRuntime {
    private lateinit var plugin: JavaPlugin
    private lateinit var api: SymphonyApi
    private lateinit var settings: DamageWatchSettings
    private lateinit var messages: Messages
    private val pendingAudiences = object : LinkedHashMap<UUID, Set<UUID>>() {
        override fun removeEldestEntry(eldest: MutableMap.MutableEntry<UUID, Set<UUID>>): Boolean = size > 256
    }
    private var enabled = false

    fun load(plugin: JavaPlugin) {
        this.plugin = plugin
        saveDefault("config.yml")
        saveDefault("language.yml")
    }

    fun enable() {
        api = Bukkit.getServicesManager().load(SymphonyApi::class.java)
            ?: error("SymphonyApi 服务不可用")
        settings = DamageWatchSettings.load(plugin)
        messages = Messages(plugin.dataFolder.resolve("language.yml"))
        enabled = true
        BlinkLog.success(messages.text("console.enabled"))
    }

    fun disable() {
        if (!enabled) return
        pendingAudiences.clear()
        enabled = false
        BlinkLog.info(messages.text("console.disabled"))
    }

    fun observe(event: SymphonyDamageEvent) {
        if (!enabled) return
        val recipients = recipients(event.request.attacker, event.request.victim)
        val recipientIds = recipients.mapTo(linkedSetOf(), Player::getUniqueId)
        pendingAudiences[event.transactionId] = recipientIds

        val channels = event.breakdown.channels.take(settings.maximumChannels)
        val attributeUses = event.breakdown.attributes
            .asSequence()
            .filter { settings.includeInactiveAttributes || it.activated }
            .take(settings.maximumAttributes)
            .toList()
        val lines = buildList {
            add(messages.text("resolved.header"))
            add(messages.text("resolved.transaction", "transaction" to short(event.transactionId), "cause" to event.request.cause))
            add(messages.text(
                "resolved.participants",
                "attacker" to entityName(event.request.attacker),
                "victim" to entityName(event.request.victim)
            ))
            add(messages.text(
                "resolved.summary",
                "damage" to number(event.finalDamage),
                "hit" to yesNo(event.hit),
                "critical" to yesNo(event.critical),
                "cancelled" to yesNo(event.isCancelled)
            ))
            add(messages.text(
                "resolved.relations",
                "advantaged" to number(event.advantagedDamage),
                "neutral" to number(event.neutralDamage),
                "disadvantaged" to number(event.disadvantagedDamage)
            ))
            event.request.parentTransactionId?.let {
                add(messages.text("resolved.parent", "parent" to short(it)))
            }
            if (settings.includeMetadata && event.request.metadata.isNotEmpty()) {
                add(messages.text("resolved.metadata-header"))
                event.request.metadata.toSortedMap().forEach { (key, value) ->
                    add(messages.text("resolved.metadata", "key" to key, "value" to value))
                }
            }
            add(messages.text("resolved.channel-header"))
            channels.forEach { channel ->
                add(channelLine(channel))
                if (channel.reactions.isNotEmpty()) {
                    add(messages.text("resolved.reactions", "reactions" to channel.reactions.sorted().joinToString()))
                }
            }
            if (event.breakdown.channels.size > channels.size) {
                add(messages.text("resolved.truncated", "count" to (event.breakdown.channels.size - channels.size)))
            }
            add(messages.text("resolved.attribute-header"))
            attributeUses.forEach { add(attributeLine(it)) }
            val visibleAttributeCount = event.breakdown.attributes.count { settings.includeInactiveAttributes || it.activated }
            if (visibleAttributeCount > attributeUses.size) {
                add(messages.text("resolved.truncated", "count" to (visibleAttributeCount - attributeUses.size)))
            }
            if (attributeUses.isEmpty()) add(messages.text("common.none"))
            add(messages.text("resolved.footer"))
        }
        publishDetails(recipients, lines)
        publishActionBar(
            recipients,
            messages.text(
                "action-bar.resolved",
                "damage" to number(event.finalDamage),
                "channels" to channels.joinToString { channelName(it.channel) }
            )
        )
    }

    fun hitCheck(event: SymphonyHitCheckEvent) {
        if (!enabled || event.hit || !settings.missMessage) return
        val recipients = recipients(event.request.attacker, event.request.victim)
        val lines = listOf(
            messages.text("miss.header"),
            messages.text("miss.transaction", "transaction" to short(event.transactionId), "cause" to event.request.cause),
            messages.text(
                "miss.participants",
                "attacker" to entityName(event.request.attacker),
                "victim" to entityName(event.request.victim)
            ),
            messages.text(
                "miss.summary",
                "accuracy" to number(event.accuracy),
                "dodge" to number(event.dodge),
                "chance" to percent(event.dodgeChance),
                "roll" to number(event.roll)
            )
        )
        publishDetails(recipients, lines)
        publishActionBar(recipients, messages.text("action-bar.missed"))
    }

    fun confirm(event: SymphonyDamageConfirmedEvent) {
        if (!enabled) return
        val result = event.result
        val recipientIds = pendingAudiences.remove(result.transactionId).orEmpty()
        val recipients = recipientIds.mapNotNull(Bukkit::getPlayer)
        val channelSummary = result.breakdown.channels.joinToString { channel ->
            "${channelName(channel.channel)} ${number(channel.finalAmount)}"
        }.ifBlank { messages.text("common.none") }
        if (settings.confirmationMessage) {
            val line = messages.text(
                "confirmed.chat",
                "transaction" to short(result.transactionId),
                "damage" to number(result.finalDamage),
                "channels" to channelSummary
            )
            if (settings.chatDetails) recipients.forEach { it.sendMessage(line) }
            if (settings.notifyConsole) plugin.server.consoleSender.sendMessage(line)
        }
        publishActionBar(recipients, messages.text("action-bar.confirmed", "damage" to number(result.finalDamage)))
    }

    private fun recipients(attacker: LivingEntity?, victim: LivingEntity): Set<Player> = linkedSetOf<Player>().apply {
        if (settings.notifyAttacker) (attacker as? Player)?.let(::add)
        if (settings.notifyVictim) (victim as? Player)?.let(::add)
    }

    private fun publishDetails(recipients: Set<Player>, lines: List<String>) {
        if (settings.chatDetails) recipients.forEach { player -> lines.forEach(player::sendMessage) }
        if (settings.notifyConsole) lines.forEach(plugin.server.consoleSender::sendMessage)
    }

    private fun publishActionBar(recipients: Collection<Player>, message: String) {
        if (!settings.actionBarSummary) return
        recipients.forEach { it.spigot().sendMessage(ChatMessageType.ACTION_BAR, TextComponent(message)) }
    }

    private fun channelLine(channel: DamageChannelResult): String = messages.text(
        "resolved.channel",
        "name" to channelName(channel.channel),
        "id" to channel.channel,
        "requested" to number(channel.requestedAmount),
        "relation" to messages.optional("relations.${channel.relation.name.lowercase()}", channel.relation.name),
        "related" to number(channel.afterRelationAmount),
        "critical" to number(channel.afterCriticalAmount),
        "mitigated" to number(channel.afterMitigationAmount),
        "final" to number(channel.finalAmount)
    )

    private fun attributeLine(use: DamageAttributeUse): String {
        val definition = api.definitions.attribute(use.attribute)
        return messages.text(
            "resolved.attribute",
            "name" to (definition?.name ?: use.attribute.value),
            "id" to use.attribute.toString(),
            "owner" to messages.optional("owners.${use.owner.name.lowercase()}", use.owner.name),
            "role" to messages.optional("roles.${use.role.name.lowercase()}", use.role.name),
            "value" to number(use.value),
            "channel" to (use.channel?.let(::channelName) ?: messages.text("common.not-applicable")),
            "activated" to yesNo(use.activated)
        )
    }

    private fun channelName(id: String): String = messages.optional("channels.${id.lowercase()}", id)
    private fun entityName(entity: LivingEntity?): String = when (entity) {
        null -> messages.text("common.unknown")
        is Player -> entity.name
        else -> entity.name
    }

    private fun yesNo(value: Boolean): String = messages.text(if (value) "common.affirmative" else "common.negative")
    private fun number(value: Double): String = BigDecimal.valueOf(value)
        .setScale(settings.decimals, RoundingMode.HALF_UP)
        .stripTrailingZeros()
        .toPlainString()
    private fun percent(value: Double): String = BigDecimal.valueOf(value * 100.0)
        .setScale(settings.decimals, RoundingMode.HALF_UP)
        .stripTrailingZeros()
        .toPlainString() + "%"
    private fun short(id: UUID): String = id.toString().substring(0, 8)

    private fun saveDefault(path: String) {
        if (!plugin.dataFolder.resolve(path).isFile) plugin.saveResource(path, false)
    }
}
