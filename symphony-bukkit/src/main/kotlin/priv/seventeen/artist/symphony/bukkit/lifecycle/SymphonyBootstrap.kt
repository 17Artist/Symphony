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

package priv.seventeen.artist.symphony.bukkit.lifecycle

import priv.seventeen.artist.blink.BlinkLog
import priv.seventeen.artist.blink.bukkitPlugin
import priv.seventeen.artist.blink.lifecycle.Awake
import priv.seventeen.artist.blink.lifecycle.LifeCycle
import priv.seventeen.artist.symphony.bukkit.runtime.SymphonyRuntime

object SymphonyBootstrap {
    @JvmStatic
    @Awake(LifeCycle.LOAD, priority = -100)
    fun load() {
        SymphonyRuntime.load(bukkitPlugin)
    }

    @JvmStatic
    @Awake(LifeCycle.ENABLE, priority = -100)
    fun enable() {
        SymphonyRuntime.enable()
        BlinkLog.success(SymphonyRuntime.language().text("console.service-loaded"))
    }

    @JvmStatic
    @Awake(LifeCycle.ACTIVE, priority = -100)
    fun active() {
        SymphonyRuntime.activate()
        BlinkLog.success(SymphonyRuntime.language().text("console.service-active"))
    }

    @JvmStatic
    @Awake(LifeCycle.DISABLE, priority = 100)
    fun disable() {
        SymphonyRuntime.disable()
        SymphonyRuntime.languageOrNull()?.text("console.service-disabled")?.let(BlinkLog::info)
    }
}
