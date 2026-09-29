package cbslang

import java.nio.file.Paths
import kotlin.system.exitProcess

/**
 * Reads CBS language definitions and reports what was found, including any
 * notation the reader could not place.
 *
 * `--write-disambiguation <dir>` writes one normalised disambiguation data file
 * per language, and `--check-disambiguation <dir>` verifies such a directory
 * still matches the language definitions. Both are the form the parser generator
 * consumes, which is what keeps a generated parser from disagreeing with the
 * disambiguation it was generated from.
 */
fun main(args: Array<String>) {
    val usage = "usage: cbslang [--write-disambiguation <dir> | --check-disambiguation <dir>] " +
        "<directory-of-language-definitions>..."

    var mode: String? = null
    var dir: String? = null
    val roots = mutableListOf<String>()
    var i = 0
    while (i < args.size) {
        when (val arg = args[i]) {
            "--write-disambiguation", "--check-disambiguation" -> {
                mode = arg
                dir = args.getOrNull(i + 1)
                i += 2
            }

            "-h", "--help" -> {
                println(usage)
                return
            }

            else -> {
                roots += arg
                i++
            }
        }
    }
    if (roots.isEmpty()) {
        println(usage)
        exitProcess(2)
    }
    // A missing directory argument would otherwise be read as a language root,
    // and the run would report that a directory which was meant to hold the
    // output does not contain any languages.
    if (mode != null && dir == null) {
        println("$mode needs a directory")
        println(usage)
        exitProcess(2)
    }

    when (mode) {
        "--write-disambiguation" -> {
            val out = Paths.get(dir!!)
            for (outcome in DisambiguationFiles.write(Paths.get(roots[0]), out)) {
                report(outcome)
            }
        }

        "--check-disambiguation" -> {
            val dir = Paths.get(dir!!)
            var failed = 0
            for (outcome in DisambiguationFiles.check(Paths.get(roots[0]), dir)) {
                report(outcome)
                failed += outcome.failures.size
            }
            if (failed > 0) {
                println("$failed data file(s) are not in step with the language definitions")
                exitProcess(1)
            }
        }

        else -> {
            var problems = 0
            for (root in roots) {
                val defs = Languages.readAll(Paths.get(root))
                println("=== $root : ${defs.size} language(s)")
                for (def in defs) {
                    val d = def.disambiguation
                    println(
                        "  ${def.name}: ${def.files.size} files, " +
                            "${def.declarations.size} declarations, ${def.nonterminals.size} nonterminals, " +
                            "${def.rules.size} rules, ${def.semantics.size} semantics, " +
                            "${def.types.size} types, ${def.index.size} index entries",
                    )
                    println(
                        "    disambiguation: ${d.rejections.size} rejections, " +
                            "${d.restrictions.size} restrictions, ${d.annotated.size} annotated, " +
                            "${d.priorities.size} priority steps",
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
    }
}

private fun report(outcome: DisambiguationFiles.Outcome) {
    val d = outcome.disambiguation
    val status = if (outcome.ok) "ok" else "FAILED (${outcome.failures.joinToString("; ")})"
    println(
        "${outcome.language} ${outcome.file.fileName} $status: " +
            "${d.rejections.size} rejections, ${d.restrictions.size} restrictions, " +
            "${d.annotated.size} annotated, ${d.priorities.size} priority steps",
    )
    val problemsByKind = outcome.problems.groupingBy { it.kind }.eachCount()
    if (problemsByKind.isNotEmpty()) {
        println("  problems: $problemsByKind")
    }
}
