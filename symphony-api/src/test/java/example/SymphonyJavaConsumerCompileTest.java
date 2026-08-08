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

package example;

import java.util.List;
import org.bukkit.Bukkit;
import org.bukkit.NamespacedKey;
import org.bukkit.entity.LivingEntity;
import org.bukkit.plugin.Plugin;
import priv.seventeen.artist.symphony.api.attribute.AttributeKey;
import priv.seventeen.artist.symphony.api.attribute.AttributeModifier;
import priv.seventeen.artist.symphony.api.attribute.AttributeOperation;
import priv.seventeen.artist.symphony.api.attribute.AttributeProvider;
import priv.seventeen.artist.symphony.api.attribute.AttributeSourceKey;
import priv.seventeen.artist.symphony.api.level.LevelProvider;
import priv.seventeen.artist.symphony.api.level.ProvidedLevel;
import priv.seventeen.artist.symphony.api.service.SymphonyApi;

/** 与公开 Symphony Wiki 对应的编译样例；有意避免依赖正在运行的服务端。 */
public final class SymphonyJavaConsumerCompileTest {
    public static SymphonyApi service() {
        return Bukkit.getServicesManager().load(SymphonyApi.class);
    }

    public static void replaceSource(SymphonyApi api, LivingEntity entity) {
        AttributeKey health = AttributeKey.symphony("max_health");
        api.getSources().replaceSource(
            entity,
            new AttributeSourceKey("example", "quest-reward"),
            List.of(new AttributeModifier("health", health, AttributeOperation.ADD, 20.0, 0, false, null, "Quest reward"))
        );
    }

    public static void damage(SymphonyApi api, LivingEntity attacker, LivingEntity victim) {
        api.getDamage().attack(attacker, victim);
        api.getDamage().attack(attacker, victim, 1.5D);
        api.getDamage().damage(attacker, victim, "arcane", 15.0, "example:spell");
    }

    public static void provider(SymphonyApi api, Plugin owner) {
        AttributeProvider provider = new AttributeProvider() {
            @Override public NamespacedKey getId() { return new NamespacedKey(owner, "example-provider"); }
            @Override public List<AttributeModifier> modifiers(LivingEntity entity, priv.seventeen.artist.symphony.api.attribute.AttributeProviderContext context) {
                return List.of();
            }
        };
        api.getAttributes().registerProvider(owner, provider, 0);
    }

    public static void levelProvider(SymphonyApi api, Plugin owner) {
        LevelProvider provider = new LevelProvider() {
            @Override public NamespacedKey getId() { return new NamespacedKey(owner, "character-level"); }
            @Override public String getDisplayName() { return "Example Role"; }
            @Override public ProvidedLevel snapshot(LivingEntity entity) {
                return new ProvidedLevel(35, 1200L, 2000L, "role-1", "Warrior", java.util.Map.of("rank", "A"));
            }
        };
        api.getLevels().registerProvider(owner, provider, 100);
    }

    private SymphonyJavaConsumerCompileTest() {}
}
