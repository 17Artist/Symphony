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
}

val overtureDependency = "priv.seventeen.artist.overture:overture:${property("overtureVersion")}"

dependencies {
    api(project(":symphony-api"))
    implementation(project(":symphony-engine"))
    compileOnly("org.spigotmc:spigot-api:${property("spigotVersion")}")
    compileOnly(overtureDependency)

    testImplementation(kotlin("test-junit5"))
    testImplementation(overtureDependency)
    testImplementation("org.snakeyaml:snakeyaml-engine:2.7")
    testImplementation("org.spigotmc:spigot-api:${property("spigotVersion")}")
}

kotlin {
    jvmToolchain(17)
}
