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

package priv.seventeen.artist.symphony.overture.data

import priv.seventeen.artist.overture.api.data.ItemDataNode

internal fun ItemDataNode.Compound.allowedOnly(vararg names: String): Set<String> =
    values.keys - names.toSet()

internal fun ItemDataNode.Compound.compound(name: String): ItemDataNode.Compound? = values[name] as? ItemDataNode.Compound
internal fun ItemDataNode.Compound.list(name: String): ItemDataNode.ListNode? = values[name] as? ItemDataNode.ListNode
internal fun ItemDataNode.Compound.text(name: String): String? = (values[name] as? ItemDataNode.Text)?.value
internal fun ItemDataNode.Compound.boolean(name: String): Boolean? = (values[name] as? ItemDataNode.Bool)?.value

internal fun ItemDataNode.Compound.int(name: String): Int? = when (val node = values[name]) {
    is ItemDataNode.Integer -> node.toIntExact()
    is ItemDataNode.Decimal -> Math.toIntExact(node.toLongExact())
    else -> null
}

internal fun ItemDataNode.Compound.number(name: String): Double? = when (val node = values[name]) {
    is ItemDataNode.Integer -> node.value.toDouble()
    is ItemDataNode.Decimal -> node.value
    is ItemDataNode.Text -> parseNumberOrPercent(node.value)
    else -> null
}

internal fun parseNumberOrPercent(raw: String): Double? {
    val percent = raw.endsWith('%')
    val value = (if (percent) raw.dropLast(1) else raw).toDoubleOrNull() ?: return null
    if (!value.isFinite()) return null
    return if (percent) value / 100.0 else value
}

internal fun compoundOf(vararg values: Pair<String, ItemDataNode>): ItemDataNode.Compound =
    ItemDataNode.Compound(linkedMapOf(*values))

