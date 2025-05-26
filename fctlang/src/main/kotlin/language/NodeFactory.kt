package language

import builtin.SequenceNode
import builtin.TermNode
import builtin.TermNode.Companion.DEBUG
import generated.aliasMap
import kotlin.reflect.KClass
import kotlin.reflect.KFunction
import kotlin.reflect.KParameter

object NodeFactory {
    private const val GENERATED = "generated"
    private const val INTERPRETER = "language"
    private const val BUILTIN = "builtin"

    internal fun createNode(funconName: String, args: List<Any>): TermNode {
        val resolvedName = aliasMap[funconName] ?: funconName

        val classNames = sequenceOf(GENERATED, INTERPRETER, BUILTIN)
            .map { packageName -> toClassName(resolvedName, packageName) }

        val clazz = classNames
            .mapNotNull { className ->
                runCatching { Class.forName(className).kotlin }.getOrNull()
            }
            .firstOrNull() ?: throw ClassNotFoundException("No class found for $funconName")

        val constructor = clazz.constructors.first()

        if (DEBUG) {
            println("creating node: ${toNodeName(funconName)} with children ${args.map { it::class.simpleName }}")
            println("Constructor for ${clazz.simpleName}: ${constructor.parameters.map { it.type.toString() }}")
        }

        val argsMap = prepareArguments(constructor, args)
        return constructor.callBy(argsMap) as TermNode
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
                java.lang.reflect.Array.newInstance(componentType, varargArgs.size).also { array ->
                    varargArgs.withIndex().forEach { arg ->
                        val value = if (componentType == SequenceNode::class.java) {
                            createNode("sequence", listOf(arg.value))
                        } else arg.value
                        java.lang.reflect.Array.set(array, arg.index, value)
                    }
                }
            } else java.lang.reflect.Array.newInstance(componentType, 0)

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
