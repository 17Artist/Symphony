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

package priv.seventeen.artist.symphony.engine.attribute

import priv.seventeen.artist.symphony.api.attribute.AttributeKey
import priv.seventeen.artist.symphony.engine.definition.CompiledAttributeDefinition

class AttributeGraph private constructor(
    val topologicalOrder: List<AttributeKey>,
    private val directDependents: Map<AttributeKey, Set<AttributeKey>>
) {
    fun affectedClosure(keys: Set<AttributeKey>): Set<AttributeKey> {
        if (keys.isEmpty()) return topologicalOrder.toSet()
        val result = LinkedHashSet<AttributeKey>()
        val queue = ArrayDeque<AttributeKey>()
        keys.sorted().forEach {
            if (result.add(it)) queue.addLast(it)
        }
        while (queue.isNotEmpty()) {
            directDependents[queue.removeFirst()].orEmpty().sorted().forEach {
                if (result.add(it)) queue.addLast(it)
            }
        }
        return result
    }

    companion object {
        fun build(definitions: Map<AttributeKey, CompiledAttributeDefinition>): AttributeGraph {
            val dependencies = definitions.mapValues { it.value.definition.dependsOn }
            val missing = dependencies.flatMap { (key, deps) ->
                deps.filterNot(definitions::containsKey).map { key to it }
            }
            require(missing.isEmpty()) {
                "缺少属性依赖：" + missing.joinToString { "${it.first}->${it.second}" }
            }

            val marks = HashMap<AttributeKey, Int>()
            val order = ArrayList<AttributeKey>(definitions.size)
            val path = ArrayDeque<AttributeKey>()

            fun visit(key: AttributeKey) {
                when (marks[key]) {
                    2 -> return
                    1 -> {
                        val cycle = (path.toList() + key).joinToString(" -> ")
                        throw IllegalArgumentException("属性依赖形成循环：$cycle")
                    }
                }
                marks[key] = 1
                path.addLast(key)
                dependencies[key].orEmpty().sorted().forEach(::visit)
                path.removeLast()
                marks[key] = 2
                order += key
            }

            definitions.keys.sorted().forEach(::visit)

            val dependents = linkedMapOf<AttributeKey, MutableSet<AttributeKey>>()
            dependencies.forEach { (key, deps) ->
                deps.forEach { dependency -> dependents.getOrPut(dependency, ::linkedSetOf).add(key) }
            }
            return AttributeGraph(order, dependents.mapValues { it.value.toSet() })
        }
    }
}
