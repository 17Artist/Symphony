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

package priv.seventeen.artist.symphony.api.event

import org.bukkit.NamespacedKey
import org.bukkit.entity.LivingEntity
import org.bukkit.entity.Player
import org.bukkit.event.Event
import org.bukkit.event.HandlerList
import org.bukkit.inventory.ItemStack
import priv.seventeen.artist.symphony.api.attribute.AttributeModifier
import priv.seventeen.artist.symphony.api.attribute.AttributeSourceKey
import priv.seventeen.artist.symphony.api.level.LevelSnapshot

class AffixApplyEvent(val player: Player, val item: ItemStack, val affix: NamespacedKey) : CancellableSymphonyEvent() {
    override fun getHandlers() = HANDLERS
    companion object { @JvmField val HANDLERS = HandlerList(); @JvmStatic fun getHandlerList() = HANDLERS }
}

class AffixRemoveEvent(val player: Player, val item: ItemStack, val affix: NamespacedKey) : CancellableSymphonyEvent() {
    override fun getHandlers() = HANDLERS
    companion object { @JvmField val HANDLERS = HandlerList(); @JvmStatic fun getHandlerList() = HANDLERS }
}

class AffixTriggerEvent(val entity: LivingEntity, val affix: NamespacedKey, val callbackId: String) : Event() {
    override fun getHandlers() = HANDLERS
    companion object { @JvmField val HANDLERS = HandlerList(); @JvmStatic fun getHandlerList() = HANDLERS }
}

class BuffApplyEvent(val entity: LivingEntity, val key: String, val modifier: AttributeModifier) : CancellableSymphonyEvent() {
    override fun getHandlers() = HANDLERS
    companion object { @JvmField val HANDLERS = HandlerList(); @JvmStatic fun getHandlerList() = HANDLERS }
}

class BuffExpireEvent(val entity: LivingEntity, val key: String) : Event() {
    override fun getHandlers() = HANDLERS
    companion object { @JvmField val HANDLERS = HandlerList(); @JvmStatic fun getHandlerList() = HANDLERS }
}

class SkillPrepareEvent(
    val caster: LivingEntity,
    val skill: NamespacedKey,
    val target: LivingEntity?,
    val source: AttributeSourceKey? = null,
    val level: Int = 1,
    val itemId: String? = null
) : CancellableSymphonyEvent() {
    override fun getHandlers() = HANDLERS
    companion object { @JvmField val HANDLERS = HandlerList(); @JvmStatic fun getHandlerList() = HANDLERS }
}

class SkillConfirmedEvent(
    val caster: LivingEntity,
    val skill: NamespacedKey,
    val target: LivingEntity?,
    val source: AttributeSourceKey? = null,
    val level: Int = 1,
    val itemId: String? = null
) : Event() {
    override fun getHandlers() = HANDLERS
    companion object { @JvmField val HANDLERS = HandlerList(); @JvmStatic fun getHandlerList() = HANDLERS }
}

class LevelChangeEvent(
    val entity: LivingEntity,
    val previous: LevelSnapshot?,
    val current: LevelSnapshot?,
    val reason: String
) : Event() {
    override fun getHandlers() = HANDLERS
    companion object { @JvmField val HANDLERS = HandlerList(); @JvmStatic fun getHandlerList() = HANDLERS }
}

class GemInsertEvent(
    val player: Player,
    val item: ItemStack,
    val gem: NamespacedKey,
    val slot: Int = -1,
    val category: String? = null
) : CancellableSymphonyEvent() {
    override fun getHandlers() = HANDLERS
    companion object { @JvmField val HANDLERS = HandlerList(); @JvmStatic fun getHandlerList() = HANDLERS }
}

class GemRemoveEvent(val player: Player, val item: ItemStack, val gem: NamespacedKey, val slot: Int = -1) : CancellableSymphonyEvent() {
    override fun getHandlers() = HANDLERS
    companion object { @JvmField val HANDLERS = HandlerList(); @JvmStatic fun getHandlerList() = HANDLERS }
}

class SocketDrillEvent(
    val player: Player,
    val item: ItemStack,
    val tool: NamespacedKey,
    val slot: Int,
    val accepts: Set<String>
) : CancellableSymphonyEvent() {
    override fun getHandlers() = HANDLERS
    companion object { @JvmField val HANDLERS = HandlerList(); @JvmStatic fun getHandlerList() = HANDLERS }
}

class EnhanceEvent(val player: Player, val item: ItemStack, val fromLevel: Int, val targetLevel: Int) : CancellableSymphonyEvent() {
    override fun getHandlers() = HANDLERS
    companion object { @JvmField val HANDLERS = HandlerList(); @JvmStatic fun getHandlerList() = HANDLERS }
}
