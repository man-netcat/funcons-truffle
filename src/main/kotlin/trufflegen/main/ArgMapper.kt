package trufflegen.main

import kotlin.reflect.KCallable
import kotlin.reflect.KParameter

fun mapArgumentsToParameters(
    function: KCallable<*>, vararg args: Any?
): Map<KParameter, Any?> {
    val result = mutableMapOf<KParameter, Any?>()

    val parameters = function.parameters
    val varargIndex = parameters.indexOfFirst { it.isVararg }

    if (varargIndex == -1) {
        // No vararg parameter
        if (parameters.size != args.size) {
            throw IllegalArgumentException("Number of arguments does not match the number of parameters.")
        }
        parameters.forEachIndexed { index, parameter ->
            result[parameter] = args[index]
        }
    } else {
        // Vararg parameter is present
        // Handle parameters before vararg
        for (i in 0 until varargIndex) {
            result[parameters[i]] = args.getOrNull(i)
        }

        // Handle vararg parameter
        val varargParameter = parameters[varargIndex]
        val varargArgs = args.slice(varargIndex until args.size - (parameters.size - varargIndex - 1)).toList()
        result[varargParameter] = varargArgs

        // Handle parameters after vararg
        for (i in (varargIndex + 1) until parameters.size) {
            result[parameters[i]] = args.getOrNull(args.size - (parameters.size - i))
        }
    }

    return result
}

// Example usage
fun exampleFunction(a: Int, f: Double, b: String, vararg c: Double, d: Boolean, e: Boolean) {}

fun main() {
    val kFunction = ::exampleFunction
    val arguments = arrayOf(1, 3.5, "Hello", 2.5, 5.6, 4.2, 3.6, true, false)
    val mapping = mapArgumentsToParameters(kFunction, *arguments)

    mapping.forEach { (parameter, argument) ->
        println("Parameter: ${parameter.name}, Argument: $argument")
    }
}
