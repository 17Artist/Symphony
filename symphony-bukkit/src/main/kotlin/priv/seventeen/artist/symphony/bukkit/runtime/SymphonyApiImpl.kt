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

package priv.seventeen.artist.symphony.bukkit.runtime

import priv.seventeen.artist.symphony.api.attribute.AttributeService
import priv.seventeen.artist.symphony.api.damage.DamageService
import priv.seventeen.artist.symphony.api.service.DefinitionService
import priv.seventeen.artist.symphony.api.service.ItemAttributeService
import priv.seventeen.artist.symphony.api.level.LevelService
import priv.seventeen.artist.symphony.api.power.CombatPowerService
import priv.seventeen.artist.symphony.api.service.MetadataService
import priv.seventeen.artist.symphony.api.service.AffixService
import priv.seventeen.artist.symphony.api.service.SkillService
import priv.seventeen.artist.symphony.api.service.SymphonyApi
import priv.seventeen.artist.symphony.api.source.AttributeSourceService
import priv.seventeen.artist.symphony.api.trigger.TriggerService

internal data class SymphonyApiImpl(
    override val attributes: AttributeService,
    override val sources: AttributeSourceService,
    override val damage: DamageService,
    override val triggers: TriggerService,
    override val definitions: DefinitionService,
    override val items: ItemAttributeService,
    override val skills: SkillService,
    override val levels: LevelService,
    override val metadata: MetadataService,
    override val affixes: AffixService,
    override val combatPower: CombatPowerService
) : SymphonyApi
