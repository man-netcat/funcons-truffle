package main

import main.objects.EntityObject
import main.objects.Object

/**
 * The Kotlin source for one corpus file, or null if it declares nothing to write.
 *
 * @param objects what the file declared, in declaration order.
 * @param included the objects to write, or null to write every one of them. An
 * empty set is therefore not the same as null: it is a selection that happens to
 * select nothing, and writes no code. The two used to be the same because the
 * caller passed an empty set to mean "no restriction", which made it impossible
 * for a dependency pass that found nothing to actually emit nothing.
 */
fun renderFile(fileName: String, objects: Collection<Object>, included: Set<Object>?): String? {
    val toProcess = if (included == null) objects else objects.filter { it in included }

    if (toProcess.isEmpty()) return null

    val stringBuilder = StringBuilder()

    stringBuilder.appendLine("package generated")
    stringBuilder.appendLine()

    val imports = listOf(
        "language.*",
        "builtin.*",
        "com.oracle.truffle.api.frame.VirtualFrame",
    )
    imports.forEach { stringBuilder.appendLine("import $it") }
    stringBuilder.appendLine()

    for (obj in toProcess) {
        if (obj is EntityObject) continue
        println("\nGenerating code for ${obj::class.simpleName} ${obj.name} (File $fileName)")
        val code = obj.makeCode()
        stringBuilder.appendLine()
        stringBuilder.appendLine(code)
        if (obj.elementInBody.isNotEmpty()) {
            stringBuilder.appendLine()
            stringBuilder.appendLine(obj.elementInBody)
        }
    }

    return stringBuilder.toString().trim()
}
