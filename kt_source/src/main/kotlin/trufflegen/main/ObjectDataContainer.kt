package trufflegen.main

abstract class ObjectDataContainer() {
    abstract val name: String
    abstract fun generateCode(): String

    internal val nodeName: String
        get() {
            return toClassName(name)
        }
}
