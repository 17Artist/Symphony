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

package priv.seventeen.artist.symphony.bukkit.gui

import org.bukkit.Material
import org.bukkit.entity.Player
import org.bukkit.inventory.ItemFlag
import org.bukkit.inventory.ItemStack
import priv.seventeen.artist.overture.api.OvertureAPI

class GuiIconFactory {
    fun icon(
        player: Player,
        templateId: String?,
        fallback: Material,
        name: String,
        lore: List<String> = emptyList()
    ): ItemStack {
        val item = templateId?.let { runCatching { OvertureAPI.getTemplateItem(it) }.getOrNull() }?.clone()
            ?: ItemStack(fallback)
        val meta = item.itemMeta ?: return item
        meta.setDisplayName(name.take(256))
        meta.lore = lore.take(64).map { it.take(1024) }
        meta.addItemFlags(*ItemFlag.values())
        item.itemMeta = meta
        return item
    }

    fun filler(player: Player): ItemStack = icon(player, null, Material.BLACK_STAINED_GLASS_PANE, " ")
}
