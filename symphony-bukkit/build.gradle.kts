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
    `maven-publish`
}

val overtureDependency = "priv.seventeen.artist.overture:overture:${property("overtureVersion")}"

blink {
    name.set("Symphony")
    version.set(project.version.toString())
    description.set("可配置的属性、战斗与装备能力引擎")
    authors.set(listOf("17Artist"))
    packageName.set("priv.seventeen.artist.symphony.bukkit")
    apiVersion.set("1.18")
    logPrefix.set("§6♦ §cSymphony")
    depend.set(listOf("Overture"))
    softDepend.set(listOf("PlaceholderAPI", "MythicMobs"))
    foliaSupported.set(false)
    enableAria.set(true)
    enableAsteroid.set(true)
    enableScript.set(false)
    libraries.set(
        listOf(
            "org.snakeyaml:snakeyaml-engine:2.7"
        )
    )
}

dependencies {
    implementation(project(":symphony-api"))
    implementation(project(":symphony-engine"))
    implementation(project(":symphony-overture"))
    implementation(project(":symphony-integrations"))
    implementation("priv.seventeen.artist.blink:blink-common:${property("blinkVersion")}")
    compileOnly("priv.seventeen.artist.aria:aria:${property("ariaVersion")}")
    compileOnly("org.spigotmc:spigot-api:${property("spigotVersion")}")
    compileOnly(overtureDependency)

    testImplementation(kotlin("test-junit5"))
    testImplementation(overtureDependency)
    testImplementation("priv.seventeen.artist.aria:aria:${property("ariaVersion")}")
    testImplementation("org.spigotmc:spigot-api:${property("spigotVersion")}")
    testImplementation("org.snakeyaml:snakeyaml-engine:2.7")
}

tasks.shadowJar {
    archiveClassifier.set("")
}

// 将普通模块 JAR 与可部署的 Shadow JAR 输出到不同路径。
// 如果两者共用同一输出，clean build 与 Maven 发布同时运行时，Gradle 将无法判断产物来源。
tasks.jar {
    archiveClassifier.set("plain")
}

val verifyRuntimeBoundary by tasks.registering {
    dependsOn(tasks.shadowJar)
    doLast {
        val published = tasks.named<ShadowJar>("shadowJar").get().archiveFile.get().asFile
        val forbiddenPrefixes = listOf(
            "kotlin/", "kotlinx/",
            "org/bukkit/", "net/minecraft/",
            "priv/seventeen/artist/blink/",
            "priv/seventeen/artist/aria/", "priv/seventeen/artist/asteroid/",
            "priv/seventeen/artist/overture/",
            "priv/seventeen/artist/arcartx/",
            "com/zaxxer/hikari/", "org/sqlite/", "com/mysql/",
            "org/snakeyaml/", "priv/seventeen/artist/symphony/libs/",
            "me/clip/placeholderapi/", "io/lumine/mythic/"
        )
        ZipFile(published).use { zip ->
            val entries = zip.entries().asSequence().map { it.name }.toList()
            val forbidden = entries.filter { entry -> forbiddenPrefixes.any(entry::startsWith) }
            check(forbidden.isEmpty()) {
                "发布版 Symphony JAR 包含不应打包的服务端类、共享类或运行库类：${forbidden.take(20)}"
            }
        }
    }
}

tasks.named("check") {
    dependsOn(verifyRuntimeBoundary)
}

tasks.named("build") {
    dependsOn("shadowJar")
}

val symphonyConfigPack = providers.gradleProperty("symphonyConfigPack")
tasks.register<Test>("validateConfigPack") {
    group = "verification"
    description = "使用正式加载器校验 Symphony 与 Overture 配置包"
    dependsOn(tasks.testClasses)
    testClassesDirs = sourceSets["test"].output.classesDirs
    classpath = sourceSets["test"].runtimeClasspath
    filter {
        includeTestsMatching("priv.seventeen.artist.symphony.bukkit.ConfigPackValidationTest")
    }
    if (symphonyConfigPack.isPresent) {
        val pack = file(symphonyConfigPack.get())
        inputs.dir(pack)
        systemProperty("symphony.config.pack", pack.absolutePath)
    }
    outputs.upToDateWhen { false }
    doFirst {
        require(symphonyConfigPack.isPresent) {
            "请使用 -PsymphonyConfigPack=<包含 Symphony/ 与可选 Overture/ 的目录> 指定配置包"
        }
    }
}

publishing {
    publications {
        create<MavenPublication>("plugin") {
            artifact(tasks.shadowJar)
            artifactId = "symphony"
            pom {
                name.set("Symphony")
                description.set("面向 Bukkit 服务器的可配置属性、战斗与装备能力引擎")
                url.set("https://github.com/17Artist/Symphony")
                licenses {
                    license {
                        name.set("Apache License 2.0")
                        url.set("https://www.apache.org/licenses/LICENSE-2.0.txt")
                        distribution.set("repo")
                    }
                }
                developers {
                    developer {
                        id.set("17Artist")
                        name.set("17Artist")
                    }
                }
                scm {
                    url.set("https://github.com/17Artist/Symphony")
                    connection.set("scm:git:https://github.com/17Artist/Symphony.git")
                    developerConnection.set("scm:git:ssh://git@github.com/17Artist/Symphony.git")
                }
            }
        }
    }
}

kotlin {
    jvmToolchain(17)
}
