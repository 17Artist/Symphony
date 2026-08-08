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

import com.github.jengelman.gradle.plugins.shadow.tasks.ShadowJar
import java.util.zip.ZipFile

plugins {
    kotlin("jvm")
    id("priv.seventeen.artist.blink")
    id("com.github.johnrengelman.shadow")
}

blink {
    name.set("SymphonyDamageWatch")
    version.set(project.version.toString())
    description.set("用于观察 Symphony 伤害事件的运行时插件")
    authors.set(listOf("17Artist"))
    packageName.set("priv.seventeen.artist.symphony.damagewatch")
    apiVersion.set("1.18")
    logPrefix.set("§6♬ §c伤害观察")
    depend.set(listOf("Symphony"))
    foliaSupported.set(false)
    enableAria.set(false)
    enableAsteroid.set(false)
    enableScript.set(false)
}

dependencies {
    implementation("priv.seventeen.artist.blink:blink-common:${property("blinkVersion")}")
    compileOnly(project(":symphony-api"))
    compileOnly("org.spigotmc:spigot-api:${property("spigotVersion")}")
}

tasks.shadowJar {
    archiveClassifier.set("")
}

val verifyRuntimeBoundary by tasks.registering {
    dependsOn(tasks.shadowJar)
    doLast {
        val published = tasks.named<ShadowJar>("shadowJar").get().archiveFile.get().asFile
        val forbiddenPrefixes = listOf(
            "kotlin/", "kotlinx/", "org/bukkit/", "net/minecraft/",
            "priv/seventeen/artist/blink/", "priv/seventeen/artist/aria/", "priv/seventeen/artist/asteroid/",
            "priv/seventeen/artist/symphony/api/", "org/snakeyaml/"
        )
        ZipFile(published).use { zip ->
            val entries = zip.entries().asSequence().map { it.name }.toList()
            val forbidden = entries.filter { entry -> forbiddenPrefixes.any(entry::startsWith) }
            check(forbidden.isEmpty()) { "DamageWatch JAR 包含不应打包的共享类：${forbidden.take(20)}" }
            val descriptor = zip.getInputStream(requireNotNull(zip.getEntry("plugin.yml"))).bufferedReader().use { it.readText() }
            check("depend:" in descriptor && "Symphony" in descriptor) { "DamageWatch 必须依赖 Symphony" }
            check(entries.any { it == "priv/seventeen/artist/symphony/damagewatch/blink/bootstrap/KotlinBootstrap.class" }) {
                "DamageWatch 缺少插件内部重定位后的 Blink 运行库"
            }
        }
    }
}

tasks.named("check") { dependsOn(verifyRuntimeBoundary) }
tasks.named("build") { dependsOn("shadowJar") }

kotlin {
    jvmToolchain(17)
}
