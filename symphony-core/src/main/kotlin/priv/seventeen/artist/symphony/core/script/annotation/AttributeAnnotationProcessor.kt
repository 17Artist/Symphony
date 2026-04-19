package priv.seventeen.artist.symphony.core.script.annotation

import priv.seventeen.artist.aria.Aria
import priv.seventeen.artist.aria.annotation.AnnotationRegistry
import priv.seventeen.artist.aria.annotation.AnnotationRegistry.AnnotatedTarget
import priv.seventeen.artist.aria.annotation.AriaAnnotation
import priv.seventeen.artist.aria.callable.ICallable
import priv.seventeen.artist.aria.value.IValue
import priv.seventeen.artist.blink.BlinkLog
import priv.seventeen.artist.symphony.core.attribute.AttributeDefinition
import priv.seventeen.artist.symphony.core.attribute.AttributeRegistry
import priv.seventeen.artist.symphony.core.script.AttributeCallableRegistry
import priv.seventeen.artist.symphony.core.script.ExpressionCompiler

/**
 * 属性注解处理器。
 *
 * 扫描 Aria 引擎中所有被 @attribute(...) 注解的类，读取同类上的兄弟注解
 * （@displayName / @category / @default / ... ）构建 AttributeDefinition 并注册。
 *
 * 设计要点：
 * - 注解在 .aria 脚本解析时即刻进入 AnnotationRegistry（engine 级共享）。
 * - 本处理器在所有属性脚本 evalFile 完成后调用一次，遍历 @attribute 类聚合。
 * - 属于同一类的兄弟注解通过 target.name() 关联（类级注解 className=null，
 *   name=类名；方法/字段注解 className=所属类名）。
 */
object AttributeAnnotationProcessor {

    private const val ANN_ATTRIBUTE = "attribute"

    /**
     * 已知注解键（用于未知注解告警）。未声明的可自行扩展。
     */
    private val knownClassAnnotations = setOf(
        ANN_ATTRIBUTE, "displayName", "description", "category",
        "default", "min", "max", "format", "priority",
        "vanillaBinding", "readonly", "tag", "tags",
        "dependsOn", "when",
        "defaultExpr", "minExpr", "maxExpr"
    )

    private val knownMethodAnnotations = setOf("derive", "onChange", "formula")

    /**
     * 处理所有 @attribute 注解，注册到 AttributeRegistry。
     *
     * @return 新增注册的属性数量
     */
    fun process(): Int {
        val registry: AnnotationRegistry = Aria.getEngine().annotationRegistry
        val all = registry.all
        // 以 @attribute 为锚
        val anchors = registry.findClassesByAnnotation(ANN_ATTRIBUTE)
        var registered = 0

        for (anchor in anchors) {
            val className = anchor.name()
            val id = anchor.annotation().firstString()
            if (id == null) {
                BlinkLog.warn("  类 §c${className} §f的 @attribute 缺少 id 参数，跳过")
                continue
            }

            // 同类上的所有注解（kind=CLASS 且 name==className，
            // 或 kind=METHOD/FIELD 且 className==className）
            val classAnnos = mutableListOf<AriaAnnotation>()
            val memberTargets = mutableListOf<AnnotatedTarget>()
            for (t in all) {
                when (t.kind()) {
                    AnnotatedTarget.TargetKind.CLASS ->
                        if (t.name() == className) classAnnos += t.annotation()
                    AnnotatedTarget.TargetKind.METHOD,
                    AnnotatedTarget.TargetKind.FIELD ->
                        if (className == t.className()) memberTargets += t
                    else -> {}
                }
            }

            // 告警未知注解
            classAnnos.forEach { a ->
                if (a.name() !in knownClassAnnotations) {
                    BlinkLog.warn("  §c${className} §f使用了未知类注解 §e@${a.name()}")
                }
            }
            memberTargets.forEach { t ->
                if (t.annotation().name() !in knownMethodAnnotations) {
                    BlinkLog.warn("  §c${className}.${t.name()} §f使用了未知方法/字段注解 §e@${t.annotation().name()}")
                }
            }

            val def = buildDefinition(id, className, classAnnos, memberTargets) ?: continue
            AttributeRegistry.register(def)
            registered++
        }
        // 静态依赖校验
        validateDependencies()
        return registered
    }

    private fun validateDependencies() {
        val allIds = AttributeRegistry.ids()
        for (def in AttributeRegistry.getAll()) {
            for (dep in def.dependsOn) {
                if (dep !in allIds) {
                    BlinkLog.warn("属性 §c${def.id} §f声明的依赖 §c${dep} §f不存在")
                }
            }
        }
    }

