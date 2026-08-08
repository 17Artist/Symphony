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

package priv.seventeen.artist.symphony.engine.set

import org.bukkit.NamespacedKey
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue
import priv.seventeen.artist.symphony.api.attribute.AttributeKey
import priv.seventeen.artist.symphony.api.attribute.AttributeModifier
import priv.seventeen.artist.symphony.api.attribute.AttributeOperation
import priv.seventeen.artist.symphony.api.attribute.AttributeSourceKey
import priv.seventeen.artist.symphony.api.source.ItemSourceSnapshot
import priv.seventeen.artist.symphony.api.source.SetPieceContribution
import priv.seventeen.artist.symphony.engine.definition.SetBonusDefinition
import priv.seventeen.artist.symphony.engine.definition.SetBonusDisplayDefinition
import priv.seventeen.artist.symphony.engine.definition.SetDefinition
import priv.seventeen.artist.symphony.engine.definition.SetDisplayDefinition

class SetResolverTest {
    private val setKey = NamespacedKey("symphony", "frost_guardian")
    private val definition = SetDefinition(
        setKey.toString(), true, true,
        mapOf(
            2 to bonus(2),
            4 to bonus(4),
            7 to bonus(7)
        ),
        SetDisplayDefinition(
            "寒霜守护者",
            (0 until 8).associate { "piece-$it" to "部件 $it" } +
                (0 until 3).associate { "external-piece-$it" to "额外部件 $it" } +
                mapOf("one" to "部件一", "two" to "部件二")
        )
    )

    @Test
    fun `counts armor hand and multiple external item sources beyond five`() {
        val sources = linkedMapOf<AttributeSourceKey, ItemSourceSnapshot>()
        listOf("head", "chest", "legs", "feet", "main_hand").forEachIndexed { index, slot ->
            val key = AttributeSourceKey("equipment", slot)
            sources[key] = item(key, "native-$index", "piece-$index")
        }
        repeat(3) { index ->
            val key = AttributeSourceKey("arcartx", "ArcartX_Slot_$index")
            sources[key] = item(key, "external-$index", "external-piece-$index")
        }
        val resolved = SetResolver().resolve(sources, mapOf(definition.id to definition))
        assertEquals(8, resolved.counts[definition.id])
        assertTrue(definition.bonuses.keys.all { definition.id to it in resolved.activeThresholds })
    }

    @Test
    fun `same stable instance is counted once even across different source ids`() {
        val firstKey = AttributeSourceKey("arcartx", "one")
        val secondKey = AttributeSourceKey("arcartx", "two")
        val resolved = SetResolver().resolve(
            mapOf(
                firstKey to item(firstKey, "same", "one"),
                secondKey to item(secondKey, "same", "two")
            ),
            mapOf(definition.id to definition)
        )
        assertEquals(1, resolved.counts[definition.id])
        assertFalse(definition.id to 2 in resolved.activeThresholds)
    }

    @Test
    fun `threshold diff reports every crossed activation and deactivation`() {
        val sources = (0 until 8).associate { index ->
            val key = AttributeSourceKey("external", "slot-$index")
            key to item(key, "instance-$index", "piece-$index")
        }
        val resolver = SetResolver()
        val full = resolver.resolve(sources, mapOf(definition.id to definition))
        assertEquals(listOf(2, 4, 7), full.thresholdChanges.filter { it.active }.map { it.threshold })
        val reduced = resolver.resolve(sources.entries.take(3).associate { it.toPair() }, mapOf(definition.id to definition), full.activeThresholds)
        assertEquals(listOf(4, 7), reduced.thresholdChanges.filterNot { it.active }.map { it.threshold })
        assertEquals(3, reduced.counts[definition.id])
    }

    private fun item(source: AttributeSourceKey, instance: String, piece: String) = ItemSourceSnapshot(
        source, "test_item", instance, emptyList(), listOf(SetPieceContribution(setKey, piece))
    )

    private fun bonus(threshold: Int) = SetBonusDefinition(
        threshold,
        listOf(AttributeModifier("bonus", AttributeKey.symphony("ice_damage"), AttributeOperation.ADD, threshold.toDouble())),
        emptyList(),
        SetBonusDisplayDefinition("$threshold 件效果", listOf("测试套装效果"))
    )
}
