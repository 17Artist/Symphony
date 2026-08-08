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

package priv.seventeen.artist.symphony.runes

import priv.seventeen.artist.blink.bukkitPlugin
import priv.seventeen.artist.blink.lifecycle.Awake
import priv.seventeen.artist.blink.lifecycle.LifeCycle

object RuneBootstrap {
    @JvmStatic
    @Awake(LifeCycle.LOAD, priority = -100)
    fun load() = RuneRuntime.load(bukkitPlugin)

    @JvmStatic
    @Awake(LifeCycle.ENABLE, priority = -100)
    fun enable() = RuneRuntime.enable()

    @JvmStatic
    @Awake(LifeCycle.DISABLE, priority = 100)
    fun disable() = RuneRuntime.disable()
}
