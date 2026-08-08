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

package priv.seventeen.artist.symphony.api.service

import org.bukkit.NamespacedKey
import org.bukkit.entity.LivingEntity
import org.bukkit.inventory.ItemStack
import org.bukkit.plugin.Plugin
import priv.seventeen.artist.symphony.api.attribute.AttributeDefinition
import priv.seventeen.artist.symphony.api.attribute.AttributeKey
import priv.seventeen.artist.symphony.api.attribute.AttributeService
import priv.seventeen.artist.symphony.api.attribute.AttributeSourceKey
import priv.seventeen.artist.symphony.api.damage.DamageService
import priv.seventeen.artist.symphony.api.level.LevelService
import priv.seventeen.artist.symphony.api.power.CombatPowerService
import priv.seventeen.artist.symphony.api.registration.RegistrationHandle
import priv.seventeen.artist.symphony.api.source.AttributeSourceService
import priv.seventeen.artist.symphony.api.trigger.TriggerService

interface DefinitionService {
    val revision: Long
    fun attribute(key: AttributeKey): AttributeDefinition?
    fun attributes(): Map<AttributeKey, AttributeDefinition>
    fun sets(): Map<NamespacedKey, SetDefinitionView>
    fun passives(): Set<PassiveDefinitionView>
    fun registerAttribute(
        owner: Plugin,
        definition: AttributeDefinition,
        priority: Int = 0
    ): RegistrationHandle
}

data class SetBonusView(
    val threshold: Int,
    val name: String,
    val description: List<String>
)

data class SetDefinitionView(
    val key: NamespacedKey,
    val name: String,
    val pieces: Map<String, String>,
    val bonuses: List<SetBonusView>
) {
    val thresholds: List<Int> get() = bonuses.map(SetBonusView::threshold)
}
data class PassiveDefinitionView(val key: NamespacedKey, val kind: String, val name: String)

interface ItemAttributeService {
    fun inspect(item: ItemStack): ItemInspection
    fun rebuild(item: ItemStack, viewer: LivingEntity? = null): ItemMutationOutcome
}

data class ItemInspection(
    val overtureItemId: String?,
    val instanceId: String?,
    val attributes: Map<AttributeKey, Double>,
    val setIds: Set<NamespacedKey>,
    val diagnostics: List<String>,
    val affixes: List<ItemFeatureView> = emptyList(),
    val gems: List<ItemFeatureView> = emptyList(),
    val skills: List<ItemFeatureView> = emptyList(),
    val sockets: SocketView? = null,
    val enhancementLevel: Int = 0,
    val instanceRevision: Int = 0,
    val symphonyDataPresent: Boolean = false,
    val offhandAllowed: Boolean? = null,
    val offhandAttributeScale: Double? = null
) {
    val isOvertureItem: Boolean get() = overtureItemId != null

    /** 仅当物品含有至少一个由 Symphony 管理的组件或实例时返回 true。 */
    val isSymphonyItem: Boolean
        get() = symphonyDataPresent || instanceId != null || attributes.isNotEmpty() || setIds.isNotEmpty() ||
            affixes.isNotEmpty() || gems.isNotEmpty() || skills.isNotEmpty() || sockets != null || enhancementLevel > 0

    /**
     * 物品可以放入 Symphony 工坊时返回 true。
     *
     * 由物品定义创建的物品即使尚无实例 UUID，也可以进入工坊。首次成功修改时会以原子方式
     * 创建实例标识与修订号；仅打开只读预览不得改写物品。
     */
    val supportsWorkshops: Boolean
        get() = instanceId != null || attributes.isNotEmpty() || setIds.isNotEmpty() ||
            affixes.isNotEmpty() || gems.isNotEmpty() || skills.isNotEmpty() || sockets != null || enhancementLevel > 0
}

data class ItemFeatureView(
    val key: NamespacedKey,
    val level: Int,
    val parameters: Map<String, Double>,
    val tags: Set<String>,
    val locked: Boolean = false,
    val category: String? = null
)

data class SocketSlotView(
    val index: Int,
    val accepts: Set<String>,
    val unlocked: Boolean,
    val unlockAtEnhancement: Int?,
    val gem: ItemFeatureView? = null,
    val addedByTool: NamespacedKey? = null
)

data class SocketView(
    val slots: List<SocketSlotView>,
    val maximumExtraSlots: Int
) {
    val capacity: Int get() = slots.size
    val unlocked: Int get() = slots.count(SocketSlotView::unlocked)
    val used: Int get() = slots.count { it.gem != null }
    val accepts: Set<String> get() = slots.flatMapTo(linkedSetOf(), SocketSlotView::accepts)
    val lockedSlots: Map<Int, Int> get() = slots.mapNotNull { slot ->
        slot.unlockAtEnhancement?.let { slot.index to it }
    }.toMap()
}

sealed interface ItemMutationOutcome {
    data class Success(val itemStack: ItemStack) : ItemMutationOutcome
    data class Failure(val reason: String, val cause: Throwable? = null) : ItemMutationOutcome
}

interface SkillService {
    fun knownSkills(): Set<NamespacedKey>
    fun inspect(caster: LivingEntity): List<SkillView>
    fun cast(caster: LivingEntity, skill: NamespacedKey, target: LivingEntity? = null): Boolean
    fun cast(
        caster: LivingEntity,
        source: AttributeSourceKey,
        skill: NamespacedKey,
        target: LivingEntity? = null
    ): Boolean
}

data class SkillView(
    val key: NamespacedKey,
    val name: String,
    val provider: String,
    val source: AttributeSourceKey,
    val itemId: String?,
    val instanceId: String?,
    val level: Int,
    val description: String = "",
    val targetType: String,
    val range: Double?,
    val activation: SkillActivationView? = null,
    val cooldownMillis: Long,
    val remainingCooldownMillis: Long
)

data class SkillActivationView(
    val input: String,
    val source: String,
    val cancelEvent: String
)

data class RuntimeStatusView(val id: NamespacedKey, val stacks: Int, val remainingMillis: Long)
data class AuraView(val channel: String, val gauge: Double, val remainingMillis: Long)

interface MetadataService {
    fun combatState(entity: LivingEntity): CombatStateView
    fun statuses(entity: LivingEntity): List<RuntimeStatusView>
    fun auras(entity: LivingEntity): List<AuraView>
    fun activeSets(entity: LivingEntity): Map<NamespacedKey, Int>
    fun activePassives(entity: LivingEntity): Set<NamespacedKey>
}

data class CombatStateView(val active: Boolean, val remainingMillis: Long)

data class ActiveAffix(val id: NamespacedKey, val level: Int, val parameters: Map<String, Double>)

interface AffixService {
    fun knownAffixes(): Set<NamespacedKey>
    fun activeAffixes(entity: LivingEntity): List<ActiveAffix>
}

interface SymphonyApi {
    val attributes: AttributeService
    val sources: AttributeSourceService
    val damage: DamageService
    val triggers: TriggerService
    val definitions: DefinitionService
    val items: ItemAttributeService
    val skills: SkillService
    val levels: LevelService
    val metadata: MetadataService
    val affixes: AffixService
    val combatPower: CombatPowerService
}
