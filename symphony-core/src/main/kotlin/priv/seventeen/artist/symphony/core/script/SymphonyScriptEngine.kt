package priv.seventeen.artist.symphony.core.script

import priv.seventeen.artist.aria.Aria
import priv.seventeen.artist.aria.api.AriaCompiledRoutine
import priv.seventeen.artist.aria.callable.NativeCallable
import priv.seventeen.artist.aria.context.Context
import priv.seventeen.artist.aria.context.VariableKey
import priv.seventeen.artist.blink.BlinkLog
import priv.seventeen.artist.blink.script.AriaScriptManager
import priv.seventeen.artist.symphony.core.script.annotation.AttributeAnnotationProcessor
import priv.seventeen.artist.symphony.core.script.namespace.NamespaceRegistrar
import java.io.File
import java.util.concurrent.ConcurrentHashMap

/**
 * Symphony Aria 脚本引擎封装。
 * 所有脚本在加载时预编译为 [AriaCompiledRoutine]，运行时直接执行编译产物。
 */
class SymphonyScriptEngine {
    private var initialized = false

    /** code hash → 编译产物缓存 */
    private val routineCache = ConcurrentHashMap<Int, AriaCompiledRoutine>()
    private var nameCounter = 0

    companion object {
        /** 向 Aria Context 注入变量的工具方法 */
        fun injectVars(ctx: Context, vars: Map<String, Any?>) {
            val gs = ctx.globalStorage
            for ((key, value) in vars) {
                gs.getGlobalVariable(VariableKey.of(key)).setValue(NativeCallable.wrapObject(value))
            }
        }
    }

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
                // 属性脚本需要执行注解注册，用 evalFile 走全局上下文
                AriaScriptManager.evalFile(file)
                BlinkLog.detail("  §7已执行: §f${file.relativeTo(dir).path.replace('\\', '/')}")
            } catch (e: Exception) {
                BlinkLog.warn("  属性脚本执行失败 ${file.name}: ${e.message}")
            }
        }
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

    /**
     * 编译并缓存脚本，相同 code 只编译一次。
     */
    fun compile(name: String, code: String): AriaCompiledRoutine? {
        if (!initialized) return null
        return try {
            routineCache.computeIfAbsent(code.hashCode()) {
                Aria.compile(name, code)
            }
        } catch (e: Exception) {
            BlinkLog.warn("脚本编译失败 $name: ${e.message}")
            null
        }
    }

    /**
     * 执行脚本。优先走编译缓存，每次创建新 Context 传入变量。
     */
    fun eval(code: String, vars: Map<String, Any> = emptyMap()): Any? {
        if (!initialized) return null
        return try {
            val routine = routineCache.computeIfAbsent(code.hashCode()) {
                Aria.compile("eval:${nameCounter++}", code)
            }
            val ctx = Aria.createContext()
            injectVars(ctx, vars)
            routine.execute(ctx)?.jvmValue()
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

    fun shutdown() {
        routineCache.clear()
        initialized = false
        BlinkLog.info("脚本引擎已关闭")
    }
}
