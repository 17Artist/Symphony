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

package priv.seventeen.artist.symphony.engine.power

import kotlin.math.abs
import kotlin.math.ceil
import kotlin.math.exp
import kotlin.math.floor
import kotlin.math.ln
import kotlin.math.log10
import kotlin.math.pow
import kotlin.math.round
import kotlin.math.sqrt

class CompiledPowerExpression internal constructor(
    val source: String,
    variables: Set<String>,
    private val root: PowerNode
) {
    val variables: Set<String> = variables.toSet()

    fun evaluate(values: Map<String, Double>): Double {
        require(values.values.all(Double::isFinite)) { "战力变量必须全部是有限数" }
        val result = root.evaluate { name -> values[name] ?: 0.0 }
        require(result.isFinite()) { "战力公式产生了非有限数结果" }
        return result
    }
}

object PowerExpressionCompiler {
    @JvmStatic
    fun compile(source: String): CompiledPowerExpression = Parser(source).compile()
}

internal fun interface PowerNode {
    fun evaluate(variable: (String) -> Double): Double
}

private data class ConstantNode(val value: Double) : PowerNode {
    override fun evaluate(variable: (String) -> Double): Double = value
}

private data class VariableNode(val name: String) : PowerNode {
    override fun evaluate(variable: (String) -> Double): Double = variable(name)
}

private data class UnaryNode(val operator: String, val operand: PowerNode) : PowerNode {
    override fun evaluate(variable: (String) -> Double): Double = when (operator) {
        "+" -> operand.evaluate(variable)
        "-" -> -operand.evaluate(variable)
        "!" -> if (truthy(operand.evaluate(variable))) 0.0 else 1.0
        else -> error("不支持的一元运算符 $operator")
    }
}

private data class BinaryNode(val operator: String, val left: PowerNode, val right: PowerNode) : PowerNode {
    override fun evaluate(variable: (String) -> Double): Double {
        if (operator == "&&") {
            if (!truthy(left.evaluate(variable))) return 0.0
            return if (truthy(right.evaluate(variable))) 1.0 else 0.0
        }
        if (operator == "||") {
            if (truthy(left.evaluate(variable))) return 1.0
            return if (truthy(right.evaluate(variable))) 1.0 else 0.0
        }
        val first = left.evaluate(variable)
        val second = right.evaluate(variable)
        return when (operator) {
            "+" -> first + second
            "-" -> first - second
            "*" -> first * second
            "/" -> {
                require(abs(second) > ZERO_EPSILON) { "战力公式不能除以零" }
                first / second
            }
            "%" -> {
                require(abs(second) > ZERO_EPSILON) { "战力公式不能除以零" }
                first % second
            }
            "^" -> first.pow(second)
            ">" -> boolean(first > second)
            ">=" -> boolean(first >= second)
            "<" -> boolean(first < second)
            "<=" -> boolean(first <= second)
            "==" -> boolean(first == second)
            "!=" -> boolean(first != second)
            else -> error("不支持的二元运算符 $operator")
        }
    }
}

private data class FunctionNode(val name: String, val arguments: List<PowerNode>) : PowerNode {
    override fun evaluate(variable: (String) -> Double): Double {
        if (name == "if") {
            return if (truthy(arguments[0].evaluate(variable))) {
                arguments[1].evaluate(variable)
            } else arguments[2].evaluate(variable)
        }
        val values = arguments.map { it.evaluate(variable) }
        return when (name) {
            "min" -> values.minOrNull()!!
            "max" -> values.maxOrNull()!!
            "clamp" -> {
                require(values[1] <= values[2]) { "clamp 的最小值不能大于最大值" }
                values[0].coerceIn(values[1], values[2])
            }
            "abs" -> abs(values[0])
            "sqrt" -> {
                require(values[0] >= 0.0) { "sqrt 的输入值不能为负数" }
                sqrt(values[0])
            }
            "pow" -> values[0].pow(values[1])
            "floor" -> floor(values[0])
            "ceil" -> ceil(values[0])
            "round" -> round(values[0])
            "ln" -> {
                require(values[0] > 0.0) { "ln 的输入值必须大于零" }
                ln(values[0])
            }
            "log10" -> {
                require(values[0] > 0.0) { "log10 的输入值必须大于零" }
                log10(values[0])
            }
            "exp" -> exp(values[0])
            else -> error("不支持的战力函数 $name")
        }
    }
}

