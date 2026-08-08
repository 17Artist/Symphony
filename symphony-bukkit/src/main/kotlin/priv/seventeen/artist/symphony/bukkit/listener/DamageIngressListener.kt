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

package priv.seventeen.artist.symphony.bukkit.listener

import org.bukkit.event.EventPriority
import org.bukkit.event.entity.EntityDamageEvent
import priv.seventeen.artist.blink.event.AutoListener
import priv.seventeen.artist.symphony.bukkit.runtime.SymphonyRuntime

object DamageIngressListener {
    @JvmStatic
    @AutoListener(priority = EventPriority.HIGH, ignoreCancelled = true)
    fun onDamageHigh(event: EntityDamageEvent) {
        SymphonyRuntime.damageOrNull()?.prepare(event)
    }

    @JvmStatic
    @AutoListener(priority = EventPriority.MONITOR, ignoreCancelled = false)
    fun onDamageMonitor(event: EntityDamageEvent) {
        SymphonyRuntime.damageOrNull()?.monitor(event)
    }
}

