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

package priv.seventeen.artist.symphony.engine.definition

import priv.seventeen.artist.symphony.api.attribute.AttributeDefinition
import priv.seventeen.artist.symphony.api.attribute.AttributeKey
import priv.seventeen.artist.symphony.api.attribute.AttributeModifier
import priv.seventeen.artist.symphony.engine.power.CompiledPowerExpression
import priv.seventeen.artist.symphony.engine.power.PowerExpressionCompiler
import java.math.RoundingMode
import java.time.Instant

data class ScriptDefinition(
    val id: String,
    val inline: String? = null,
    val file: String? = null
) {
    init {
        require((inline == null) xor (file == null)) { "inline 与 file 必须且只能填写一项" }
    }
}

data class CallbackDefinition(
    val id: String,
    val trigger: String,
    val priority: Int,
    val script: ScriptDefinition? = null,
    val conditions: List<Map<String, Any?>> = emptyList(),
    val actions: List<Map<String, Any?>> = emptyList(),
    val metadata: Map<String, Any?> = emptyMap()
) {
    init {
        require(script != null || actions.isNotEmpty()) { "回调 $id 必须配置 script、file 或 actions" }
    }
}

data class CompiledAttributeDefinition(
    val definition: AttributeDefinition,
    val callbacks: List<CallbackDefinition>
)

enum class ArmorFormulaType {
    DIMINISHING
}

data class ArmorFormulaDefinition(
    val type: ArmorFormulaType,
    val constant: Double,
    val defense: AttributeKey,
    val percentPenetration: AttributeKey,
    val flatPenetration: AttributeKey
) {
    init {
        require(constant > 0.0 && constant.isFinite()) { "护甲常数必须是大于零的有限数" }
    }
}

data class DamageChannelDefinition(
    val id: String,
    val name: String,
    val damageAttribute: AttributeKey?,
    val resistanceAttribute: AttributeKey?,
    val amplificationAttribute: AttributeKey?,
    val mitigation: String?,
    val canCrit: Boolean,
    val element: Boolean,
    val color: String?
)

data class SetBonusDefinition(
    val threshold: Int,
    val modifiers: List<AttributeModifier>,
    val callbacks: List<CallbackDefinition>,
    val display: SetBonusDisplayDefinition
) {
    init {
        require(threshold > 0) { "套装激活件数必须大于零" }
    }
}

data class SetBonusDisplayDefinition(
    val name: String,
    val description: List<String>
) {
    init {
        require(name.isNotBlank()) { "套装效果显示名称不能为空" }
        require(description.isNotEmpty()) { "套装效果说明不能为空" }
        require(description.all(String::isNotBlank)) { "套装效果说明不能包含空白行" }
    }
}

data class SetDisplayDefinition(
    val name: String,
    val pieces: Map<String, String>
) {
    init {
        require(name.isNotBlank()) { "套装显示名称不能为空" }
        require(pieces.isNotEmpty()) { "套装部件显示配置不能为空" }
        require(pieces.all { (id, text) -> id.isNotBlank() && text.isNotBlank() }) {
            "套装显示配置包含空白的部件 ID 或名称"
        }
    }
}

data class SetDefinition(
    val id: String,
    val duplicateInstanceOnce: Boolean,
    val allowDuplicatePieceId: Boolean,
    val bonuses: Map<Int, SetBonusDefinition>,
    val display: SetDisplayDefinition
) {
    val name: String get() = display.name
    val pieces: Map<String, String> get() = display.pieces
}

data class GenericDefinition(
    val id: String,
    val sourcePath: String,
    val values: Map<String, Any?>
)

enum class InteractionType {
    CONVERSION,
    OVERFLOW,
    THRESHOLD,
    SYNERGY,
    CONFLICT,
    AMPLIFY,
    DIMINISH
}

data class InteractionDefinition(
    val id: String,
    val type: InteractionType,
    val source: AttributeKey,
    val target: AttributeKey,
    val threshold: Double,
    val ratio: Double
) {
    init {
        require(threshold.isFinite() && ratio.isFinite()) { "属性联动数值必须是有限数" }
        require(source != target) { "属性联动 $id 不能将来源属性同时设为目标属性" }
    }
}

data class EnhancementLevelDefinition(
    val level: Int,
    val successChance: Double,
    val downgradeChance: Double,
    val destroyChance: Double,
    val multiplier: Double,
    val cost: ItemRequirementDefinition? = null
)

enum class EnhancementOutcome {
    SUCCESS,
    DESTROY,
    DOWNGRADE,
    STAY
}

fun EnhancementLevelDefinition.resolveOutcome(
    roll: Double,
    preventDestroy: Boolean = false,
    preventDowngrade: Boolean = false
): EnhancementOutcome {
    require(roll.isFinite() && roll >= 0.0 && roll < 1.0) { "强化随机值必须是位于 [0, 1) 的有限数" }
    val destroyBoundary = successChance + destroyChance
    val downgradeBoundary = destroyBoundary + downgradeChance
    return when {
        roll < successChance -> EnhancementOutcome.SUCCESS
        roll < destroyBoundary -> if (preventDestroy) EnhancementOutcome.STAY else EnhancementOutcome.DESTROY
        roll < downgradeBoundary -> if (preventDowngrade) EnhancementOutcome.STAY else EnhancementOutcome.DOWNGRADE
        else -> EnhancementOutcome.STAY
    }
}

/**
 * 服务器物品事务所使用的物品堆要求。
 * 只会包含一种标识：原版材质或 Overture 物品 ID。
 */
