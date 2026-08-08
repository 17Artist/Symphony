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

package priv.seventeen.artist.symphony.engine.equipment

import priv.seventeen.artist.symphony.api.source.ItemSourceSnapshot

enum class OffhandMode(val id: String) {
    DISABLED("disabled"),
    FULL("full"),
    SCALED("scaled"),
    ITEM_CONTROLLED("item-controlled");

    companion object {
        fun parse(value: String): OffhandMode = values().firstOrNull {
            it.id == value.lowercase().replace('_', '-')
        } ?: throw IllegalArgumentException(
            "equipment.offhand.mode 必须是以下值之一：${values().joinToString { it.id }}"
        )
    }
}

data class OffhandSettings(
    val mode: OffhandMode,
    val attributeScale: Double
) {
    init {
        require(attributeScale.isFinite() && attributeScale in 0.0..1.0) {
            "副手属性倍率必须位于 0 到 1 之间"
        }
    }
}

data class OffhandItemSettings(
    val enabled: Boolean,
    val attributeScale: Double
) {
    init {
        require(attributeScale.isFinite() && attributeScale in 0.0..1.0) {
            "物品的副手属性倍率必须位于 0 到 1 之间"
        }
    }
}

object OffhandSourcePolicy {
    fun apply(
        source: ItemSourceSnapshot,
        settings: OffhandSettings,
        item: OffhandItemSettings?
    ): ItemSourceSnapshot? {
        val scale = when (settings.mode) {
            OffhandMode.DISABLED -> return null
            OffhandMode.FULL -> {
                if (item?.enabled == false) return null
                1.0
            }
            OffhandMode.SCALED -> {
                if (item?.enabled == false) return null
                settings.attributeScale
            }
            OffhandMode.ITEM_CONTROLLED -> {
                if (item?.enabled != true) return null
                item.attributeScale
            }
        }
        if (scale == 1.0) return source
        return source.copy(
            modifiers = source.modifiers.map { modifier -> modifier.copy(value = modifier.value * scale) }
        )
    }
}
