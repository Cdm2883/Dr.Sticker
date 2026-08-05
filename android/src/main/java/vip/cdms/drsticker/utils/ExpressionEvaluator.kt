package vip.cdms.drsticker.utils

class ExpressionEvaluator(
    private val expression: String,
    private val variables: Map<String, Double>
) {
    private var position = -1
    private var char = 0

    private fun next() {
        char = if (++position < expression.length) expression[position].code else -1
    }

    private fun eat(charToEat: Int): Boolean {
        while (char == ' '.code) next()
        if (char == charToEat) return true.also { next() }
        return false
    }

    fun parse(): Double {
        next()
        val x = parseExpression()
        if (position < expression.length)
            error("Unexpected character: ${char.toChar()}")
        return x
    }

    private fun parseExpression(): Double {
        var x = parseTerm()
        while (true) {
            when {
                eat('+'.code) -> x += parseTerm()
                eat('-'.code) -> x -= parseTerm()
                else -> return x
            }
        }
    }

    private fun parseTerm(): Double {
        var x = parseFactor()
        while (true) {
            when {
                eat('*'.code) -> x *= parseFactor()
                eat('/'.code) -> x /= parseFactor()
                else -> return x
            }
        }
    }

    private fun parseFactor(): Double {
        if (eat('+'.code)) return parseFactor()
        if (eat('-'.code)) return -parseFactor()

        var x: Double
        val startPos = this.position
        if (eat('('.code)) {
            x = parseExpression()
            eat(')'.code)

        } else if (char in '0'.code..'9'.code || char == '.'.code) {
            while (char in '0'.code..'9'.code || char == '.'.code) next()
            x = expression.substring(startPos, this.position).toDouble()

        } else if (eat('$'.code)) {
            val varStart = this.position
            while (char in 'a'.code..'z'.code || char in 'A'.code..'Z'.code || char == '_'.code || char in '0'.code..'9'.code) next()
            val varName = expression.substring(varStart, this.position)
            x = variables[varName] ?: error("Unknown variable: $$varName")

        } else {
            error("Unexpected character: ${char.toChar()}")
        }

        return x
    }
}

fun String.evalExpr(variables: Map<String, Double> = emptyMap()) =
    ExpressionEvaluator(this, variables).parse()
