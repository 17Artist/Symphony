/*
 * Copyright 2026 17Artist
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package priv.seventeen.artist.symphony.integrations.papi

import java.lang.reflect.InvocationTargetException

/** 避免可选依赖仅因方法返回类型变化而在 JVM 链接阶段失败。 */
internal object OptionalIntegrationMethodInvoker {
    data class Invocation(val returnValue: Any?)

    fun invokeNoArg(target: Any, methodName: String): Invocation? {
        val method = target.javaClass.methods.firstOrNull {
            it.name == methodName && it.parameterCount == 0
        } ?: return null
        return try {
            Invocation(method.invoke(target))
        } catch (error: InvocationTargetException) {
            throw error.targetException
        }
    }
}
