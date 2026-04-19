package priv.seventeen.artist.symphony.core.script

import priv.seventeen.artist.blink.BlinkLog
import priv.seventeen.artist.blink.script.AriaScriptManager
import priv.seventeen.artist.symphony.core.script.annotation.AttributeAnnotationProcessor
import priv.seventeen.artist.symphony.core.script.namespace.NamespaceRegistrar
import java.io.File

/**
 * Symphony Aria 脚本引擎封装。
 * 直接使用 AriaScriptManager（Blink 自动初始化 Aria）。
 */
class SymphonyScriptEngine {
    private var initialized = false

    fun initialize(dataFolder: File) {
        BlinkLog.info("初始化脚本引擎...")
        val scriptsDir = File(dataFolder, "scripts")
        if (!scriptsDir.exists()) scriptsDir.mkdirs()
        listOf("attributes", "mechanics", "formulas", "skills", "conditions", "modules").forEach {
            File(scriptsDir, it).mkdirs()
        }

        if (!AriaScriptManager.isAvailable) {
            BlinkLog.error("Aria 脚本引擎不可用，请检查 Blink 配置中 enableAria 是否为 true")
            return
        }

        // 注册 symphony.* 命名空间到 Aria
        NamespaceRegistrar.registerAll()

        initialized = true
        BlinkLog.success("Aria 脚本引擎就绪")
    }

    fun isInitialized(): Boolean = initialized

    fun loadAttributeScripts(dataFolder: File) {
        if (!initialized) return
        val dir = File(dataFolder, "scripts/attributes")
        if (!dir.exists()) return
        val files = dir.walkTopDown().filter { it.isFile && it.extension == "aria" }.toList()
        if (files.isEmpty()) {
            BlinkLog.info("scripts/attributes 目录为空，跳过加载")
            return
        }
        BlinkLog.info("加载 §b${files.size} §f个属性脚本...")
        for (file in files) {
            try {
                AriaScriptManager.evalFile(file)
                BlinkLog.detail("  §7已执行: §f${file.relativeTo(dir).path.replace('\\', '/')}")
            } catch (e: Exception) {
                BlinkLog.warn("  属性脚本执行失败 ${file.name}: ${e.message}")
            }
        }
        // 注解处理：将所有 @attribute 类聚合后注册到 AttributeRegistry
        try {
            val n = AttributeAnnotationProcessor.process()
            BlinkLog.success("注解处理完成 — 注册 §b${n} §f个属性")
        } catch (e: Exception) {
            BlinkLog.error("属性注解处理失败: ${e.message}")
            e.printStackTrace()
        }
    }

    fun loadFormulaScripts(dataFolder: File, engine: FormulaEngine) {
        if (!initialized) return
        val dir = File(dataFolder, "scripts/formulas")
        if (!dir.exists()) return
        val files = dir.listFiles { f -> f.extension == "aria" } ?: return
        BlinkLog.info("加载 §b${files.size} §f个公式脚本...")
        for (file in files) {
            try {
                engine.register(file.nameWithoutExtension, file.readText())
                BlinkLog.detail("  §7已注册公式: §f${file.nameWithoutExtension}")
            } catch (e: Exception) {
                BlinkLog.warn("  公式脚本加载失败 ${file.name}: ${e.message}")
            }
        }
    }

    fun loadMechanicsScripts(dataFolder: File) {
        if (!initialized) return
        val dir = File(dataFolder, "scripts/mechanics")
        if (!dir.exists()) return
        val files = dir.listFiles { f -> f.extension == "aria" } ?: return
        BlinkLog.info("加载 §b${files.size} §f个机制脚本...")
        for (file in files) {
            try {
                AriaScriptManager.evalFile(file)
                BlinkLog.detail("  §7已执行: §f${file.name}")
            } catch (e: Exception) {
                BlinkLog.warn("  机制脚本执行失败 ${file.name}: ${e.message}")
            }
        }
    }

    fun eval(code: String, vars: Map<String, Any> = emptyMap()): Any? {
        if (!initialized) return null
        return try {
            AriaScriptManager.eval(code, vars)
        } catch (e: Exception) {
            BlinkLog.warn("脚本执行失败: ${e.message}")
            null
        }
    }

    fun evalFile(file: File): Any? {
        if (!initialized) return null
        return try {
            AriaScriptManager.evalFile(file)
        } catch (e: Exception) {
            BlinkLog.warn("脚本文件执行失败 ${file.name}: ${e.message}")
            null
        }
    }

    fun compile(name: String, code: String): Any? {
        if (!initialized) return null
        return try {
            AriaScriptManager.compile(name, code)
        } catch (e: Exception) {
            BlinkLog.warn("脚本编译失败 $name: ${e.message}")
            null
        }
    }

    fun shutdown() {
        initialized = false
        BlinkLog.info("脚本引擎已关闭")
    }
}
