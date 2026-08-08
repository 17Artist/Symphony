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

package priv.seventeen.artist.symphony.bukkit.listener

import org.bukkit.entity.LivingEntity
import org.bukkit.entity.Player
import org.bukkit.event.EventPriority
import org.bukkit.event.block.Action
import org.bukkit.event.player.PlayerAnimationEvent
import org.bukkit.event.player.PlayerAnimationType
import org.bukkit.event.player.PlayerChangedWorldEvent
import org.bukkit.event.player.PlayerInteractEntityEvent
import org.bukkit.event.player.PlayerInteractEvent
import org.bukkit.event.player.PlayerItemDamageEvent
import org.bukkit.event.player.PlayerItemConsumeEvent
import org.bukkit.event.player.PlayerItemHeldEvent
import org.bukkit.event.player.PlayerMoveEvent
import org.bukkit.event.player.PlayerTeleportEvent
import org.bukkit.event.player.PlayerToggleSneakEvent
import org.bukkit.event.player.PlayerToggleSprintEvent
import org.bukkit.event.weather.WeatherChangeEvent
import org.bukkit.inventory.EquipmentSlot
import priv.seventeen.artist.blink.event.AutoListener
import priv.seventeen.artist.symphony.bukkit.gameplay.SkillInputResult
import priv.seventeen.artist.symphony.bukkit.gameplay.SkillInputStatus
import priv.seventeen.artist.symphony.bukkit.runtime.SymphonyRuntime
import priv.seventeen.artist.symphony.engine.definition.SkillActivationInput
import priv.seventeen.artist.symphony.engine.trigger.EntityJumpTrigger
import priv.seventeen.artist.symphony.engine.trigger.EntityMoveTrigger
import priv.seventeen.artist.symphony.engine.trigger.EntitySneakTrigger
import priv.seventeen.artist.symphony.engine.trigger.EntitySprintTrigger
import priv.seventeen.artist.symphony.engine.trigger.EntityTrigger
import priv.seventeen.artist.symphony.engine.trigger.EntityTriggerContext
import priv.seventeen.artist.symphony.engine.trigger.ItemConsumeTrigger
import priv.seventeen.artist.symphony.engine.trigger.ItemDamageTrigger
import priv.seventeen.artist.symphony.engine.trigger.ItemHeldChangeTrigger
import priv.seventeen.artist.symphony.engine.trigger.ItemInteractTrigger
import priv.seventeen.artist.symphony.engine.trigger.ItemLeftClickTrigger
import priv.seventeen.artist.symphony.engine.trigger.ItemRightClickTrigger
import java.util.UUID

object MovementAndInteractionListener {
    @JvmStatic
    @AutoListener(priority = EventPriority.MONITOR, ignoreCancelled = true)
    fun onMove(event: PlayerMoveEvent) {
        val to = event.to ?: return
        if (event.from.blockX == to.blockX && event.from.blockY == to.blockY && event.from.blockZ == to.blockZ) return
        SymphonyRuntime.environmentOrNull()?.mark(event.player)
        dispatch(EntityMoveTrigger, event.player, null, mapOf("from" to event.from, "to" to to))
        if (to.y - event.from.y > 0.20 && !event.player.isFlying) {
            dispatch(EntityJumpTrigger, event.player, null, mapOf("from" to event.from, "to" to to))
        }
    }

    @JvmStatic
    @AutoListener(priority = EventPriority.MONITOR, ignoreCancelled = true)
    fun onTeleport(event: PlayerTeleportEvent) {
        SymphonyRuntime.environmentOrNull()?.mark(event.player)
        if (event.to?.world != event.from.world) SymphonyRuntime.epicFightOrNull()?.onWorldChange(event.player)
    }

    @JvmStatic
    @AutoListener(priority = EventPriority.MONITOR)
    fun onWorld(event: PlayerChangedWorldEvent) {
        SymphonyRuntime.environmentOrNull()?.mark(event.player)
        SymphonyRuntime.epicFightOrNull()?.onWorldChange(event.player)
    }

    @JvmStatic
    @AutoListener(priority = EventPriority.MONITOR)
    fun onWeather(event: WeatherChangeEvent) {
        event.world.players.forEach { SymphonyRuntime.environmentOrNull()?.mark(it) }
    }

    @JvmStatic
    @AutoListener(priority = EventPriority.MONITOR, ignoreCancelled = true)
    fun onSneak(event: PlayerToggleSneakEvent) {
        dispatch(EntitySneakTrigger, event.player, null, mapOf("sneaking" to event.isSneaking))
    }

    @JvmStatic
    @AutoListener(priority = EventPriority.MONITOR, ignoreCancelled = true)
    fun onSprint(event: PlayerToggleSprintEvent) {
        dispatch(EntitySprintTrigger, event.player, null, mapOf("sprinting" to event.isSprinting))
    }