private class Parser(private val source: String) {
    private var cursor = 0
    private var nodeCount = 0
    private val variables = linkedSetOf<String>()

    fun compile(): CompiledPowerExpression {
        require(source.isNotBlank()) { "战力公式不能为空" }
        require(source.length <= MAX_EXPRESSION_LENGTH) {
            "战力公式不能超过 $MAX_EXPRESSION_LENGTH 个字符"
        }
        val root = parseOr(0)
        skipWhitespace()
        require(cursor == source.length) { errorAt("遇到意外内容") }
        return CompiledPowerExpression(source, variables, root)
    }

    private fun parseOr(depth: Int): PowerNode {
        var node = parseAnd(depth)
        while (match("||")) node = binary("||", node, parseAnd(depth))
        return node
    }

    private fun parseAnd(depth: Int): PowerNode {
        var node = parseComparison(depth)
        while (match("&&")) node = binary("&&", node, parseComparison(depth))
        return node
    }

    private fun parseComparison(depth: Int): PowerNode {
        var node = parseAdditive(depth)
        while (true) {
            val operator = listOf(">=", "<=", "==", "!=", ">", "<").firstOrNull(::match) ?: break
            node = binary(operator, node, parseAdditive(depth))
        }
        return node
    }

    private fun parseAdditive(depth: Int): PowerNode {
        var node = parseMultiplicative(depth)
        while (true) {
            node = when {
                match("+") -> binary("+", node, parseMultiplicative(depth))
                match("-") -> binary("-", node, parseMultiplicative(depth))
                else -> return node
            }
        }
    }

    private fun parseMultiplicative(depth: Int): PowerNode {
        var node = parsePower(depth)
        while (true) {
            node = when {
                match("*") -> binary("*", node, parsePower(depth))
                match("/") -> binary("/", node, parsePower(depth))
                match("%") -> binary("%", node, parsePower(depth))
                else -> return node
            }
        }
    }

    private fun parsePower(depth: Int): PowerNode {
        val left = parseUnary(depth)
        return if (match("^")) binary("^", left, parsePower(depth)) else left
    }

    private fun parseUnary(depth: Int): PowerNode = when {
        match("+") -> node(UnaryNode("+", parseUnary(depth)))
        match("-") -> node(UnaryNode("-", parseUnary(depth)))
        match("!") -> node(UnaryNode("!", parseUnary(depth)))
        else -> parsePrimary(depth)
    }

    private fun parsePrimary(depth: Int): PowerNode {
        require(depth <= MAX_NESTING_DEPTH) { "战力公式嵌套深度超过 $MAX_NESTING_DEPTH" }
        skipWhitespace()
        if (match("(")) {
            val nested = parseOr(depth + 1)
            expect(")")
            return nested
        }
        if (peekNumber()) return constant(parseNumber())
        val identifier = parseIdentifier()
        skipWhitespace()
        if (!match("(")) {
            require(identifier in BUILT_IN_VARIABLES) { errorAt("未知的战力变量 '$identifier'") }
            variables += identifier
            return node(VariableNode(identifier))
        }
        if (identifier in LOOKUP_FUNCTIONS) {
            val key = parseStringLiteral()
            expect(")")
            require(key.isNotBlank() && key.length <= MAX_LOOKUP_LENGTH) {
                errorAt("$identifier 查询键长度必须为 1 到 $MAX_LOOKUP_LENGTH 个字符")
            }
            val variable = "$identifier:$key"
            variables += variable
            return node(VariableNode(variable))
        }
        require(identifier in FUNCTIONS) { errorAt("未知的战力函数 '$identifier'") }
        val arguments = mutableListOf<PowerNode>()
        skipWhitespace()
        if (!match(")")) {
            do {
                require(arguments.size < MAX_FUNCTION_ARGUMENTS) {
                    "$identifier 的参数数量超过上限 $MAX_FUNCTION_ARGUMENTS"
                }
                arguments += parseOr(depth + 1)
            } while (match(","))
            expect(")")
        }
        validateArity(identifier, arguments.size)
        return node(FunctionNode(identifier, arguments))
    }

    private fun parseIdentifier(): String {
        skipWhitespace()
        val start = cursor
        require(cursor < source.length && (source[cursor].isLetter() || source[cursor] == '_')) {
            errorAt("此处应为数字、变量、函数或 '('")
        }
        cursor++
        while (cursor < source.length && (source[cursor].isLetterOrDigit() || source[cursor] == '_')) cursor++
        return source.substring(start, cursor).lowercase()
    }

