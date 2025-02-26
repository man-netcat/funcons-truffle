package language

import kotlin.reflect.KClass
import kotlin.reflect.KFunction

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

        println("Found constructor: $constructor for class: ${clazz.qualifiedName}")

        val args = prepareArguments(constructor, children)

        return constructor.call(*args)
    }

    private fun findMatchingConstructor(
        clazz: KClass<out Any>,
        children: List<Any>,
    ): KFunction<Any>? {
        val totalArgs = children.size

        return clazz.constructors.find { constructor ->
            val parameterTypes = constructor.parameters
            val isVararg = constructor.parameters.any { it.type.toString().endsWith("Array<*>") }
            val fixedParamCount = if (isVararg) parameterTypes.size - 1 else parameterTypes.size

            if (isVararg) {
                totalArgs >= fixedParamCount
            } else {
                totalArgs == parameterTypes.size
            }
        }
    }

    private fun prepareArguments(
        constructor: KFunction<Any>,
        children: List<Any>,
    ): Array<Any?> {
        val parameterTypes = constructor.parameters
        val isVararg = parameterTypes.any { it.type.toString().endsWith("Array<*>") }
        val args = mutableListOf<Any?>()

        args.addAll(children)

        if (isVararg) {
            val varargPosition = parameterTypes.indexOfFirst { it.type.toString().endsWith("Array<*>") }

            if (varargPosition == -1) {
                throw IllegalArgumentException("Constructor has vararg parameter but no array parameter found.")
            }

            val fixedArgsBeforeVararg = args.take(varargPosition)
            val remainingArgs = args.drop(varargPosition)

            val fixedArgsAfterVarargCount = parameterTypes.size - varargPosition - 1
            val varargArgs = remainingArgs.take(remainingArgs.size - fixedArgsAfterVarargCount)
            val fixedArgsAfterVararg = remainingArgs.takeLast(fixedArgsAfterVarargCount)

            val varargType = parameterTypes[varargPosition].type
            val varargArray = java.lang.reflect.Array.newInstance(varargType.javaClass.componentType, varargArgs.size)
            for (i in varargArgs.indices) {
                java.lang.reflect.Array.set(varargArray, i, varargArgs[i])
            }

            return (fixedArgsBeforeVararg + varargArray + fixedArgsAfterVararg).toTypedArray()
        }

        return args.toTypedArray()
    }

    private fun toClassName(funconName: String, packageName: String): String {
        return "$packageName.${funconName.split('-').joinToString("") { it.replaceFirstChar(Char::titlecase) }}Node"
    }
}