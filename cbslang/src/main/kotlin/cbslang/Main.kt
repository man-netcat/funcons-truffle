package cbslang

import java.nio.file.Paths
import kotlin.system.exitProcess

/**
 * Reads CBS language definitions and reports what was found, including any
 * notation the reader could not place. Used to survey the vendored languages
 * before generating parsers from them.
 */
fun main(args: Array<String>) {
    if (args.isEmpty()) {
        println("usage: cbslang <directory-of-language-definitions>...")
        exitProcess(2)
    }

    var problems = 0
    for (root in args) {
        val defs = Languages.readAll(Paths.get(root))
        println("=== $root : ${defs.size} language(s)")
        for (def in defs) {
            println(
                "  ${def.name}: ${def.files.size} files, " +
                    "${def.declarations.size} declarations, ${def.nonterminals.size} nonterminals, " +
                    "${def.rules.size} rules, ${def.semantics.size} semantics, " +
                    "${def.types.size} types, ${def.index.size} index entries",
            )
            val problemsByKind = def.problems.groupingBy { it.kind }.eachCount()
            if (problemsByKind.isNotEmpty()) {
                problems += def.problems.size
                println("    problems: $problemsByKind")
                for (p in def.problems.take(12)) {
                    println("      ${p.file}:${p.line} ${p.kind}: ${p.text.take(90)}")
                }
                if (def.problems.size > 12) println("      ... ${def.problems.size - 12} more")
            }
        }
    }
    if (problems > 0) {
        println("total problems: $problems")
    }
}
