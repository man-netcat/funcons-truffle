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
            val source = try {
                Files.readString(file)
            } catch (e: Exception) {
                failures += file to CBSLangException("cannot read: $e", file.name, 0, 0)
                continue
            }
            val read = try {
                Reader(source, file.name).read()
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
            // The disambiguation lives in the SDF3 blocks in the comments, so
            // it is read from the same file rather than transcribed separately.
            val sdf = SdfReader(file.name)
            val disambiguation = sdf.read(name, source)
            grouped.getOrPut(name) { mutableListOf() } += read.copy(
                disambiguation = disambiguation,
                disambiguationProblems = sdf.problems,
            )
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
            disambiguation = Disambiguation("<failed>", emptyList(), emptyList(), emptyList(), emptyList()),
        )
    }

    private fun merge(name: String, reads: List<FileRead>): LanguageDef {
        val def = LanguageDef(
            name = name,
            files = reads.map { it.file },
            declarations = reads.flatMap { it.declarations },
            types = reads.flatMap { it.types },
            semantics = reads.flatMap { it.semantics },
            rules = reads.flatMap { it.rules },
            headings = reads.flatMap { it.headings },
            index = reads.flatMap { it.index },
            imports = reads.flatMap { it.imports },
            problems = reads.flatMap { it.problems } + reads.flatMap { it.disambiguationProblems },
            disambiguation = mergeDisambiguation(name, reads),
        )
        // Checked once the whole language is assembled, because a production can
        // be declared in a different file from the disambiguation that names it.
        val undeclared = def.undeclaredProductions.map {
            Problem(name, 0, "production the disambiguation names is not declared", it.render())
        }
        return if (undeclared.isEmpty()) def else def.copy(problems = def.problems + undeclared)
    }

    /**
     * A language states its disambiguation in more than one file, and the
     * sections are separate rather than alternatives, so they are concatenated
     * in file order. `Syntax SDF` and `Lexis SDF` may both appear in one file,
     * and the reader keeps them apart, so nothing is lost.
     */
    private fun mergeDisambiguation(name: String, reads: List<FileRead>): Disambiguation {
        val parts = reads.mapNotNull { it.disambiguation }.filter { !it.isEmpty }
        if (parts.isEmpty()) return Disambiguation(name, emptyList(), emptyList(), emptyList(), emptyList())
        return Disambiguation(
            language = name,
            rejections = parts.flatMap { it.rejections },
            restrictions = parts.flatMap { it.restrictions },
            annotated = parts.flatMap { it.annotated },
            priorities = parts.flatMap { it.priorities },
        )
    }
}
