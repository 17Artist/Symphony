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

package priv.seventeen.artist.symphony.api.level

import org.bukkit.NamespacedKey
import org.bukkit.entity.LivingEntity
import org.bukkit.plugin.Plugin
import priv.seventeen.artist.symphony.api.registration.RegistrationHandle

/**
 * 由等级提供者维护的快照，对应实体当前选中的角色。
 *
 * Symphony 不会保存或修改这些数据。角色插件始终是数据的唯一权威来源，
 * 切换角色后可以返回另一份快照。
 */
data class ProvidedLevel(
    val level: Int,
    val experience: Long? = null,
    val experienceForNextLevel: Long? = null,
    val characterId: String? = null,
    val characterName: String? = null,
    val metadata: Map<String, String> = emptyMap()
) {
    init {
        require(level >= 0) { "等级不能为负数" }
        require(experience == null || experience >= 0L) { "经验值不能为负数" }
        require(experienceForNextLevel == null || experienceForNextLevel > 0L) {
            "升到下一级所需经验必须大于零"
        }
        require(characterId == null || characterId.isNotBlank()) { "角色 ID 不能为空" }
        require(characterName == null || characterName.isNotBlank()) { "角色名称不能为空" }
        require(metadata.size <= 64) { "等级元数据最多包含 64 项" }
        require(metadata.all { (key, value) -> key.isNotBlank() && key.length <= 64 && value.length <= 256 }) {
            "等级元数据包含无效的键或值"
        }
    }
}

interface LevelProvider {
    val id: NamespacedKey
    val displayName: String

    /** 当前实体的等级数据不归此提供者管理时返回 null。 */
    fun snapshot(entity: LivingEntity): ProvidedLevel?
}

data class LevelSnapshot(
    val provider: NamespacedKey,
    val providerName: String,
    val level: Int,
    val experience: Long?,
    val experienceForNextLevel: Long?,
    val characterId: String?,
    val characterName: String?,
    val metadata: Map<String, String>
)

interface LevelService {
    /** 从持有该实体数据的提供者中选取优先级最高的一项。 */
    fun snapshot(entity: LivingEntity): LevelSnapshot?

    /**
     * 重新选择等级提供者，在需要时发布变更事件，并使可能依赖外部等级或角色数据的
     * 属性提供者失效。
     */
    fun refresh(entity: LivingEntity, reason: String): LevelSnapshot?

    fun registerProvider(
        owner: Plugin,
        provider: LevelProvider,
        priority: Int = 0
    ): RegistrationHandle
}