    private fun buildDefinition(
        id: String,
        className: String,
        classAnnos: List<AriaAnnotation>,
        memberTargets: List<AnnotatedTarget>
    ): AttributeDefinition? {
        var displayName: String = id
        var description: String = ""
        var category: String = "custom"
        var defaultValue: Double = 0.0
        var minValue: Double = -Double.MAX_VALUE
        var maxValue: Double = Double.MAX_VALUE
        var format: String = "number"
        var priority: Int = 0
        var vanillaBinding: String? = null
        var readonly: Boolean = false
        val tags = mutableListOf<String>()
        val dependsOn = mutableListOf<String>()
        val whenConditions = mutableListOf<String>()
        var defaultExpr: String? = null
        var minExpr: String? = null
        var maxExpr: String? = null

        for (a in classAnnos) {
            when (a.name()) {
                ANN_ATTRIBUTE -> {} // 锚点
                "displayName" -> a.firstString()?.let { displayName = it }
                "description" -> a.firstString()?.let { description = it }
                "category" -> a.firstString()?.let { category = it }
                "default" -> a.firstDouble()?.let { defaultValue = it }
                "min" -> a.firstDouble()?.let { minValue = it }
                "max" -> a.firstDouble()?.let { maxValue = it }
                "format" -> a.firstString()?.let { format = it }
                "priority" -> a.firstDouble()?.let { priority = it.toInt() }
                "vanillaBinding" -> a.firstString()?.let { vanillaBinding = it }
                "readonly" -> readonly = a.firstBoolean() ?: true
                "tag" -> a.firstString()?.let { tags += it }
                "tags" -> {
                    for (i in 0 until a.argCount()) {
                        a.getArg(i)?.let { v -> collectStrings(v, tags) }
                    }
                }
                "dependsOn" -> {
                    for (i in 0 until a.argCount()) {
                        a.getArg(i)?.let { v -> collectStrings(v, dependsOn) }
                    }
                }
                "when" -> {
                    for (i in 0 until a.argCount()) {
                        a.getArg(i)?.let { v -> collectStrings(v, whenConditions) }
                    }
                }
                "defaultExpr" -> a.firstString()?.let { defaultExpr = it }
                "minExpr" -> a.firstString()?.let { minExpr = it }
                "maxExpr" -> a.firstString()?.let { maxExpr = it }
            }
        }

        // 方法注解：捕获 FunctionValue/ICallable 存入 AttributeCallableRegistry
        val deriveTarget = memberTargets.firstOrNull { it.annotation().name() == "derive" }
        val formulaTarget = memberTargets.firstOrNull { it.annotation().name() == "formula" }
        val onChangeTarget = memberTargets.firstOrNull { it.annotation().name() == "onChange" }

        val deriveId = deriveTarget?.let { "${className}#${it.name()}" }
        val formulaId = formulaTarget?.let { "${className}#${it.name()}" }
        val onChangeId = onChangeTarget?.let { "${className}#${it.name()}" }

        deriveTarget?.asCallable()?.let { AttributeCallableRegistry.putDerive(id, it) }
        formulaTarget?.asCallable()?.let { AttributeCallableRegistry.putFormula(id, it) }
        onChangeTarget?.asCallable()?.let { AttributeCallableRegistry.putOnChange(id, it) }

        // 编译表达式到 AttributeCallableRegistry
        defaultExpr?.let { ExpressionCompiler.compile(id, "default", it)?.let { c -> AttributeCallableRegistry.putDefaultExpr(id, c) } }
        minExpr?.let { ExpressionCompiler.compile(id, "min", it)?.let { c -> AttributeCallableRegistry.putMinExpr(id, c) } }
        maxExpr?.let { ExpressionCompiler.compile(id, "max", it)?.let { c -> AttributeCallableRegistry.putMaxExpr(id, c) } }

        return AttributeDefinition(
            id = id,
            displayName = displayName,
            description = description,
            category = category,
            defaultValue = defaultValue,
            minValue = minValue,
            maxValue = maxValue,
            format = format,
            priority = priority,
            vanillaBinding = vanillaBinding,
            readonly = readonly,
            tags = tags.toList(),
            formulaId = formulaId,
            deriveId = deriveId,
            onChangeId = onChangeId,
            dependsOn = dependsOn.toList(),
            whenConditions = whenConditions.toList()
        )
    }

    private fun AnnotatedTarget.asCallable(): ICallable? {
        val v = this.value() ?: return null
        return when (val jvm = v.jvmValue()) {
            is ICallable -> jvm
            else -> null
        }
    }

    //  注解参数取值辅助 
    private fun AriaAnnotation.argCount(): Int {
        val arr = this.args() ?: return 0
        return arr.size
    }

    private fun AriaAnnotation.firstString(): String? {
        if (argCount() == 0) return null
        return try { getArg(0)?.stringValue() } catch (_: Exception) { null }
    }

    private fun AriaAnnotation.firstDouble(): Double? {
        if (argCount() == 0) return null
        return try { getArg(0)?.numberValue() } catch (_: Exception) { null }
    }

    private fun AriaAnnotation.firstBoolean(): Boolean? {
        if (argCount() == 0) return null
        return try { getArg(0)?.booleanValue() } catch (_: Exception) { null }
    }

    private fun collectStrings(v: IValue<*>, out: MutableList<String>) {
        val jvm = v.jvmValue()
        when (jvm) {
            is String -> out += jvm
            is List<*> -> jvm.forEach { el ->
                when (el) {
                    is String -> out += el
                    is IValue<*> -> out += el.stringValue()
                    else -> out += el?.toString() ?: ""
                }
            }
            else -> out += v.stringValue()
        }
    }
}
