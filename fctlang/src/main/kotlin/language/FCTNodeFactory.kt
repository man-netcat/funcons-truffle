package language

import generated.aliasMap
import java.lang.reflect.Array
import kotlin.reflect.KClass
import kotlin.reflect.KFunction
import kotlin.reflect.KParameter

object FCTNodeFactory {
    private const val GENERATED = "generated"
    private const val INTERPRETER = "language"

    fun createNode(funconName: String, args: List<Any>): TermNode {
        val resolvedName = aliasMap[funconName] ?: funconName

        val classNames = sequenceOf(GENERATED, INTERPRETER)
            .map { packageName -> toClassName(resolvedName, packageName) }

        val clazz = classNames
            .mapNotNull { className ->
                runCatching { Class.forName(className).kotlin }.getOrNull()
            }
            .firstOrNull() ?: throw ClassNotFoundException("No class found for $funconName")

//        println("creating node: ${toNodeName(funconName)} with children ${args.map { it::class.simpleName }}")
        return instantiate(clazz, args) as TermNode
    }

    private fun instantiate(clazz: KClass<out Any>, args: List<Any>): Any {
        val totalArgs = args.size

//        clazz.constructors.forEach { constructor ->
//            println("Constructor for ${clazz.simpleName}: ${constructor.parameters.joinToString { it.type.toString() }}")
//        }

        val constructor = clazz.constructors.first()
        val argsMap = prepareArguments(constructor, args)
        return constructor.callBy(argsMap)
    }

    private fun prepareArguments(
        constructor: KFunction<Any>,
        args: List<Any>,
    ): Map<KParameter, Any?> {
        val parameters = constructor.parameters
        val varargIndex = parameters.indexOfFirst { it.isVararg }
        val sequenceIndex = parameters.indexOfFirst { it.type.classifier == SequenceNode::class }

        return if (varargIndex >= 0) {
            val beforeVararg = parameters.take(varargIndex)
            val varargParam = parameters[varargIndex]
            val afterVararg = parameters.drop(varargIndex + 1)

            val beforeArgs = args.take(beforeVararg.size)
            val varargArgs = args.drop(beforeVararg.size).dropLast(afterVararg.size)
                .toTypedArray()
            val afterArgs = args.takeLast(afterVararg.size)


            val componentType = (varargParam.type.arguments.first().type!!.classifier as KClass<*>).java
            val varargArray = if (varargArgs.isNotEmpty()) {
                Array.newInstance(componentType, varargArgs.size).also { array ->
                    varargArgs.withIndex().forEach { arg ->
                        val value = if (componentType == SequenceNode::class.java) {
                            createNode("sequence", listOf(arg.value))
                        } else arg.value
                        Array.set(array, arg.index, value)
                    }
                }
            } else Array.newInstance(componentType, 0)

            beforeVararg.zip(beforeArgs).toMap() +
                    mapOf(varargParam to varargArray) +
                    afterVararg.zip(afterArgs).toMap()
        } else if (sequenceIndex >= 0) {
            val beforeSequence = parameters.take(sequenceIndex)
            val sequenceParam = parameters[sequenceIndex]
            val afterSequence = parameters.drop(sequenceIndex + 1)

            val beforeArgs = args.take(beforeSequence.size)
            val sequenceArgs = args.drop(beforeSequence.size).dropLast(afterSequence.size)
                .toTypedArray()
            val afterArgs = args.takeLast(afterSequence.size)
            val sequence = createNode("sequence", sequenceArgs.filterIsInstance<TermNode>())

            beforeSequence.zip(beforeArgs).toMap() +
                    (sequenceParam to sequence) +
                    afterSequence.zip(afterArgs).toMap()
        } else if (args.size != parameters.size) {
            val updatedArgs = mutableListOf<Any?>()

            for (i in parameters.indices) {
                val param = parameters[i]

                // If there's a corresponding argument, use it.
                if (i < args.size) {
                    updatedArgs.add(args[i])
                } else {
                    // If there's no argument for the parameter:
                    if (param.type.isMarkedNullable) {
                        // Insert `null` if the parameter is nullable
                        updatedArgs.add(createNode("null-value", emptyList()))
                    } else {
                        // Otherwise, find a value from the already existing arguments (this part could be customized based on your needs)
                        val fallbackArgument = args.firstOrNull() // You can define the fallback argument as needed.
                        updatedArgs.add(fallbackArgument)
                    }
                }
            }

            parameters.zip(updatedArgs).toMap()
        } else {
            parameters.zip(args).toMap()
        }
    }

    private fun toNodeName(funconName: String): String {
        return "${funconName.split('-').joinToString("") { it.replaceFirstChar(Char::titlecase) }}Node"
    }

    private fun toClassName(funconName: String, packageName: String): String {
        return "$packageName.${toNodeName(funconName)}"
    }
}
