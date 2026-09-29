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

/**
 * The alias map and the type aliases for the whole corpus, as one Kotlin file.
 *
 * Entities are skipped: an alias names a node to construct, and an entity has no
 * node, it is read and written by name.
 *
 * @param included the objects to write, or null for all of them, as in [renderFile].
 */
/**
 * The node metadata for every generated class, as one Kotlin file.
 *
 * `builtin.NodeMeta` used to read a node's constructor shape reflectively, once per class and then
 * on every call that creates or reads a node. The generator already knows each constructor when it
 * writes the class, so it writes the description here instead: keyed by binary class name, built on
 * demand, so a node class that is never used costs nothing. The node classes Truffle implements by
 * hand are absent and are still read reflectively.
 *
 * @param included the objects to write, or null for all of them, as in [renderFile].
 */
fun renderNodeMetas(registry: Registry, included: Set<Object>?): String {
    val stringBuilder = StringBuilder()

    stringBuilder.appendLine("package generated")
    stringBuilder.appendLine("import builtin.*")
    stringBuilder.appendLine()

    stringBuilder.appendLine("/** Written by trufflegen: a node's constructor shape, decided here rather than by reflection. */")
    stringBuilder.appendLine("internal val nodeMetas: Map<String, () -> NodeMeta> = mapOf(")
    registry.objects
        .asSequence()
        .filter { included == null || it in included }
        .mapNotNull { it.nodeMetaEntry }
        .joinToString(",\n    ", prefix = "    ", postfix = "\n)")
        .let(stringBuilder::append)

    return stringBuilder.toString()
}

fun renderAliases(registry: Registry, included: Set<Object>?): String {
    val stringBuilder = StringBuilder()

    stringBuilder.appendLine("package generated")
    stringBuilder.appendLine("import language.*")
    stringBuilder.appendLine("import builtin.*")
    stringBuilder.appendLine()

    stringBuilder.appendLine("val aliasMap: Map<String, String> = mapOf(")
    val objectsToProcess = if (included != null) {
        registry.objects.filter { obj -> obj in included && obj !is EntityObject }
    } else registry.objects.filter { obj -> obj !is EntityObject }

    objectsToProcess.flatMap { obj ->
        obj.aliases
            .asSequence()
            .filterNot { alias -> alias == obj.name }
            .map { alias -> "\"$alias\" to \"${obj.name}\"" }
    }
        .joinToString(",\n    ", prefix = "    ", postfix = "\n)")
        .let(stringBuilder::append)

    stringBuilder.appendLine()
    objectsToProcess.flatMap { obj ->
        obj.aliases
            .asSequence()
            .filterNot { alias -> alias == obj.name }
            .map { alias ->
                makeTypeAlias(toNodeName(alias), obj.nodeName)
            }
    }
        .joinToString("\n")
        .let(stringBuilder::appendLine)

    return stringBuilder.toString()
}
