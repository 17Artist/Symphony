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

package priv.seventeen.artist.symphony.bukkit.equipment

import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class ItemDisplayRefreshPolicyTest {
    private val availableItemIds = setOf("active_blade", "active_helmet")

    @Test
    fun `已删除定义的历史物品不会尝试重建`() {
        assertFalse(shouldAttemptOvertureRebuild("removed_blade", availableItemIds))
    }

    @Test
    fun `仍有定义的物品会正常重建`() {
        assertTrue(shouldAttemptOvertureRebuild("active_blade", availableItemIds))
    }

    @Test
    fun `缺失或空白 ID 仍交给 Overture 报告数据异常`() {
        assertTrue(shouldAttemptOvertureRebuild(null, availableItemIds))
        assertTrue(shouldAttemptOvertureRebuild("", availableItemIds))
        assertTrue(shouldAttemptOvertureRebuild("   ", availableItemIds))
    }
}
