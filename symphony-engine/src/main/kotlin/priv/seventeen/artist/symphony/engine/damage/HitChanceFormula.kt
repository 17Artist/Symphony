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

package priv.seventeen.artist.symphony.engine.damage

object HitChanceFormula {
    fun dodgeChance(accuracy: Double, dodge: Double): Double {
        require(accuracy.isFinite() && dodge.isFinite()) { "命中与闪避属性必须是有限数" }
        return (dodge - (accuracy - 1.0)).coerceIn(0.0, 0.9)
    }
}
