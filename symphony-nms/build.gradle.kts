// symphony-nms: NMS 适配层接口 + Asteroid/Bukkit 实现
dependencies {
    api(project(":symphony-common"))
    // Asteroid NMS — 运行时由 Blink Shadow 打入，编译期仅需 compileOnly
    compileOnly("priv.seventeen.artist.asteroid:asteroid-nms:1.0.1")
    compileOnly("priv.seventeen.artist.blink:blink-common:1.1.2")
}
