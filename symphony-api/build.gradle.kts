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

plugins {
    kotlin("jvm")
    `java-library`
    `maven-publish`
}

dependencies {
    compileOnly("org.spigotmc:spigot-api:${property("spigotVersion")}")
    testImplementation(kotlin("test-junit5"))
    testImplementation("org.spigotmc:spigot-api:${property("spigotVersion")}")
}

kotlin {
    jvmToolchain(17)
}

java {
    withSourcesJar()
}

publishing {
    publications {
        create<MavenPublication>("mavenJava") {
            from(components["java"])
            artifactId = "symphony-api"
            pom {
                name.set("Symphony API")
                description.set("Symphony 属性、伤害与物品能力的公共接入 API")
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
