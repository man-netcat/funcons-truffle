package trufflegen.main

import java.io.File

abstract class DefinitionDataContainer() {
    lateinit var file: File
    abstract val name: String
    abstract fun generateCode(): String

    internal val nodeName: String
        get() {
            return toClassName(name)
        }
}
