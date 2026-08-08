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

package priv.seventeen.artist.symphony.api.event

import org.bukkit.NamespacedKey
import org.bukkit.entity.LivingEntity
import org.bukkit.event.Event
import org.bukkit.event.HandlerList

/**
 * 一个或多个套装的有效件数发生变化后发布。
 *
 * 外部背包系统可以监听此事件，刷新存放在 Bukkit 常规玩家背包之外的物品显示。
 */
class SetCountsChangedEvent(
    val entity: LivingEntity,
    counts: Map<NamespacedKey, Int>
) : Event() {
    val counts: Map<NamespacedKey, Int> = counts.toMap()

    override fun getHandlers(): HandlerList = HANDLERS

    companion object {
        @JvmStatic
        val HANDLERS = HandlerList()

        @JvmStatic
        fun getHandlerList(): HandlerList = HANDLERS
    }
}
