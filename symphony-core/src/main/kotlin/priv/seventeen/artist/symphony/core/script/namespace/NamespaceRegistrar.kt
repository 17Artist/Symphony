package priv.seventeen.artist.symphony.core.script.namespace

import priv.seventeen.artist.blink.BlinkLog
import priv.seventeen.artist.blink.script.AriaScriptManager

/**
 * 将 symphony.* API 注入 Aria 全局作用域（GlobalStorage 跨脚本共享）。
 *
 * 通过一段 bootstrap 脚本把 SymphonyBridge 实例的方法包装成嵌套对象
 * `global.symphony.attribute.* / entity.* / effect.*`，
 * 让所有 .aria 文件都能直接调用。
 *
 * 注意：属性的「定义」改用 @attribute 注解式脚本，
 * 这里只暴露查询/运行时操作，不再有 register / registerAll。
 */
object NamespaceRegistrar {

    fun registerAll() {
        if (!AriaScriptManager.isAvailable) return

        // 将 Kotlin 单例注册为 Aria 全局变量，脚本通过 Java 互操作调用
        val bootstrapCode = """
            val.SymphonyBridge = use('priv.seventeen.artist.symphony.core.script.namespace.SymphonyBridge')
            val.bridge = SymphonyBridge()

            global.symphony = {
                'attribute': {
                    'unregister': -> { bridge.attributeUnregister(args[0]) },
                    'list': -> { return bridge.attributeList() },
                    'exists': -> { return bridge.attributeExists(args[0]) },
                    'getInfo': -> { return bridge.attributeGetInfo(args[0]) },
                    'listByCategory': -> { return bridge.attributeListByCategory(args[0]) },
                    'listByTag': -> { return bridge.attributeListByTag(args[0]) },
                    'get': -> { return bridge.attributeGet(args[0], args[1]) },
                    'getRaw': -> { return bridge.attributeGetRaw(args[0], args[1]) }
                },
                'entity': {
                    'damage': -> { bridge.entityDamage(args[0], args[1], args[2]) },
                    'heal': -> { bridge.entityHeal(args[0], args[1]) },
                    'getHealth': -> { return bridge.entityGetHealth(args[0]) },
                    'getMaxHealth': -> { return bridge.entityGetMaxHealth(args[0]) }
                },
                'effect': {
                    'particle': -> { bridge.effectParticle(args[0], args[1], args[2]) },
                    'sound': -> { bridge.effectSound(args[0], args[1], args[2], args[3]) }
                }
            }
        """.trimIndent()

        try {
            AriaScriptManager.eval(bootstrapCode)
            BlinkLog.detail("  §7已注册 §fsymphony.* §7命名空间")
        } catch (e: Exception) {
            BlinkLog.error("命名空间注册失败: ${e.message}")
            e.printStackTrace()
        }
    }
}
