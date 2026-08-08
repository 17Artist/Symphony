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

package priv.seventeen.artist.symphony.bukkit.service

import org.bukkit.Bukkit
import org.bukkit.NamespacedKey
import org.bukkit.entity.LivingEntity
import org.bukkit.inventory.ItemStack
import org.bukkit.plugin.Plugin
import priv.seventeen.artist.symphony.api.attribute.AttributeExplain
import priv.seventeen.artist.symphony.api.attribute.AttributeKey
import priv.seventeen.artist.symphony.api.attribute.AttributeModifier
import priv.seventeen.artist.symphony.api.attribute.AttributeProvider
import priv.seventeen.artist.symphony.api.attribute.AttributeProviderContext
import priv.seventeen.artist.symphony.api.attribute.AttributeService
import priv.seventeen.artist.symphony.api.attribute.AttributeSnapshot
import priv.seventeen.artist.symphony.api.attribute.AttributeSourceKey
import priv.seventeen.artist.symphony.api.registration.RegistrationHandle
import priv.seventeen.artist.symphony.api.source.AttributeSourceService
import priv.seventeen.artist.symphony.api.source.ItemSourceSnapshot
import priv.seventeen.artist.symphony.api.source.SourceUpdateResult
import priv.seventeen.artist.symphony.engine.attribute.AttributeStateStore
import priv.seventeen.artist.symphony.engine.attribute.SourceBatch
import priv.seventeen.artist.symphony.engine.attribute.SourceLineParser
import priv.seventeen.artist.symphony.engine.attribute.StateMutationResult
import priv.seventeen.artist.symphony.engine.definition.DefinitionRepository
import priv.seventeen.artist.symphony.engine.equipment.OffhandSettings
import priv.seventeen.artist.symphony.overture.item.OvertureItemSourceCompiler

class BukkitAttributeSourceService(
    private val store: AttributeStateStore,
    private val lineParser: SourceLineParser,
    private val itemCompiler: OvertureItemSourceCompiler
) : AttributeSourceService {
    var onSetCountsChanged: (LivingEntity) -> Unit = {}

    override fun replaceSource(
        entity: LivingEntity,
        source: AttributeSourceKey,
        modifiers: List<AttributeModifier>
    ): SourceUpdateResult = guarded(source) {
        requirePrimaryThread()
        store.replace(entity.uniqueId, source, modifiers, null).toApi(source)
    }

    override fun replaceSourceFromLines(
        entity: LivingEntity,
        source: AttributeSourceKey,
        lines: List<String>
    ): SourceUpdateResult = guarded(source) {
        requirePrimaryThread()
        val modifiers = lineParser.parse(lines)
        store.replace(entity.uniqueId, source, modifiers, null).toApi(source)
    }

    override fun replaceSourceFromItem(
        entity: LivingEntity,
        source: AttributeSourceKey,
        item: ItemStack
    ): SourceUpdateResult = guarded(source) {
        requirePrimaryThread()
        val beforeCounts = setCounts(entity)
        val snapshot = if (item.type.isAir) {
            ItemSourceSnapshot(source, null, null, emptyList(), emptyList())
        } else {
            itemCompiler.compile(source, item)
        }
        val meaningful = snapshot.takeIf(ItemSourceSnapshot::hasSymphonyContributions)
        val mutation = store.replace(entity.uniqueId, source, meaningful?.modifiers.orEmpty(), meaningful)
        if (source.namespace != "equipment" && beforeCounts != setCounts(entity)) onSetCountsChanged(entity)
        mutation.toApi(source)
    }

    override fun removeSource(entity: LivingEntity, source: AttributeSourceKey): SourceUpdateResult = guarded(source) {
        requirePrimaryThread()
        val beforeCounts = setCounts(entity)
        val mutation = store.remove(entity.uniqueId, source)
        if (source.namespace != "equipment" && beforeCounts != setCounts(entity)) onSetCountsChanged(entity)
        mutation.toApi(source)
    }

    override fun itemSources(entity: LivingEntity): Map<AttributeSourceKey, ItemSourceSnapshot> =
        store.stateIfPresent(entity.uniqueId)?.sources
            ?.mapNotNull { (source, batch) -> batch.item?.let { source to it } }
            ?.toMap()
            .orEmpty()

    fun replaceEquipmentSources(
        entity: LivingEntity,
        items: Map<AttributeSourceKey, ItemStack?>,
        offhand: OffhandSettings
    ): SourceUpdateResult = guarded(AttributeSourceKey("equipment", "batch")) {
        requirePrimaryThread()
        require(items.keys.all { it.namespace == "equipment" }) { "装备批次中包含非装备来源" }
        val replacements = items.toSortedMap().mapNotNull { (source, item) ->
            if (item == null || item.type.isAir) return@mapNotNull null
            val snapshot = if (source.value == "off_hand") {
                itemCompiler.compileOffhand(source, item, offhand) ?: return@mapNotNull null
            } else {
                itemCompiler.compile(source, item)
            }
            if (!snapshot.hasSymphonyContributions()) {
                null
            } else source to SourceBatch(snapshot.modifiers, snapshot)
        }.toMap()
        store.replaceSources(
            entity.uniqueId,
            removeWhen = { it.namespace == "equipment" },
            replacements = replacements,
            reason = "equipment.batch"
        ).toApi(AttributeSourceKey("equipment", "batch"))
    }

    fun setCounts(entity: LivingEntity): Map<String, Int> =
        store.stateIfPresent(entity.uniqueId)?.setResolution?.counts.orEmpty()

    fun replaceManagedSources(
        entity: LivingEntity,
        namespaces: Set<String>,
        replacements: Map<AttributeSourceKey, List<AttributeModifier>>,
        reason: String
    ): SourceUpdateResult = guarded(AttributeSourceKey("runtime", "managed-batch")) {
        requirePrimaryThread()
        require(namespaces.isNotEmpty()) { "托管来源命名空间不能为空" }
        require(replacements.keys.all { it.namespace in namespaces }) { "托管来源批次包含不符合要求的命名空间" }
        store.replaceSources(
            entity.uniqueId,
            removeWhen = { it.namespace in namespaces },
            replacements = replacements.mapValues { SourceBatch(it.value.toList()) },
            reason = reason
        ).toApi(AttributeSourceKey("runtime", "managed-batch"))
    }

    private inline fun guarded(source: AttributeSourceKey, block: () -> SourceUpdateResult): SourceUpdateResult =
        try {
            block()
        } catch (error: Throwable) {
            SourceUpdateResult.Rejected(source, error.message ?: "属性来源更新失败", error)
        }

    private fun StateMutationResult.toApi(source: AttributeSourceKey): SourceUpdateResult = if (cancelled) {
        SourceUpdateResult.Rejected(source, "AttributeSnapshotPrepareEvent 取消了属性来源事务")
    } else if (changed) {
        SourceUpdateResult.Applied(
            source,
            state.revision,
            changedAttributes.mapTo(linkedSetOf()) { it.value },
            setThresholdChanges
        )
    } else {
        SourceUpdateResult.Unchanged(source, state.revision)
    }

    private fun requirePrimaryThread() {
        check(Bukkit.isPrimaryThread()) { "Bukkit 实体或物品来源更新必须在主线程执行" }
    }
}

