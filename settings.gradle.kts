pluginManagement {
    repositories {
        maven("https://repo.arcartx.com/repository/maven-public/")
        maven("https://maven.aliyun.com/repository/central")
        maven("https://maven.aliyun.com/repository/gradle-plugin")
        gradlePluginPortal()
        mavenCentral()
    }
}

rootProject.name = "Symphony"

include("symphony-common")
include("symphony-core")
include("symphony-nms")
include("symphony-plugin")
