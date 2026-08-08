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

import org.bukkit.entity.Player
import org.bukkit.inventory.ItemStack
import priv.seventeen.artist.overture.api.OvertureAPI
import priv.seventeen.artist.overture.api.data.ItemDataNode
import priv.seventeen.artist.overture.api.data.ItemMutationResult

sealed interface ItemMutationPlanResult {
    data class Success(val itemStack: ItemStack) : ItemMutationPlanResult
    data class Failure(val reason: String, val cause: Throwable? = null) : ItemMutationPlanResult
}

class OvertureMutationGateway {
    fun mutate(
        item: ItemStack,
        player: Player?,
        changes: Map<String, ItemDataNode>,
        removals: Set<String> = emptySet()
    ): ItemMutationPlanResult {
        require(changes.keys.intersect(removals).isEmpty()) { "同一路径不能在一次修改中同时被改写和删除" }
        val result = OvertureAPI.mutateItem(item, player) { data ->
            removals.sorted().forEach(data::remove)
            changes.toSortedMap().forEach(data::put)
        }
        return when (result) {
            is ItemMutationResult.Success -> ItemMutationPlanResult.Success(result.itemStack)
            is ItemMutationResult.Failure -> ItemMutationPlanResult.Failure(result.reason, result.cause)
        }
    }
}
