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
import org.bukkit.entity.LivingEntity
import priv.seventeen.artist.symphony.api.trigger.ResultPolicy
import priv.seventeen.artist.symphony.api.trigger.SymphonyTrigger
import priv.seventeen.artist.symphony.api.trigger.TriggerContext
import priv.seventeen.artist.symphony.api.trigger.TriggerPhase
import java.util.UUID
import kotlin.reflect.KClass

data class EntityTriggerContext(
    override val transactionId: UUID,
    override val self: LivingEntity,
    override val target: LivingEntity?,
    override val createdAtMillis: Long,
    val values: Map<String, Any?> = emptyMap()
) : TriggerContext

abstract class EntityTrigger(
    path: String,
    final override val phase: TriggerPhase,
    final override val resultPolicy: ResultPolicy = ResultPolicy.IGNORE,
    final override val cancellable: Boolean = false
) : SymphonyTrigger<EntityTriggerContext> {
    final override val id: NamespacedKey = NamespacedKey("symphony", path)
    final override val contextType: KClass<EntityTriggerContext> = EntityTriggerContext::class
}

object AttributeCalculateTrigger : EntityTrigger("attribute.calculate", TriggerPhase.PREPARE, ResultPolicy.FINITE_NUMBER)
object CombatAttackPrepareTrigger : EntityTrigger("combat.attack_prepare", TriggerPhase.PREPARE, ResultPolicy.DAMAGE_ADJUSTMENT, true)
object CombatAttackConfirmedTrigger : EntityTrigger("combat.attack_confirmed", TriggerPhase.CONFIRMED)
object CombatMeleeTrigger : EntityTrigger("combat.melee", TriggerPhase.APPLY)
object CombatRangedTrigger : EntityTrigger("combat.ranged", TriggerPhase.APPLY)
object CombatCriticalTrigger : EntityTrigger("combat.critical", TriggerPhase.APPLY)
object CombatDamageDealtTrigger : EntityTrigger("combat.damage_dealt", TriggerPhase.CONFIRMED)
object CombatDamageTakenTrigger : EntityTrigger("combat.damage_taken", TriggerPhase.CONFIRMED)
object CombatDamageConfirmedTrigger : EntityTrigger("combat.damage_confirmed", TriggerPhase.CONFIRMED)
object CombatBlockConfirmedTrigger : EntityTrigger("combat.block_confirmed", TriggerPhase.CONFIRMED)
object CombatDodgeTrigger : EntityTrigger("combat.dodge", TriggerPhase.CONFIRMED)
object CombatKillTrigger : EntityTrigger("combat.kill", TriggerPhase.CONFIRMED)
object CombatDeathTrigger : EntityTrigger("combat.death", TriggerPhase.CONFIRMED)
object CombatLowHealthTrigger : EntityTrigger("combat.low_health", TriggerPhase.CONFIRMED)
object CombatEnterTrigger : EntityTrigger("combat.enter", TriggerPhase.CONFIRMED)
object CombatLeaveTrigger : EntityTrigger("combat.leave", TriggerPhase.CONFIRMED)
object EntityMoveTrigger : EntityTrigger("entity.move", TriggerPhase.NOTIFY)
object EntityJumpTrigger : EntityTrigger("entity.jump", TriggerPhase.NOTIFY)
object EntitySneakTrigger : EntityTrigger("entity.sneak", TriggerPhase.NOTIFY)
object EntitySprintTrigger : EntityTrigger("entity.sprint", TriggerPhase.NOTIFY)
object EntityTimerTrigger : EntityTrigger("entity.timer", TriggerPhase.NOTIFY)
object ItemInteractTrigger : EntityTrigger("item.interact", TriggerPhase.PREPARE, ResultPolicy.BOOLEAN, true)
object ItemLeftClickTrigger : EntityTrigger("item.left_click", TriggerPhase.PREPARE, ResultPolicy.BOOLEAN, true)
object ItemRightClickTrigger : EntityTrigger("item.right_click", TriggerPhase.PREPARE, ResultPolicy.BOOLEAN, true)
object ItemEquipTrigger : EntityTrigger("item.equip", TriggerPhase.CONFIRMED)
object ItemUnequipTrigger : EntityTrigger("item.unequip", TriggerPhase.CONFIRMED)
object ItemHeldChangeTrigger : EntityTrigger("item.held_change", TriggerPhase.CONFIRMED)
object ItemConsumeTrigger : EntityTrigger("item.consume", TriggerPhase.CONFIRMED)
object ItemDamageTrigger : EntityTrigger("item.damage", TriggerPhase.CONFIRMED)
object PlayerJoinTrigger : EntityTrigger("player.join", TriggerPhase.NOTIFY)
object PlayerQuitTrigger : EntityTrigger("player.quit", TriggerPhase.NOTIFY)
object PlayerRespawnTrigger : EntityTrigger("player.respawn", TriggerPhase.NOTIFY)
object SkillCastTrigger : EntityTrigger("skill.cast", TriggerPhase.PREPARE, ResultPolicy.BOOLEAN, true)
object SkillHitTrigger : EntityTrigger("skill.hit", TriggerPhase.CONFIRMED)
object PlayerLevelUpTrigger : EntityTrigger("player.level_up", TriggerPhase.CONFIRMED)
object StatusTickTrigger : EntityTrigger("status.tick", TriggerPhase.CONFIRMED)

object BuiltInTriggers {
    val all: List<EntityTrigger> = listOf(
        AttributeCalculateTrigger,
        CombatAttackPrepareTrigger, CombatAttackConfirmedTrigger, CombatMeleeTrigger, CombatRangedTrigger,
        CombatCriticalTrigger, CombatDamageDealtTrigger, CombatDamageTakenTrigger, CombatDamageConfirmedTrigger,
        CombatBlockConfirmedTrigger, CombatDodgeTrigger, CombatKillTrigger, CombatDeathTrigger, CombatLowHealthTrigger,
        CombatEnterTrigger, CombatLeaveTrigger,
        EntityMoveTrigger, EntityJumpTrigger, EntitySneakTrigger, EntitySprintTrigger, EntityTimerTrigger,
        ItemInteractTrigger, ItemLeftClickTrigger, ItemRightClickTrigger, ItemEquipTrigger, ItemUnequipTrigger,
        ItemHeldChangeTrigger, ItemConsumeTrigger, ItemDamageTrigger,
        PlayerJoinTrigger, PlayerQuitTrigger, PlayerRespawnTrigger,
        SkillCastTrigger, SkillHitTrigger, PlayerLevelUpTrigger, StatusTickTrigger
    )
}