    @JvmStatic
    @AutoListener(priority = EventPriority.HIGH, ignoreCancelled = true)
    fun onInteract(event: PlayerInteractEvent) {
        val trigger = when (event.action) {
            Action.LEFT_CLICK_AIR, Action.LEFT_CLICK_BLOCK -> ItemLeftClickTrigger
            Action.RIGHT_CLICK_AIR, Action.RIGHT_CLICK_BLOCK -> ItemRightClickTrigger
            else -> ItemInteractTrigger
        }
        val values = mapOf("action" to event.action.name, "item" to event.item, "block" to event.clickedBlock)
        val specific = dispatch(trigger, event.player, null, values)
        val general = if (trigger === ItemInteractTrigger) false else dispatch(ItemInteractTrigger, event.player, null, values)
        val skill = if (event.action == Action.RIGHT_CLICK_AIR || event.action == Action.RIGHT_CLICK_BLOCK) {
            activateSkill(event.player, null, event.hand ?: EquipmentSlot.HAND)
        } else SkillInputResult(SkillInputStatus.NONE)
        if (specific || general || skill.cancelEvent) event.isCancelled = true
    }

    @JvmStatic
    @AutoListener(priority = EventPriority.HIGH, ignoreCancelled = true)
    fun onEntityInteract(event: PlayerInteractEntityEvent) {
        val target = event.rightClicked as? LivingEntity
        val specific = dispatch(ItemRightClickTrigger, event.player, target, mapOf("entity" to event.rightClicked))
        val general = dispatch(ItemInteractTrigger, event.player, target, mapOf("entity" to event.rightClicked))
        val skill = activateSkill(event.player, target, event.hand)
        if (specific || general || skill.cancelEvent) event.isCancelled = true
    }

    @JvmStatic
    @AutoListener(priority = EventPriority.HIGH)
    fun onSwing(event: PlayerAnimationEvent) {
        if (event.animationType == PlayerAnimationType.ARM_SWING &&
            dispatch(ItemLeftClickTrigger, event.player, null, mapOf("animation" to event.animationType.name))) event.isCancelled = true
    }

    @JvmStatic
    @AutoListener(priority = EventPriority.MONITOR, ignoreCancelled = true)
    fun onHeld(event: PlayerItemHeldEvent) {
        dispatch(
            ItemHeldChangeTrigger,
            event.player,
            null,
            mapOf("previousSlot" to event.previousSlot, "newSlot" to event.newSlot)
        )
    }

    @JvmStatic
    @AutoListener(priority = EventPriority.MONITOR, ignoreCancelled = true)
    fun onConsume(event: PlayerItemConsumeEvent) {
        dispatch(ItemConsumeTrigger, event.player, null, mapOf("item" to event.item))
    }

    @JvmStatic
    @AutoListener(priority = EventPriority.MONITOR, ignoreCancelled = true)
    fun onDamageItem(event: PlayerItemDamageEvent) {
        dispatch(ItemDamageTrigger, event.player, null, mapOf("item" to event.item, "damage" to event.damage))
    }

    private fun dispatch(
        trigger: EntityTrigger,
        self: LivingEntity,
        target: LivingEntity?,
        values: Map<String, Any?>
    ): Boolean = SymphonyRuntime.triggerOrNull()?.dispatch(
        trigger,
        EntityTriggerContext(UUID.randomUUID(), self, target, System.currentTimeMillis(), values)
    )?.cancelled == true

    private fun activateSkill(player: Player, target: LivingEntity?, hand: EquipmentSlot): SkillInputResult {
        val input = if (player.isSneaking) SkillActivationInput.SNEAK_RIGHT_CLICK else SkillActivationInput.RIGHT_CLICK
        val result = SymphonyRuntime.skillOrNull()?.activate(player, input, hand, target)
            ?: return SkillInputResult(SkillInputStatus.NONE)
        val language = SymphonyRuntime.languageOrNull() ?: return result
        when (result.status) {
            SkillInputStatus.NONE -> Unit
            SkillInputStatus.SUCCESS -> player.sendMessage(language.text("skill-cast.success", "skill" to result.skillName))
            SkillInputStatus.COOLDOWN -> player.sendMessage(language.text(
                "skill-cast.cooldown",
                "skill" to result.skillName,
                "seconds" to java.math.BigDecimal.valueOf(result.remainingMillis)
                    .divide(java.math.BigDecimal.valueOf(1000L), 1, java.math.RoundingMode.UP)
                    .stripTrailingZeros().toPlainString()
            ))
            SkillInputStatus.NO_TARGET -> player.sendMessage(language.text("skill-cast.no-target", "skill" to result.skillName))
            SkillInputStatus.REJECTED -> player.sendMessage(language.text("skill-cast.failed", "skill" to result.skillName))
        }
        return result
    }
}
