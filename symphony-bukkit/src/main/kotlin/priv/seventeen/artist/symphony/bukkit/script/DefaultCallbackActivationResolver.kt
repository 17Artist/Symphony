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

package priv.seventeen.artist.symphony.bukkit.script

import priv.seventeen.artist.symphony.engine.attribute.AttributeStateStore
import priv.seventeen.artist.symphony.engine.trigger.EntityTriggerContext
import priv.seventeen.artist.symphony.engine.config.FeatureSettings

class DefaultCallbackActivationResolver(
    private val store: AttributeStateStore,
    private val features: FeatureSettings,
    private val statusVariables: (CallbackOwner, EntityTriggerContext) -> Map<String, Any?>? = { _, _ -> null },
    private val environmentVariables: (CallbackOwner, EntityTriggerContext) -> Map<String, Any?>? = { _, _ -> null },
    private val passiveVariables: (CallbackOwner, EntityTriggerContext) -> Map<String, Any?>? = { _, _ -> null }
) : CallbackActivationResolver {
    override fun variables(owner: CallbackOwner, context: EntityTriggerContext): Map<String, Any?>? {
        return when (owner.kind) {
            CallbackOwnerKind.ATTRIBUTE -> {
                val calculating = context.values["attribute"]?.toString()
                if (calculating == null || calculating == owner.id) mapOf("attributeId" to owner.id) else null
            }
            CallbackOwnerKind.SET -> {
                val threshold = owner.qualifier?.toIntOrNull() ?: return null
                if ((owner.id to threshold) in store.stateIfPresent(context.self.uniqueId)?.setResolution?.activeThresholds.orEmpty()) {
                    mapOf("setId" to owner.id, "threshold" to threshold)
                } else null
            }
            CallbackOwnerKind.AFFIX -> if (!features.affixes) null else store.stateIfPresent(context.self.uniqueId)?.sources?.values.orEmpty().asSequence()
                .mapNotNull { it.item }
                .flatMap { it.affixes.asSequence() }
                .firstOrNull { it.id.toString() == owner.id }
                ?.let { feature -> feature.parameters + mapOf("affixId" to owner.id, "level" to feature.level) }
            CallbackOwnerKind.SKILL -> if (features.skills && context.values["skill"] == owner.id) emptyMap() else null
            CallbackOwnerKind.STATUS -> if (features.statuses) statusVariables(owner, context) else null
            CallbackOwnerKind.ENVIRONMENT -> if (features.environments) environmentVariables(owner, context) else null
            CallbackOwnerKind.RESONANCE -> if (features.resonances) passiveVariables(owner, context) else null
            CallbackOwnerKind.TALENT -> if (features.talents) passiveVariables(owner, context) else null
        }
    }
}
