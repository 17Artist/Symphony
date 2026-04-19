package priv.seventeen.artist.symphony.core.script

import priv.seventeen.artist.aria.callable.ICallable
import java.util.concurrent.ConcurrentHashMap

/**
 * 派生属性函数注册表。
 *
 * 属性注解脚本中 @derive 标记的方法会被捕获为 Aria ICallable 并按属性 ID 存入此表。
 * AttributeCalculator 在计算 readonly 属性时查此表取函数执行。
 *
 * @onChange / @formula 同理（预留）。
 */
object AttributeCallableRegistry {
    private val derive = ConcurrentHashMap<String, ICallable>()
    private val onChange = ConcurrentHashMap<String, ICallable>()
    private val formula = ConcurrentHashMap<String, ICallable>()
    private val defaultExpr = ConcurrentHashMap<String, ICallable>()
    private val minExpr = ConcurrentHashMap<String, ICallable>()
    private val maxExpr = ConcurrentHashMap<String, ICallable>()

    fun putDerive(attrId: String, fn: ICallable) { derive[attrId] = fn }
    fun putOnChange(attrId: String, fn: ICallable) { onChange[attrId] = fn }
    fun putFormula(attrId: String, fn: ICallable) { formula[attrId] = fn }
    fun putDefaultExpr(attrId: String, fn: ICallable) { defaultExpr[attrId] = fn }
    fun putMinExpr(attrId: String, fn: ICallable) { minExpr[attrId] = fn }
    fun putMaxExpr(attrId: String, fn: ICallable) { maxExpr[attrId] = fn }

    fun getDerive(attrId: String): ICallable? = derive[attrId]
    fun getOnChange(attrId: String): ICallable? = onChange[attrId]
    fun getFormula(attrId: String): ICallable? = formula[attrId]
    fun getDefaultExpr(attrId: String): ICallable? = defaultExpr[attrId]
    fun getMinExpr(attrId: String): ICallable? = minExpr[attrId]
    fun getMaxExpr(attrId: String): ICallable? = maxExpr[attrId]

    fun clear() {
        derive.clear()
        onChange.clear()
        formula.clear()
        defaultExpr.clear()
        minExpr.clear()
        maxExpr.clear()
    }
}
