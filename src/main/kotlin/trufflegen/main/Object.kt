package trufflegen.main

import java.io.File

abstract class Object() {
    lateinit var file: File
    abstract val name: String
    abstract fun generateCode(objects: Map<String, Object>): String

    internal val nodeName: String
        get() {
            return toClassName(name)
        }
}
