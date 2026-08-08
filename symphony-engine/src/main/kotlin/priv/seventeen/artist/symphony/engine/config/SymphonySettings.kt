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

package priv.seventeen.artist.symphony.engine.config

import priv.seventeen.artist.symphony.engine.equipment.OffhandSettings

data class CombatSettings(
    val enabled: Boolean,
    val minimumDamage: Double,
    val confirmationDelayTicks: Long,
    val maxTransactionDepth: Int,
    val mappedEnvironmentalCauses: Map<String, String>
)

data class ScriptSettings(
    val javaInteropUnrestricted: Boolean,
    val slowCallbackWarningMillis: Long,
    val failureWindowSeconds: Long,
    val disableAfterFailures: Int
)

data class PerformanceSettings(
    val equipmentCoalesceTicks: Long,
    val timerBucketTicks: Long,
    val cacheIdleSeconds: Long
)

data class FeatureSettings(
    val affixes: Boolean,
    val skills: Boolean,
    val gems: Boolean,
    val sockets: Boolean,
    val enhancement: Boolean,
    val interactions: Boolean,
    val elements: Boolean,
    val resonances: Boolean,
    val talents: Boolean,
    val statuses: Boolean,
    val environments: Boolean
)

data class EquipmentSettings(
    val offhand: OffhandSettings,
    val coalesceTicks: Long
)

data class EpicFightCompatibilitySettings(
    val enabled: Boolean,
    val postWorldGraceMillis: Long,
    val stuckInactionMillis: Long,
    val fallbackPollTicks: Long
)

data class CompatibilitySettings(
    val epicFight: EpicFightCompatibilitySettings
)

data class SymphonySettings(
    val schema: Int,
    val combat: CombatSettings,
    val scripts: ScriptSettings,
    val performance: PerformanceSettings,
    val features: FeatureSettings,
    val equipment: EquipmentSettings,
    val compatibility: CompatibilitySettings
)
