package cbslang

import java.nio.file.Files
import java.nio.file.Path

/**
 * The per-language `.disambiguation` data files.
 *
 * One file per language, holding only that language's disambiguation, written from
 * the SDF3 blocks in its language definition. The parser generator reads these
 * rather than re-parsing the definitions, so a generated parser cannot disagree
 * with the disambiguation it was generated from.
 *
 * [check] is the counterpart of [write] and is deliberately stricter. Writing
 * only proves the renderer ran. Checking proves two separate things: that the
 * file still says what the language definition says, and that the file can be read
 * back and re-rendered to itself. The second is the one that catches a renderer
 * losing information, because a lossy rendering is still a syntactically valid
 * file that a human would never notice was wrong.
 */
object DisambiguationFiles {

    /**
     * What happened to one language's data file.
     *
     * [failures] is empty when the file is present and agrees with its definition.
     * [problems] is the notation the reader could not place, which is reported
     * separately because a language can be both correctly written and still
     * contain notation we do not yet understand.
     */
    data class Outcome(
        val language: String,
        val file: Path,
        val disambiguation: Disambiguation,
        val problems: List<Problem>,
        val failures: List<String>,
    ) {
        val ok: Boolean get() = failures.isEmpty()
    }

    /** Writes one data file per language found under [root], into [out]. */
    fun write(root: Path, out: Path): List<Outcome> =
        definitions(root).map { def ->
            val file = out.resolve(slug(def.name) + ".disambiguation")
            Files.createDirectories(file.parent)
            Files.writeString(file, def.disambiguation.render())
            Outcome(def.name, file, def.disambiguation, def.problems, emptyList())
        }

    /**
     * Checks the data files in [dir] against the language definitions under [root].
     *
     * The disambiguation used in the result is the one read back from the file,
     * not the one in the definition, so a caller that acts on an [Outcome] acts on
     * what a consumer of the file would actually see.
     */
    fun check(root: Path, dir: Path): List<Outcome> =
        definitions(root).map { def ->
            val file = dir.resolve(slug(def.name) + ".disambiguation")
            if (!Files.exists(file)) {
                return@map Outcome(
                    def.name, file, def.disambiguation, def.problems,
                    listOf("no data file; run with --write-disambiguation"),
                )
            }
            val onDisk = Files.readString(file)
            val failures = mutableListOf<String>()
            if (onDisk != def.disambiguation.render()) {
                failures += "differs from what the language definition renders"
            }
            val reader = SdfReader(file.fileName.toString())
            val reread = reader.read(def.name, onDisk)
            if (reread.render() != onDisk) {
                failures += "does not survive being read back and re-rendered"
            }
            Outcome(def.name, file, reread, def.problems + reader.problems, failures)
        }

    private fun definitions(root: Path): List<LanguageDef> =
        Languages.readAll(root).filter { it.name != "<failed>" }

    /** A file name for a language, e.g. `OCaml Light` becomes `ocaml-light`. */
    fun slug(name: String): String =
        name.lowercase().map { if (it.isLetterOrDigit()) it else '-' }
            .joinToString("")
            .trim('-')
            .replace(Regex("-+"), "-")
}