    private fun parseStringLiteral(): String {
        skipWhitespace()
        require(cursor < source.length && (source[cursor] == '\'' || source[cursor] == '"')) {
            errorAt("查询函数的 ID 必须使用引号包裹")
        }
        val quote = source[cursor++]
        val result = StringBuilder()
        while (cursor < source.length) {
            val character = source[cursor++]
            if (character == quote) return result.toString()
            if (character == '\\') {
                require(cursor < source.length) { errorAt("转义序列未结束") }
                val escaped = source[cursor++]
                require(escaped == quote || escaped == '\\') { errorAt("仅支持转义引号和反斜杠") }
                result.append(escaped)
            } else result.append(character)
        }
        throw IllegalArgumentException(errorAt("字符串未结束"))
    }

    private fun parseNumber(): Double {
        skipWhitespace()
        val start = cursor
        var digits = false
        while (cursor < source.length && source[cursor].isDigit()) {
            cursor++
            digits = true
        }
        if (cursor < source.length && source[cursor] == '.') {
            cursor++
            while (cursor < source.length && source[cursor].isDigit()) {
                cursor++
                digits = true
            }
        }
        require(digits) { errorAt("数字格式无效") }
        if (cursor < source.length && source[cursor].lowercaseChar() == 'e') {
            cursor++
            if (cursor < source.length && source[cursor] in setOf('+', '-')) cursor++
            val exponentStart = cursor
            while (cursor < source.length && source[cursor].isDigit()) cursor++
            require(cursor > exponentStart) { errorAt("数字指数格式无效") }
        }
        return source.substring(start, cursor).toDouble().also {
            require(it.isFinite()) { errorAt("数字必须是有限数") }
        }
    }

    private fun peekNumber(): Boolean {
        skipWhitespace()
        return cursor < source.length && (source[cursor].isDigit() ||
            source[cursor] == '.' && cursor + 1 < source.length && source[cursor + 1].isDigit())
    }

    private fun binary(operator: String, left: PowerNode, right: PowerNode): PowerNode =
        node(BinaryNode(operator, left, right))

    private fun constant(value: Double): PowerNode = node(ConstantNode(value))

    private fun <T : PowerNode> node(value: T): T {
        nodeCount++
        require(nodeCount <= MAX_NODE_COUNT) { "战力公式的运算次数超过上限 $MAX_NODE_COUNT" }
        return value
    }

    private fun match(token: String): Boolean {
        skipWhitespace()
        if (!source.startsWith(token, cursor)) return false
        cursor += token.length
        return true
    }

    private fun expect(token: String) {
        require(match(token)) { errorAt("此处应为 '$token'") }
    }

    private fun skipWhitespace() {
        while (cursor < source.length && source[cursor].isWhitespace()) cursor++
    }

    private fun errorAt(message: String): String = "$message，位置：第 ${cursor + 1} 个字符"

    private fun validateArity(name: String, size: Int) {
        when (name) {
            "min", "max" -> require(size >= 1) { "$name 至少需要一个参数" }
            "clamp", "if" -> require(size == 3) { "$name 必须使用三个参数" }
            "pow" -> require(size == 2) { "pow 必须使用两个参数" }
            else -> require(size == 1) { "$name 必须使用一个参数" }
        }
    }

    companion object {
        private val BUILT_IN_VARIABLES = setOf(
            "level", "experience", "source_count", "item_source_count",
            "set_count", "set_piece_count", "set_tier_count",
            "skill_count", "affix_count", "gem_count",
            "enhancement_total", "enhancement_max"
        )
        private val LOOKUP_FUNCTIONS = setOf("attribute", "set_pieces", "set_tiers")
        private val FUNCTIONS = setOf(
            "min", "max", "clamp", "abs", "sqrt", "pow", "floor", "ceil", "round",
            "ln", "log10", "exp", "if"
        )
        private const val MAX_EXPRESSION_LENGTH = 16_384
        private const val MAX_NESTING_DEPTH = 64
        private const val MAX_NODE_COUNT = 4096
        private const val MAX_FUNCTION_ARGUMENTS = 64
        private const val MAX_LOOKUP_LENGTH = 128
    }
}

private fun truthy(value: Double): Boolean = abs(value) > ZERO_EPSILON
private fun boolean(value: Boolean): Double = if (value) 1.0 else 0.0
private const val ZERO_EPSILON = 1.0e-12
