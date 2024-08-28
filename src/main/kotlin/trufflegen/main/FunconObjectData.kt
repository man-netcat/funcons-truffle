package trufflegen.main

class FunconObjectData(
    override val name: String,
    private val params: List<Param>?,
    private val content: String,
    private val returns: ReturnType,
    private val aliases: List<String>,
) : ObjectDataContainer() {

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

        val cls = makeClass(nodeName, emptyList(), paramsStr, emptyList(), content)

        val aliasStrs = aliases.joinToString("\n") { alias -> makeTypeAlias(toClassName(alias), nodeName) }

        val file = makeFile("fctruffle.generated", imports, cls, aliasStrs)
        return file
    }
}

