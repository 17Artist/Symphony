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

package priv.seventeen.artist.symphony.engine.definition

import priv.seventeen.artist.symphony.api.attribute.AttributeKey
import priv.seventeen.artist.symphony.engine.attribute.AttributeGraph
import java.util.concurrent.atomic.AtomicLong
import java.util.concurrent.atomic.AtomicReference

data class CompiledDefinitions(
    val snapshot: DefinitionSnapshot,
    val graph: AttributeGraph,
    val interactionsByTarget: Map<AttributeKey, List<InteractionDefinition>>
)

class DefinitionRepository(initial: DefinitionSnapshot = DefinitionSnapshot.empty()) {
    private val revisionSequence = AtomicLong(initial.revision)
    private val active = AtomicReference(compile(initial))

    fun current(): CompiledDefinitions = active.get()

    /** 仅在整份候选定义通过校验后分配下一个单调递增的修订号。 */
    fun commit(candidate: DefinitionSnapshot): CompiledDefinitions {
        val graph = AttributeGraph.build(candidate.attributes)
        val committed = candidate.copy(revision = revisionSequence.incrementAndGet())
        return CompiledDefinitions(committed, graph, interactionIndex(committed)).also(active::set)
    }

    private companion object {
        fun compile(snapshot: DefinitionSnapshot): CompiledDefinitions = CompiledDefinitions(
            snapshot,
            AttributeGraph.build(snapshot.attributes),
            interactionIndex(snapshot)
        )

        fun interactionIndex(snapshot: DefinitionSnapshot): Map<AttributeKey, List<InteractionDefinition>> =
            snapshot.interactions.values.groupBy(InteractionDefinition::target).mapValues { (_, values) ->
                values.sortedBy(InteractionDefinition::id)
            }
    }
}
