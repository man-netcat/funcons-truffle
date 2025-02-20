package interpreter

import java.lang.reflect.Constructor

object FCTNodeFactory {
    private const val NODE_PACKAGE = "generated"

    fun createNode(funconName: String, children: List<FCTNode>, vararg extraArgs: Any?): FCTNode {
        val className = toClassName(funconName)

        return try {
            val clazz = Class.forName(className).asSubclass(FCTNode::class.java)
            instantiate(clazz, children, extraArgs)
        } catch (e: ClassNotFoundException) {
            throw IllegalArgumentException("No matching Truffle node class found: $className", e)
        }
    }

    private fun instantiate(clazz: Class<out FCTNode>, children: List<FCTNode>, extraArgs: Array<out Any?>): FCTNode {
        // Find a constructor that matches the number of parameters or more specifically matches types
        val constructor: Constructor<*>? = clazz.constructors.find { constructor ->
            val parameterTypes = constructor.parameterTypes
            parameterTypes.size == children.size + extraArgs.size
        }

        if (constructor == null)
            throw IllegalArgumentException("No suitable constructor found for class: ${clazz.name}")

        val args = mutableListOf<Any?>()

        constructor.parameters.forEachIndexed { index, _ ->
            when {
                index < children.size -> args.add(children[index])
                index - children.size < extraArgs.size -> args.add(extraArgs[index - children.size])
                else -> args.add(null) // Add null if there are more parameters than arguments
            }
        }

        return constructor.newInstance(*args.toTypedArray()) as FCTNode
    }


    private fun toClassName(funconName: String): String {
        return "$NODE_PACKAGE." + funconName.split('-')
            .joinToString("") { it.replaceFirstChar(Char::titlecase) } + "Node"
    }
}
