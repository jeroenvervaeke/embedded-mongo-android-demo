package io.github.jeroenvervaeke.coffeefinder.data.query

import kotlin.math.asin
import kotlin.math.cos
import kotlin.math.pow
import kotlin.math.sin
import org.bson.Document

/**
 * Evaluates the handful of aggregation operators [distanceFromExpression] builds, against one
 * document.
 *
 * Asserting the nested BSON literally would pin the shape of the expression without saying
 * anything about what it computes, and the thing worth knowing about a haversine written in
 * `$sin` and `$asin` is whether the number that comes out is the distance. So the test runs it,
 * the way the engine would, and checks the answer against distances that are a matter of record.
 */
fun evaluate(expression: Any?, document: Document): Any? = when (expression) {
    is Document -> evaluateOperator(expression, document)
    is String -> if (expression.startsWith("$")) document.path(expression.removePrefix("$")) else expression
    else -> expression
}

private fun evaluateOperator(expression: Document, document: Document): Any? {
    val operator = expression.keys.singleOrNull()
        ?: error("an expression carries one operator, not ${expression.keys}")
    val argument = expression.getValue(operator)
    val operands = (argument as? List<*>)?.map { evaluate(it, document) }

    fun operand(index: Int) = (operands?.get(index) as Number).toDouble()
    fun single() = (evaluate(argument, document) as Number).toDouble()

    return when (operator) {
        "\$add" -> operands!!.sumOf { (it as Number).toDouble() }
        "\$subtract" -> operand(0) - operand(1)
        "\$multiply" -> operands!!.fold(1.0) { total, value -> total * (value as Number).toDouble() }
        "\$divide" -> operand(0) / operand(1)
        "\$pow" -> operand(0).pow(operand(1))
        "\$sin" -> sin(single())
        "\$cos" -> cos(single())
        "\$asin" -> asin(single())
        "\$sqrt" -> kotlin.math.sqrt(single())
        "\$min" -> operands!!.minOf { (it as Number).toDouble() }
        "\$degreesToRadians" -> Math.toRadians(single())
        "\$arrayElemAt" -> (operands!![0] as List<*>)[operand(1).toInt()]
        else -> error("the evaluator does not know $operator")
    }
}

/** Resolves a field path such as `loc.coordinates` against nested documents. */
private fun Document.path(path: String): Any? =
    path.split('.').fold<String, Any?>(this) { value, field -> (value as? Document)?.get(field) }
