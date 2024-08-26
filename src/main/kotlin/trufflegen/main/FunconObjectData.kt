package trufflegen.main

class FunconObjectData(
    override val name: String,
    private val params: List<Param>?,
    val returns: ReturnType,
    private val aliases: List<String>,
) : ObjectDataContainer() {

    fun argToParam(preVararg: Int, postVararg: Int, argIndex: Int): Int {
        val paramList = params ?: return -1
        val totalParams = paramList.size

        return when {
            // Argument is in the pre-vararg section
            argIndex < preVararg -> argIndex

            // Argument is in the vararg section
            argIndex in preVararg until (preVararg + (totalParams - preVararg - postVararg)) -> preVararg

            // Argument is in the post-vararg section
            argIndex >= (preVararg + (totalParams - preVararg - postVararg)) -> {
                val postVarargIndex = argIndex - (totalParams - postVararg)
                preVararg + 1 + postVarargIndex
            }

            else -> -1 // Invalid index
        }
    }

    override fun generateCode(): String {
        val imports = makeImports(
            listOf(
                "fctruffle.main.*",
                "com.oracle.truffle.api.frame.VirtualFrame",
                "com.oracle.truffle.api.nodes.NodeInfo",
                "com.oracle.truffle.api.nodes.Node.Child"
            )
        )

        val paramsStr = params.orEmpty().map { param ->
            val annotation = if (param.type.isVararg) "@Children private vararg val" else "@Child private val"
            Triple(annotation, param.string, "FCTNode")
        }

        val cls = makeClass(nodeName, constructorArgs = paramsStr)

        val aliasStrs = aliases.joinToString("\n") { alias -> makeTypeAlias(toClassName(alias), nodeName) }

        val file = makeFile("fctruffle.generated", imports, cls, aliasStrs)
        return file
    }
}

