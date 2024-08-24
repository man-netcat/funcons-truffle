package trufflegen.main

class DatatypeObjectData(override val name: String, val definition: String) : ObjectDataContainer() {
    override fun generateCode(): String {
        val cls = makeClass(nodeName, listOf(), listOf(), listOf())
        return cls
    }
}
