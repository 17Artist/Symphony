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

package priv.seventeen.artist.symphony.damagewatch

import org.bukkit.event.EventPriority
import priv.seventeen.artist.blink.event.AutoListener
import priv.seventeen.artist.symphony.api.event.SymphonyDamageConfirmedEvent
import priv.seventeen.artist.symphony.api.event.SymphonyDamageEvent
import priv.seventeen.artist.symphony.api.event.SymphonyHitCheckEvent

object DamageWatchListeners {
    @JvmStatic
    @AutoListener(priority = EventPriority.MONITOR)
    fun onHitCheck(event: SymphonyHitCheckEvent) = DamageWatchRuntime.hitCheck(event)

    @JvmStatic
    @AutoListener(priority = EventPriority.MONITOR, ignoreCancelled = false)
    fun onResolved(event: SymphonyDamageEvent) = DamageWatchRuntime.observe(event)

    @JvmStatic
    @AutoListener(priority = EventPriority.MONITOR)
    fun onConfirmed(event: SymphonyDamageConfirmedEvent) = DamageWatchRuntime.confirm(event)
}
