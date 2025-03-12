package language

import generated.aliasMap
import kotlin.reflect.KClass
import kotlin.reflect.KFunction
import kotlin.reflect.KParameter

object FCTNodeFactory {
    private const val GENERATED = "generated"
    private const val INTERPRETER = "language"

    fun createNode(funconName: String, children: List<Any>): TermNode {
        val resolvedName = aliasMap[funconName] ?: funconName

        val classNames = sequenceOf(GENERATED, INTERPRETER)
            .map { packageName -> toClassName(resolvedName, packageName) }

        val clazz = classNames
            .mapNotNull { className ->
                runCatching { Class.forName(className).kotlin }.getOrNull()
            }
            .firstOrNull() ?: throw ClassNotFoundException("No class found for $funconName")

        return instantiate(clazz, children) as TermNode
    }


    private fun instantiate(clazz: KClass<out Any>, children: List<Any>): Any {
        val constructor = findMatchingConstructor(clazz, children)
            ?: throw IllegalArgumentException("No suitable constructor found for class: ${clazz.qualifiedName}")

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
        val varargIndex = parameters.indexOfFirst { it.isVararg }

        return if (varargIndex >= 0) {
            val beforeVararg = parameters.take(varargIndex)
            val varargParam = parameters[varargIndex]
            val afterVararg = parameters.drop(varargIndex + 1)

            val beforeArgs = children.take(beforeVararg.size)
            val varargArgs = children.drop(beforeVararg.size).dropLast(afterVararg.size)
                .toTypedArray()
            val afterArgs = children.takeLast(afterVararg.size)


            val componentType = (varargParam.type.arguments.first().type!!.classifier as KClass<*>).java
            val varargArray = if (varargArgs.isNotEmpty()) {
                java.lang.reflect.Array.newInstance(componentType, varargArgs.size).also { array ->
                    varargArgs.withIndex().forEach { arg ->
                        java.lang.reflect.Array.set(array, arg.index, arg.value as TermNode)
                    }
                }
            } else {
                java.lang.reflect.Array.newInstance(componentType, 0)
            }

            beforeVararg.zip(beforeArgs).toMap() +
                    mapOf(varargParam to varargArray) +
                    afterVararg.zip(afterArgs).toMap()
        } else {
            parameters.zip(children).toMap()
        }
    }

    private fun toClassName(funconName: String, packageName: String): String {
        return "$packageName.${funconName.split('-').joinToString("") { it.replaceFirstChar(Char::titlecase) }}Node"
    }
}