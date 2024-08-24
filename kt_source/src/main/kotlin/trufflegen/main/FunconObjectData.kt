package trufflegen.main

class FunconObjectData(
    override val name: String, private val params: List<Param>?, val returns: ReturnType,
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
        val cls = makeClass(nodeName, listOf(), listOf(), listOf())
        return cls
    }
}