private fun ItemSourceSnapshot.hasSymphonyContributions(): Boolean =
    overtureItemId != null && (
        modifiers.isNotEmpty() || setPieces.isNotEmpty() || affixes.isNotEmpty() || gems.isNotEmpty() || skills.isNotEmpty() ||
            instanceId != null || enhancementLevel > 0
        )

class BukkitAttributeService(
    private val store: AttributeStateStore,
    private val definitions: DefinitionRepository
) : AttributeService {
    private val providers = OwnedPriorityRegistry<AttributeProvider>("attribute-provider")

    override fun value(entity: LivingEntity, key: AttributeKey): Double {
        val snapshot = current(entity)
        return snapshot.values[key]
            ?: definitions.current().snapshot.attributes[key]?.definition?.base
            ?: 0.0
    }

    override fun snapshot(entity: LivingEntity): AttributeSnapshot = current(entity)

    override fun explain(entity: LivingEntity, key: AttributeKey): AttributeExplain? {
        current(entity)
        return store.state(entity.uniqueId).explanations[key]
    }

    override fun invalidate(entity: LivingEntity, reason: String, affected: Set<AttributeKey>) {
        requirePrimaryThread()
        recalculateInternal(entity, reason.ifBlank { "api.invalidate" })
    }

    override fun recalculate(entity: LivingEntity): AttributeSnapshot {
        requirePrimaryThread()
        return recalculateInternal(entity, "api.recalculate")
    }

    override fun registerProvider(owner: Plugin, provider: AttributeProvider, priority: Int): RegistrationHandle {
        require(provider.id.namespace == owner.name.lowercase().replace(Regex("[^a-z0-9._-]"), "_")) {
            "提供者 ID ${provider.id} 必须使用所有者命名空间"
        }
        return providers.register(owner, provider.id, priority, provider) {}
    }

    fun removeEntity(entity: LivingEntity) {
        removeEntity(entity.uniqueId)
    }

    fun removeEntity(entityId: java.util.UUID) { store.removeEntity(entityId) }

    fun removeOwner(owner: Plugin) = providers.closeOwner(owner)

    fun clearProviders() = providers.clear()

    private fun current(entity: LivingEntity): AttributeSnapshot {
        val state = store.state(entity.uniqueId)
        val compiled = definitions.current().snapshot
        return if (
            state.snapshot.definitionRevision != compiled.revision ||
            state.snapshot.values.size != compiled.attributes.size
        ) {
            requirePrimaryThread()
            recalculateInternal(
                entity,
                if (state.snapshot.values.isEmpty()) "attribute.initial-read" else "definition.revision"
            )
        } else state.snapshot
    }

    private fun recalculateInternal(entity: LivingEntity, reason: String): AttributeSnapshot {
        val context = AttributeProviderContext(System.currentTimeMillis(), definitions.current().snapshot.revision)
        val batches = linkedMapOf<AttributeSourceKey, SourceBatch>()
        providers.active().forEach { entry ->
            val modifiers = entry.value.modifiers(entity, context)
            batches[AttributeSourceKey("provider", entry.key.toString())] = SourceBatch(modifiers.toList())
        }
        val mutation = store.replaceSources(entity.uniqueId, { it.namespace == "provider" }, batches, reason)
        val result = when {
            mutation.cancelled -> mutation.state
            mutation.changed -> mutation.state
            else -> store.recalculate(entity.uniqueId, reason)
        }
        return result.snapshot
    }

    private fun requirePrimaryThread() {
        check(Bukkit.isPrimaryThread()) { "Bukkit 属性重新计算必须在主线程执行" }
    }
}
