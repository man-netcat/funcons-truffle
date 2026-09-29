package cbslang

import java.nio.file.Files
import java.nio.file.Path
import kotlin.io.path.extension
import kotlin.io.path.name

/**
 * Assembles [LanguageDef]s from CBS language-definition directories.
 *
 * A language is spread over several files — one per section, plus a
 * disambiguation file and a start file — and every one of them repeats
 * `Language "..."`. Files are therefore grouped by the name they declare rather
 * than by directory layout, which differs between the vendored languages
 * (`MiniJava/MiniJava-cbs/MiniJava/…` nests three deep, `SL/SL-cbs/SL/…`
 * two).
 */
object Languages {

    fun readAll(root: Path): List<LanguageDef> {
        val files = Files.walk(root).use { stream ->
            stream.filter { Files.isRegularFile(it) && it.extension == "cbs" }.sorted().toList()
        }
        val grouped = LinkedHashMap<String, MutableList<FileRead>>()
        val failures = mutableListOf<Pair<Path, CBSLangException>>()

        for (file in files) {
            val read = try {
                Reader(Files.readString(file), file.name).read()
            } catch (e: CBSLangException) {
                failures += file to e
                continue
            }
            val name = read.language
            if (name == null) {
                // Not a language-definition file (for example a funcon library
                // .cbs). Silently skipped rather than reported as a problem.
                continue
            }
            grouped.getOrPut(name) { mutableListOf() } += read
        }

        val defs = grouped.map { (name, reads) -> merge(name, reads) }
        return if (failures.isEmpty()) defs else defs + failedLanguage(failures)
    }

    private fun failedLanguage(failures: List<Pair<Path, CBSLangException>>): LanguageDef {
        val problems = failures.map { (file, e) ->
            Problem(file.name, e.line, "unreadable", e.message ?: "read failed")
        }
        return LanguageDef(
            name = "<failed>",
            files = failures.map { it.first.toString() },
            declarations = emptyList(),
            types = emptyList(),
            semantics = emptyList(),
            rules = emptyList(),
            headings = emptyList(),
            index = emptyList(),
            imports = emptyList(),
            problems = problems,
        )
    }

    private fun merge(name: String, reads: List<FileRead>) = LanguageDef(
        name = name,
        files = reads.map { it.file },
        declarations = reads.flatMap { it.declarations },
        types = reads.flatMap { it.types },
        semantics = reads.flatMap { it.semantics },
        rules = reads.flatMap { it.rules },
        headings = reads.flatMap { it.headings },
        index = reads.flatMap { it.index },
        imports = reads.flatMap { it.imports },
        problems = reads.flatMap { it.problems },
    )
}
