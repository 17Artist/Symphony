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

package priv.seventeen.artist.symphony.api.power

import org.bukkit.entity.LivingEntity
import java.util.UUID

/** 一次战力计算得到的不可变结果。 */
data class CombatPowerSnapshot(
    val entityId: UUID,
    val rawValue: Double,
    val value: Double,
    val formatted: String,
    val attributeRevision: Long,
    val definitionRevision: Long,
    val variables: Map<String, Double>,
    val calculatedAtMillis: Long,
    val error: String? = null
) {
    val successful: Boolean get() = error == null
}

/**
 * 只读的战力计算服务。
 *
 * 战力不会写入属性来源，也不能作为输入属性参与计算。这样可以避免属性图出现环，
 * 并保证 PlaceholderAPI 的读取操作不会产生副作用。
 */
interface CombatPowerService {
    fun value(entity: LivingEntity): Double = snapshot(entity).value
    fun snapshot(entity: LivingEntity): CombatPowerSnapshot

    /** 线程安全地返回最近一次已经计算的结果；首次刷新或读取前返回 null。 */
    fun cached(entityId: UUID): CombatPowerSnapshot?

    /** 丢弃已经计算的结果；下次合法读取时将根据当前快照重新计算。 */
    fun invalidate(entityId: UUID)
}
