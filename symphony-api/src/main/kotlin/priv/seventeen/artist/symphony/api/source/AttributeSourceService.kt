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

package priv.seventeen.artist.symphony.api.source

import org.bukkit.NamespacedKey
import org.bukkit.entity.LivingEntity
import org.bukkit.inventory.ItemStack
import priv.seventeen.artist.symphony.api.attribute.AttributeModifier
import priv.seventeen.artist.symphony.api.attribute.AttributeSourceKey

data class SetPieceContribution(
    val setId: NamespacedKey,
    val pieceId: String,
    val amount: Int = 1
) {
    init {
        require(pieceId.isNotBlank()) { "套装部件 ID 不能为空" }
        require(amount in 1..64) { "套装部件数量必须位于 1 到 64 之间" }
    }
}

data class ItemFeatureContribution(
    val id: NamespacedKey,
    val level: Int,
    val parameters: Map<String, Double> = emptyMap(),
    val tags: Set<String> = emptySet(),
    val category: String? = null,
    val slot: Int? = null
) {
    init {
        require(level > 0) { "物品能力等级必须大于零" }
        require(parameters.values.all(Double::isFinite)) { "物品能力参数必须全部是有限数" }
        require(category == null || category.isNotBlank()) { "物品能力类别不能为空" }
        require(slot == null || slot >= 0) { "物品能力槽位不能为负数" }
    }
}

data class ItemSourceSnapshot(
    val source: AttributeSourceKey,
    val overtureItemId: String?,
    val instanceId: String?,
    val modifiers: List<AttributeModifier>,
    val setPieces: List<SetPieceContribution>,
    val affixes: List<ItemFeatureContribution> = emptyList(),
    val gems: List<ItemFeatureContribution> = emptyList(),
    val skills: List<ItemFeatureContribution> = emptyList(),
    val enhancementLevel: Int = 0,
    val instanceRevision: Int = 0
)

sealed interface SourceUpdateResult {
    val source: AttributeSourceKey

    data class Applied(
        override val source: AttributeSourceKey,
        val entityRevision: Long,
        val changedAttributes: Set<String>,
        val setThresholdChanges: List<SetThresholdChange> = emptyList()
    ) : SourceUpdateResult

    data class Unchanged(
        override val source: AttributeSourceKey,
        val entityRevision: Long
    ) : SourceUpdateResult

    data class Rejected(
        override val source: AttributeSourceKey,
        val reason: String,
        val cause: Throwable? = null
    ) : SourceUpdateResult
}

data class SetThresholdChange(
    val setId: NamespacedKey,
    val threshold: Int,
    val active: Boolean
)

interface AttributeSourceService {
    fun replaceSource(
        entity: LivingEntity,
        source: AttributeSourceKey,
        modifiers: List<AttributeModifier>
    ): SourceUpdateResult

    fun replaceSourceFromLines(
        entity: LivingEntity,
        source: AttributeSourceKey,
        lines: List<String>
    ): SourceUpdateResult

    fun replaceSourceFromItem(
        entity: LivingEntity,
        source: AttributeSourceKey,
        item: ItemStack
    ): SourceUpdateResult

    fun removeSource(entity: LivingEntity, source: AttributeSourceKey): SourceUpdateResult
    fun itemSources(entity: LivingEntity): Map<AttributeSourceKey, ItemSourceSnapshot>
}
