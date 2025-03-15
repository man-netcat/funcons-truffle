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

//        println("creating node: ${toNodeName(funconName)} with children ${children.map { it::class.simpleName }}")
        return instantiate(clazz, args) as TermNode
    }


    private fun instantiate(clazz: KClass<out Any>, args: List<Any>): Any {
        val constructor = findMatchingConstructor(clazz, args)
            ?: throw IllegalArgumentException("No suitable constructor found for class: ${clazz.qualifiedName}")

        val argsMap = prepareArguments(constructor, args)
        return constructor.callBy(argsMap)
    }

    private fun findMatchingConstructor(
        clazz: KClass<out Any>,
        args: List<Any>,
    ): KFunction<Any>? {
        val totalArgs = args.size

//        clazz.constructors.forEach { constructor ->
//            println("Constructor for ${clazz.simpleName}: ${constructor.parameters.joinToString { it.type.toString() }}")
//        }

        return clazz.constructors.find { constructor ->
            val parameters = constructor.parameters
            val isVararg = parameters.any { it.isVararg }
            val isSequence = parameters.any { it.type.classifier == SequenceNode::class }

            if (isVararg) {
                val fixedParamCount = parameters.size - 1
                totalArgs >= fixedParamCount
            } else if (isSequence) {
                val fixedParamCount = parameters.size - 1
                totalArgs >= fixedParamCount
            } else {
                totalArgs == parameters.size
            }
        }
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
            } else {
                Array.newInstance(componentType, 0)
            }

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
