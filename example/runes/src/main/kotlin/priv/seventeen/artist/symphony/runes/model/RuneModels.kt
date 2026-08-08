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

package priv.seventeen.artist.symphony.runes.model

import priv.seventeen.artist.symphony.api.attribute.AttributeKey
import priv.seventeen.artist.symphony.api.attribute.AttributeModifier
import priv.seventeen.artist.symphony.api.attribute.AttributeOperation
import priv.seventeen.artist.symphony.api.attribute.AttributeDefinition

data class ScaledValue(val base: Double, val perRank: Double = 0.0) {
    init { require(base.isFinite() && perRank.isFinite()) }
    fun at(rank: Int): Double {
        require(rank > 0)
        return base + perRank * (rank - 1)
    }
}

data class ScaledLevel(val base: Int, val perRank: Int = 0) {
    init { require(base >= 0 && perRank >= 0) }
    fun at(rank: Int): Int {
        require(rank > 0)
        return Math.addExact(base, Math.multiplyExact(perRank, rank - 1))
    }
}

data class RuneModifierDefinition(
    val id: String,
    val attribute: AttributeKey,
    val operation: AttributeOperation,
    val value: ScaledValue,
    val priority: Int = 0
) {
    fun create(runeId: String, rank: Int, description: String): AttributeModifier = AttributeModifier(
        id = "$runeId/$id/rank-$rank",
        attribute = attribute,
        operation = operation,
        value = value.at(rank),
        priority = priority,
        description = description
    )
}

data class RuneDefinition(
    val id: String,
    val displayName: String,
    val description: List<String>,
    val category: String,
    val maximumRank: Int,
    val minimumLevel: ScaledLevel,
    val modifiers: List<RuneModifierDefinition>
) {
    init {
        require(id.matches(Regex("^[a-z0-9_-]{1,64}$"))) { "符文 ID 无效：$id" }
        require(displayName.isNotBlank() && displayName.length <= 64)
        require(category.matches(Regex("^[a-z0-9._-]{1,32}$")))
        require(maximumRank in 1..100)
        require(modifiers.isNotEmpty())
        require(modifiers.map { it.id }.toSet().size == modifiers.size) { "符文 $id 中存在重复的修改器 ID" }
    }

    fun normalizedRank(rank: Int): Int = rank.coerceIn(1, maximumRank)
    fun requiredLevel(rank: Int): Int = minimumLevel.at(normalizedRank(rank))
    fun createModifiers(rank: Int): List<AttributeModifier> {
        val normalized = normalizedRank(rank)
        return modifiers.map { it.create(id, normalized, displayName) }
    }
}

data class RuneSlotDefinition(
    val id: String,
    val displayName: String,
    val acceptedCategories: Set<String>
) {
    init {
        require(id.matches(Regex("^[a-z0-9_-]{1,32}$")))
        require(displayName.isNotBlank())
        require(acceptedCategories.isNotEmpty())
    }

    fun accepts(rune: RuneDefinition): Boolean = "*" in acceptedCategories || rune.category in acceptedCategories
}

data class RuneCatalog(
    val definitionPriority: Int,
    val slots: Map<String, RuneSlotDefinition>,
    val runes: Map<String, RuneDefinition>,
    val customAttributes: Map<AttributeKey, AttributeDefinition>
) {
    init {
        require(slots.isNotEmpty())
        require(runes.isNotEmpty())
        val unusable = runes.values.filter { rune -> slots.values.none { it.accepts(rune) } }
        require(unusable.isEmpty()) { "以下符文没有可用槽位：${unusable.joinToString { it.id }}" }
    }
}

data class PlayerRuneState(
    val unlocked: Map<String, Int> = emptyMap(),
    val equipped: Map<String, String> = emptyMap()
)

enum class RuneActivationState {
    ACTIVE,
    EMPTY,
    RUNE_MISSING,
    NOT_UNLOCKED,
    CATEGORY_MISMATCH,
    LEVEL_PROVIDER_MISSING,
    LEVEL_TOO_LOW
}

data class RuneSlotStatus(
    val slot: RuneSlotDefinition,
    val rune: RuneDefinition?,
    val rank: Int?,
    val requiredLevel: Int?,
    val state: RuneActivationState
)

enum class RuneMutationFailure {
    SLOT_NOT_FOUND,
    RUNE_NOT_FOUND,
    NOT_UNLOCKED,
    CATEGORY_MISMATCH,
    ALREADY_EQUIPPED,
    LEVEL_PROVIDER_MISSING,
    LEVEL_TOO_LOW,
    SOURCE_REJECTED
}

sealed interface RuneMutationResult {
    data class Success(val status: RuneSlotStatus? = null) : RuneMutationResult
    data class Failure(
        val reason: RuneMutationFailure,
        val requiredLevel: Int? = null,
        val detail: String? = null
    ) : RuneMutationResult
}
