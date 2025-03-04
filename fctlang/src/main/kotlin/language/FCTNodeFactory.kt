package language

import kotlin.reflect.KClass
import kotlin.reflect.KFunction
import kotlin.reflect.KParameter

object FCTNodeFactory {
    private const val GENERATED = "generated"
    private const val INTERPRETER = "language"

    fun createNode(funconName: String, children: List<Any>): FCTNode {
        val className = toClassName(funconName, GENERATED)

        return try {
            val clazz = Class.forName(className).kotlin
            instantiate(clazz, children) as FCTNode
        } catch (e: ClassNotFoundException) {
            val extensionsClassName = toClassName(funconName, INTERPRETER)
            val clazz = Class.forName(extensionsClassName).kotlin
            instantiate(clazz, children) as FCTNode
        }
    }

    private fun instantiate(clazz: KClass<out Any>, children: List<Any>): Any {
        val constructor = findMatchingConstructor(clazz, children)
            ?: throw IllegalArgumentException("No suitable constructor found for class: ${clazz.qualifiedName}")

//        println("Found constructor: $constructor for class: ${clazz.qualifiedName}")

        val argsMap = prepareArguments(constructor, children)
        return constructor.callBy(argsMap)
    }

    private fun findMatchingConstructor(
        clazz: KClass<out Any>,
        children: List<Any>,
    ): KFunction<Any>? {
        val totalArgs = children.size

        return clazz.constructors.find { constructor ->
            val parameters = constructor.parameters
            val isVararg = parameters.any { it.isVararg }

            if (isVararg) {
                val fixedParamCount = parameters.size - 1
                totalArgs >= fixedParamCount
            } else {
                totalArgs == parameters.size
            }
        }
    }

    private fun prepareArguments(
        constructor: KFunction<Any>,
        children: List<Any>,
    ): Map<KParameter, Any?> {
        val parameters = constructor.parameters
        val isVararg = parameters.any { it.isVararg }

        return if (isVararg) {
            prepareVarargArguments(parameters, children)
        } else {
            prepareFixedArguments(parameters, children)
        }
    }

    private fun prepareFixedArguments(
        parameters: List<KParameter>,
        children: List<Any>,
    ): Map<KParameter, Any?> {
        return parameters.zip(children).associate { (param, arg) ->
            param to arg
        }
    }

    private fun prepareVarargArguments(
        parameters: List<KParameter>,
        children: List<Any>,
    ): Map<KParameter, Any?> {
        val varargParam = parameters.first { it.isVararg }
        val fixedParamCount = parameters.size - 1  // Total parameters minus vararg

        // Split children into vararg part and fixed parameters
        val varargChildren = children.take(children.size - fixedParamCount)
        val fixedChildren = children.drop(varargChildren.size)

        // Create vararg array (handle empty case)
        val componentType = (varargParam.type.arguments.first().type!!.classifier as KClass<*>).java
        val varargArray = if (varargChildren.isNotEmpty()) {
            java.lang.reflect.Array.newInstance(componentType, varargChildren.size).also { array ->
                for (i in varargChildren.indices) {
                    java.lang.reflect.Array.set(array, i, varargChildren[i] as FCTNode)
                }
            }
        } else {
            // Empty array for vararg
            java.lang.reflect.Array.newInstance(componentType, 0)
        }

        // Combine vararg array and fixed parameters into a map
        val argsMap = mutableMapOf<KParameter, Any?>()
        for (i in parameters.indices) {
            val param = parameters[i]
            argsMap[param] = when {
                param.isVararg -> varargArray
                else -> fixedChildren[i - (parameters.size - fixedParamCount)]
            }
        }

        return argsMap
    }

    private fun toClassName(funconName: String, packageName: String): String {
        return "$packageName.${funconName.split('-').joinToString("") { it.replaceFirstChar(Char::titlecase) }}Node"
    }
}