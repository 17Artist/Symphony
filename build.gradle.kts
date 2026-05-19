plugins {
    kotlin("jvm") version "1.8.22" apply false
}

allprojects {
    group = "symphony"
    version = "1.0.0"

    repositories {
        maven("https://repo.arcartx.com/repository/maven-public/")
        maven("https://maven.aliyun.com/repository/central")
        maven("https://maven.aliyun.com/repository/gradle-plugin")
        maven("https://mvn.lumine.io/repository/maven-public/")
        mavenCentral()
        maven("https://hub.spigotmc.org/nexus/content/repositories/snapshots/")
        maven("https://repo.extendedclip.com/content/repositories/placeholderapi/")
    }
}

subprojects {
    apply(plugin = "org.jetbrains.kotlin.jvm")

    dependencies {
        "compileOnly"("org.spigotmc:spigot-api:1.18.2-R0.1-SNAPSHOT")
        "testImplementation"("org.junit.jupiter:junit-jupiter:5.10.1")
        "testImplementation"("org.junit.jupiter:junit-jupiter-params:5.10.1")
        "testImplementation"(kotlin("test"))
    }

    configure<org.jetbrains.kotlin.gradle.dsl.KotlinJvmProjectExtension> {
        jvmToolchain(17)
    }

    tasks.withType<Test> {
        useJUnitPlatform()
    }
}
