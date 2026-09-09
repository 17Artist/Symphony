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

package priv.seventeen.artist.symphony.bukkit.compat

import org.bukkit.Keyed
import org.bukkit.NamespacedKey
import java.util.Locale

/**
 * 屏蔽 Bukkit 在旧枚举与新版注册表接口之间的二进制差异。
 *
 * 注册表字段通过反射读取，是因为 1.18 与 1.21 对同一注册表使用了不同字段名。
 * 找不到注册表时再读取静态常量，以兼容尚未提供对应注册表的旧服务端。
 */
internal object BukkitRegistryTypes {
    fun <T : Any> resolve(
        type: Class<T>,
        resourceCandidates: List<String>,
        registryFields: List<String>,
        staticFieldCandidates: List<String>
    ): T? = resolveRegistry(type, resourceCandidates, registryFields)
        ?: resolveStaticFields(type, staticFieldCandidates)

    fun enumName(raw: String): String = raw.trim()
        .substringAfter(':')
        .uppercase(Locale.ROOT)
        .replace('.', '_')
        .replace('-', '_')

    fun registryName(value: Keyed): String = value.key.key.uppercase(Locale.ROOT)

    private fun <T : Any> resolveRegistry(
        type: Class<T>,
        rawCandidates: List<String>,
        registryFields: List<String>
    ): T? {
        val keys = rawCandidates.mapNotNull(::resourceKey).distinct()
        if (keys.isEmpty()) return null
        return runCatching {
            val registryType = Class.forName("org.bukkit.Registry", false, type.classLoader)
            val get = registryType.getMethod("get", NamespacedKey::class.java)
            registryFields.firstNotNullOfOrNull { fieldName ->
                val registry = runCatching { registryType.getField(fieldName).get(null) }.getOrNull()
                    ?: return@firstNotNullOfOrNull null
                keys.firstNotNullOfOrNull { key ->
                    runCatching { get.invoke(registry, key) }.getOrNull()?.takeIf(type::isInstance)?.let(type::cast)
                }
            }
        }.getOrNull()
    }

    private fun resourceKey(raw: String): NamespacedKey? {
        val normalized = raw.trim().lowercase(Locale.ROOT)
        if (normalized.isEmpty()) return null
        return runCatching { NamespacedKey.fromString(normalized) }.getOrNull()
    }

    private fun <T : Any> resolveStaticFields(type: Class<T>, candidates: List<String>): T? =
        candidates.firstNotNullOfOrNull { candidate ->
            runCatching { type.getField(candidate).get(null) }
                .getOrNull()
                ?.takeIf(type::isInstance)
                ?.let(type::cast)
        }
}