data class ItemRequirementDefinition(
    val material: String? = null,
    val overtureItem: String? = null,
    val amount: Int
) {
    init {
        require((material == null) xor (overtureItem == null)) {
            "物品要求必须且只能配置 material 或 overture-item 其中一项"
        }
        require(amount > 0) { "物品要求数量必须大于零" }
    }
}

data class AffixPoolEntryDefinition(
    val affixId: String,
    val weight: Double,
    val minLevel: Int,
    val maxLevel: Int
) {
    init {
        require(weight.isFinite() && weight > 0.0) { "词条权重必须是大于零的有限数" }
        require(minLevel > 0 && maxLevel >= minLevel) { "词条等级范围无效：$minLevel..$maxLevel" }
    }
}

data class AffixPoolDefinition(
    val id: String,
    val maxAffixes: Int,
    val cost: ItemRequirementDefinition?,
    val entries: List<AffixPoolEntryDefinition>,
    val priority: Int = 0,
    val overtureItems: Set<String> = setOf("*"),
    val materials: Set<String> = emptySet()
) {
    init {
        require(maxAffixes in 1..64) { "词条池 max-affixes 必须位于 1 到 64 之间" }
        require(entries.isNotEmpty()) { "词条池 $id 不能为空" }
        require(overtureItems.isNotEmpty() || materials.isNotEmpty()) { "词条池 $id 至少要匹配一种物品" }
    }

    fun matches(overtureItemId: String?, material: String): Boolean =
        "*" in overtureItems || overtureItemId in overtureItems || material in materials

    fun specificity(overtureItemId: String?, material: String): Int = when {
        overtureItemId != null && overtureItemId in overtureItems -> 2
        material in materials -> 1
        "*" in overtureItems -> 0
        else -> -1
    }
}

data class EnhancementDefinition(
    val levels: Map<Int, EnhancementLevelDefinition>,
    val preventDestroyItem: String?,
    val preventDowngradeItem: String?
)

data class SocketToolDefinition(
    val id: String,
    val overtureItem: String,
    val name: String,
    val accepts: Set<String>
) {
    init {
        require(overtureItem.isNotBlank()) { "打孔工具的 Overture 物品 ID 不能为空" }
        require(name.isNotBlank()) { "打孔工具显示名称不能为空" }
        require(accepts.isNotEmpty()) { "打孔工具至少要允许一种宝石类别" }
    }
}

data class SocketRemovalDefinition(
    val tool: ItemRequirementDefinition
)

data class CombatPowerDefinition(
    val enabled: Boolean,
    val formula: String,
    val expression: CompiledPowerExpression,
    val minimum: Double,
    val maximum: Double,
    val scale: Int,
    val roundingMode: RoundingMode,
    val formatPattern: String
) {
    init {
        require(minimum.isFinite() && maximum.isFinite() && minimum <= maximum) {
            "战力上下限必须是有限数，且下限不能大于上限"
        }
        require(scale in 0..12) { "战力小数位数必须位于 0 到 12 之间" }
        require(formatPattern.isNotBlank()) { "战力显示格式不能为空" }
    }

    companion object {
        fun disabled(): CombatPowerDefinition {
            val expression = PowerExpressionCompiler.compile("0")
            return CombatPowerDefinition(false, "0", expression, 0.0, Double.MAX_VALUE, 0, RoundingMode.HALF_UP, "0")
        }
    }
}

data class DefinitionSnapshot(
    val revision: Long,
    val createdAt: Instant,
    val attributes: Map<AttributeKey, CompiledAttributeDefinition>,
    val armorFormula: ArmorFormulaDefinition,
    val damageChannels: Map<String, DamageChannelDefinition>,
    val sets: Map<String, SetDefinition>,
    val enhancement: EnhancementDefinition? = null,
    val affixes: Map<String, GenericDefinition> = emptyMap(),
    val affixPools: Map<String, AffixPoolDefinition> = emptyMap(),
    val skills: Map<String, GenericDefinition> = emptyMap(),
    val gems: Map<String, GenericDefinition> = emptyMap(),
    val socketTools: Map<String, SocketToolDefinition> = emptyMap(),
    val socketRemoval: SocketRemovalDefinition? = null,
    val combatPower: CombatPowerDefinition = CombatPowerDefinition.disabled(),
    val interactions: Map<String, InteractionDefinition> = emptyMap(),
    val reactions: Map<String, GenericDefinition> = emptyMap(),
    val resonances: Map<String, GenericDefinition> = emptyMap(),
    val talents: Map<String, GenericDefinition> = emptyMap(),
    val statuses: Map<String, GenericDefinition> = emptyMap(),
    val environments: Map<String, GenericDefinition> = emptyMap()
) {
    fun attribute(key: AttributeKey): CompiledAttributeDefinition? = attributes[key]

    companion object {
        fun empty(): DefinitionSnapshot {
            val physicalDefense = AttributeKey.symphony("physical_defense")
            val penetration = AttributeKey.symphony("penetration")
            val flatPenetration = AttributeKey.symphony("flat_penetration")
            return DefinitionSnapshot(
                revision = 0,
                createdAt = Instant.EPOCH,
                attributes = emptyMap(),
                armorFormula = ArmorFormulaDefinition(
                    ArmorFormulaType.DIMINISHING,
                    100.0,
                    physicalDefense,
                    penetration,
                    flatPenetration
                ),
                damageChannels = emptyMap(),
                sets = emptyMap(),
                enhancement = null
            )
        }
    }
}
