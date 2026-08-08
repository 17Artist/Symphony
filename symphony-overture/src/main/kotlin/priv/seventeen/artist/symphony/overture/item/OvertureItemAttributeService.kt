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

package priv.seventeen.artist.symphony.overture.item

import org.bukkit.NamespacedKey
import org.bukkit.entity.LivingEntity
import org.bukkit.entity.Player
import org.bukkit.inventory.ItemStack
import priv.seventeen.artist.overture.api.OvertureAPI
import priv.seventeen.artist.overture.api.data.ItemDataNode
import priv.seventeen.artist.overture.api.data.ItemMutationResult
import priv.seventeen.artist.symphony.api.attribute.AttributeSourceKey
import priv.seventeen.artist.symphony.api.service.ItemAttributeService
import priv.seventeen.artist.symphony.api.service.ItemFeatureView
import priv.seventeen.artist.symphony.api.service.ItemInspection
import priv.seventeen.artist.symphony.api.service.ItemMutationOutcome
import priv.seventeen.artist.symphony.api.service.SocketSlotView
import priv.seventeen.artist.symphony.api.service.SocketView
import priv.seventeen.artist.symphony.api.source.ItemFeatureContribution
import priv.seventeen.artist.symphony.overture.data.compound
import priv.seventeen.artist.symphony.overture.data.boolean
import priv.seventeen.artist.symphony.overture.data.int
import priv.seventeen.artist.symphony.overture.data.list
import priv.seventeen.artist.symphony.overture.data.number
import priv.seventeen.artist.symphony.overture.data.text

class OvertureItemAttributeService(
    private val compiler: OvertureItemSourceCompiler
) : ItemAttributeService {
    override fun inspect(item: ItemStack): ItemInspection {
        if (item.type.isAir || !OvertureAPI.isOvertureItem(item)) {
            return ItemInspection(null, null, emptyMap(), emptySet(), emptyList())
        }
        return runCatching {
            val source = compiler.compile(AttributeSourceKey("inspect", "temporary"), item)
            val view = OvertureAPI.readItemData(item)
            val symphonyNamespace = view.namespace("symphony")
            val instance = symphonyNamespace?.compound("instance")
            val offhand = view.component(OvertureItemSourceCompiler.OFFHAND)
            val affixNodes = instance?.list("affixes")?.values.orEmpty()
            val socketsNode = view.component(OvertureItemSourceCompiler.SOCKETS)
            val staticSlots = socketsNode?.list("slots")?.values.orEmpty().mapNotNull { raw ->
                val node = raw as? ItemDataNode.Compound ?: return@mapNotNull null
                val accepts = node.list("accepts")?.textValues().orEmpty()
                accepts to (node.int("unlock_at_enhancement") ?: 0)
            }
            val extraSlots = instance?.list("extra_sockets")?.values.orEmpty().mapNotNull { raw ->
                val node = raw as? ItemDataNode.Compound ?: return@mapNotNull null
                val accepts = node.list("accepts")?.textValues().orEmpty()
                Triple(accepts, node.int("unlock_at_enhancement") ?: 0, node.text("tool")?.let(NamespacedKey::fromString))
            }
            val gemsBySlot = source.gems.associateBy { it.slot ?: -1 }
            ItemInspection(
                overtureItemId = source.overtureItemId,
                instanceId = source.instanceId,
                attributes = source.modifiers.groupBy { it.attribute }.mapValues { (_, modifiers) -> modifiers.sumOf { it.value } },
                setIds = source.setPieces.mapTo(linkedSetOf()) { it.setId },
                diagnostics = emptyList(),
                affixes = source.affixes.mapIndexed { index, feature ->
                    feature.toView((affixNodes.getOrNull(index) as? ItemDataNode.Compound)
                        ?.values?.get("locked")?.let { it as? ItemDataNode.Bool }?.value ?: false)
                },
                gems = source.gems.map { it.toView() },
                skills = source.skills.map { it.toView() },
                sockets = socketsNode?.let { sockets ->
                    val slots = buildList {
                        staticSlots.forEachIndexed { index, (accepts, unlockAt) ->
                            add(SocketSlotView(
                                index,
                                accepts,
                                source.enhancementLevel >= unlockAt,
                                unlockAt.takeIf { it > 0 },
                                gemsBySlot[index]?.toView()
                            ))
                        }
                        extraSlots.forEachIndexed { offset, (accepts, unlockAt, tool) ->
                            val index = staticSlots.size + offset
                            add(SocketSlotView(
                                index,
                                accepts,
                                source.enhancementLevel >= unlockAt,
                                unlockAt.takeIf { it > 0 },
                                gemsBySlot[index]?.toView(),
                                tool
                            ))
                        }
                    }
                    SocketView(slots, sockets.int("max_extra_slots") ?: 0)
                },
                enhancementLevel = source.enhancementLevel,
                instanceRevision = source.instanceRevision,
                symphonyDataPresent = offhand != null || instance != null,
                offhandAllowed = offhand?.boolean("enabled"),
                offhandAttributeScale = offhand?.number("attribute_scale")
            )
        }.getOrElse { error ->
            ItemInspection(null, null, emptyMap(), emptySet(), listOf(error.message ?: "物品数据无法解析"))
        }
    }

    override fun rebuild(item: ItemStack, viewer: LivingEntity?): ItemMutationOutcome =
        when (val result = OvertureAPI.rebuildItem(item, viewer as? Player)) {
            is ItemMutationResult.Success -> ItemMutationOutcome.Success(result.itemStack)
            is ItemMutationResult.Failure -> ItemMutationOutcome.Failure(result.reason, result.cause)
        }

    private fun ItemFeatureContribution.toView(locked: Boolean = false) =
        ItemFeatureView(id, level, parameters, tags, locked, category)

    private fun ItemDataNode.ListNode.textValues(): Set<String> = values.mapNotNullTo(linkedSetOf()) {
        (it as? ItemDataNode.Text)?.value
    }
}
