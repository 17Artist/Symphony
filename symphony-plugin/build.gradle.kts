plugins {
    id("priv.seventeen.artist.blink") version "1.1.2"
    id("com.github.johnrengelman.shadow") version "8.1.1"
}

blink {
    name.set("Symphony")
    version.set("1.0.0")
    description.set("全能属性系统插件 — 脚本驱动、词条、触发器、技能、成长、元素反应")
    authors.set(listOf("Symphony Team"))
    apiVersion.set("1.18")
    packageName.set("priv.seventeen.artist.symphony.plugin")
    depend.set(listOf())
    softDepend.set(listOf("PlaceholderAPI", "MythicMobs"))
    foliaSupported.set(false)
    kotlinVersion.set("1.8.22")
    logPrefix.set("§6♦ §bSymphony")
    obfuscate.set(false)
    enableScript.set(false)
    enableAria.set(true)
    enableAsteroid.set(true)
}

dependencies {
    implementation(project(":symphony-common"))
    implementation(project(":symphony-core"))
    implementation(project(":symphony-nms"))
    implementation("priv.seventeen.artist.blink:blink-common:1.1.2")
    compileOnly("com.google.code.gson:gson:2.10.1")
    compileOnly("me.clip:placeholderapi:2.11.6")
}

tasks.shadowJar {
    archiveClassifier.set("")
}

tasks.named("build") {
    dependsOn("shadowJar")
}
