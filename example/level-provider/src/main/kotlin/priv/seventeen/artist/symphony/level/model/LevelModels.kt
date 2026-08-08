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

package priv.seventeen.artist.symphony.level.model

import kotlin.math.pow
import kotlin.math.roundToLong

data class PlayerProgress(
    val level: Int,
    val experience: Long
)

data class ExperienceChange(
    val progress: PlayerProgress,
    val levelsGained: Int,
    val discardedExperience: Long
)

data class LevelCurve(
    val maximumLevel: Int,
    val baseExperience: Long,
    val growthFactor: Double
) {
    init {
        require(maximumLevel in 1..10_000) { "maximumLevel 必须位于 1 到 10000 之间" }
        require(baseExperience > 0L) { "baseExperience 必须大于零" }
        require(growthFactor.isFinite() && growthFactor >= 1.0) { "growthFactor 必须是大于或等于 1.0 的有限数" }
    }

    fun experienceForNextLevel(level: Int): Long? {
        require(level in 1..maximumLevel) { "level 必须位于 1 到 maximumLevel 之间" }
        if (level >= maximumLevel) return null
        val calculated = baseExperience.toDouble() * growthFactor.pow((level - 1).toDouble())
        return if (!calculated.isFinite() || calculated >= Long.MAX_VALUE.toDouble()) Long.MAX_VALUE
        else calculated.roundToLong().coerceAtLeast(1L)
    }

    fun normalize(progress: PlayerProgress): PlayerProgress {
        val level = progress.level.coerceIn(1, maximumLevel)
        val threshold = experienceForNextLevel(level)
        val experience = if (threshold == null) 0L else progress.experience.coerceIn(0L, threshold - 1L)
        return progress.copy(level = level, experience = experience)
    }

    fun addExperience(progress: PlayerProgress, amount: Long): ExperienceChange {
        require(amount >= 0L) { "amount 不能为负数" }
        var current = normalize(progress)
        var remaining = amount
        var gained = 0
        while (remaining > 0L && current.level < maximumLevel) {
            val threshold = requireNotNull(experienceForNextLevel(current.level))
            val needed = threshold - current.experience
            if (remaining < needed) {
                current = current.copy(experience = current.experience + remaining)
                remaining = 0L
            } else {
                remaining -= needed
                current = current.copy(level = current.level + 1, experience = 0L)
                gained++
            }
        }
        return ExperienceChange(current, gained, remaining)
    }
}
