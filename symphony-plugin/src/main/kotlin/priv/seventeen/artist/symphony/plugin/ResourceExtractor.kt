package priv.seventeen.artist.symphony.plugin

import priv.seventeen.artist.blink.BlinkLog
import priv.seventeen.artist.blink.bukkitPlugin
import java.io.File
import java.io.InputStream
import java.util.jar.JarFile

/**
 * 资源文件释放器 — 首次启动时从 JAR 中释放默认配置和数据文件。
 */
object ResourceExtractor {

    private val directories = listOf(
        "affixes", "affix-pools", "skills", "sets", "resonances", "runes",
        "talents", "statuses", "interactions", "environments", "reactions",
        "config",
        "scripts/attributes", "scripts/mechanics", "scripts/formulas",
        "scripts/skills", "scripts/conditions", "scripts/modules"
    )

    fun extractDefaults() {
        val dataFolder = bukkitPlugin.dataFolder
        if (!dataFolder.exists()) dataFolder.mkdirs()

        for (dir in directories) {
            val targetDir = File(dataFolder, dir)
            if (!targetDir.exists()) targetDir.mkdirs()
        }

        // 从 JAR 中释放 assets/ 下的所有文件
        try {
            val jarFile = File(bukkitPlugin.javaClass.protectionDomain.codeSource.location.toURI())
            if (!jarFile.isFile) return

            JarFile(jarFile).use { jar ->
                val entries = jar.entries()
                while (entries.hasMoreElements()) {
                    val entry = entries.nextElement()
                    val name = entry.name

                    // 只处理 assets/ 目录下的文件（排除 config.yml，由 BlinkConfig 处理）
                    if (!name.startsWith("assets/") || entry.isDirectory) continue
                    val relativePath = name.removePrefix("assets/")
                    if (relativePath == "config.yml") continue  // BlinkConfig 已处理

                    val targetFile = File(dataFolder, relativePath)
                    if (targetFile.exists()) continue  // 不覆盖已有文件

                    // 确保父目录存在
                    targetFile.parentFile?.mkdirs()

                    // 释放文件
                    jar.getInputStream(entry).use { input ->
                        targetFile.outputStream().use { output ->
                            input.copyTo(output)
                        }
                    }
                    BlinkLog.detail("  释放: $relativePath")
                }
            }
        } catch (e: Exception) {
            BlinkLog.warn("资源文件释放失败: ${e.message}")
        }
    }
}
