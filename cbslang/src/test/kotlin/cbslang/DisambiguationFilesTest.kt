package cbslang

import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.Paths

/**
 * The generated `.disambiguation` data files, over the real corpus.
 *
 * The point of a check command is that it fails when something is wrong, so most
 * of these tests break a file on purpose and require the check to notice. A check
 * that only ever passes is indistinguishable from one that does nothing.
 */
class DisambiguationFilesTest {

    // The corpus tests read CBS-beta/Languages-beta, which is at the root; the
    // module's test task sets that as the working directory.
    private val root = Paths.get("CBS-beta", "Languages-beta")

    @Test
    fun roundTripTest(@TempDir dir: Path) {
        DisambiguationFiles.write(root, dir)
        val outcomes = DisambiguationFiles.check(root, dir)
        assertTrue(outcomes.isNotEmpty()) { "no languages were written" }
        for (outcome in outcomes) {
            assertTrue(outcome.failures.isEmpty()) {
                "${outcome.language}: ${outcome.failures.joinToString("; ")}"
            }
            assertTrue(Files.exists(outcome.file)) { "${outcome.language}: no file written" }
        }
    }

    // Each language is written as its own file, so a language whose disambiguation
    // is empty is indistinguishable from one that was never seen. The file must be
    // present and must name its language.
    @Test
    fun oneFilePerLanguageTest(@TempDir dir: Path) {
        val written = DisambiguationFiles.write(root, dir)
        val names = written.map { it.file.fileName.toString() }.sorted()
        assertTrue(names.contains("imp.disambiguation")) { "IMP missing: $names" }
        assertTrue(names.contains("ocaml-light.disambiguation")) { "OCaml Light slug missing: $names" }
        val languages = Languages.readAll(root).map { it.name }.sorted()
        assertTrue(names.size == languages.size) {
            "wrote ${names.size} files for ${languages.size} languages"
        }
        for (outcome in written) {
            val text = Files.readString(outcome.file)
            assertTrue(text.contains("\"${outcome.language}\"")) {
                "${outcome.file} does not name its language"
            }
        }
    }

    @Test
    fun missingFileTest(@TempDir dir: Path) {
        val empty = dir.resolve("empty")
        val failures = DisambiguationFiles.check(root, empty)
        assertTrue(failures.isNotEmpty()) { "nothing was checked" }
        for (outcome in failures) {
            assertTrue(outcome.failures.isNotEmpty()) {
                "${outcome.language} has no file but was reported as ok"
            }
        }
    }

    // A stale file is the failure this check exists for: the definition gained an
    // annotation and the data file did not, so a parser generated from the file
    // would accept what the definition forbids.
    @Test
    fun staleFileTest(@TempDir dir: Path) {
        val before = DisambiguationFiles.write(root, dir)
        val imp = before.first { it.language == "IMP" }
        Files.writeString(imp.file, Files.readString(imp.file).replace(" {right}", ""))

        val after = DisambiguationFiles.check(root, dir).first { it.language == "IMP" }
        assertTrue(after.failures.any { it.contains("differs") }) {
            "an edited data file was not noticed: ${after.failures}"
        }
    }

    // The renderer is a fixed point: reading a rendered file and rendering it again
    // gives the same text. This is the property that makes a generated data file
    // trustworthy, and it is not the same property as matching the definition --
    // a renderer that quietly dropped an attribute would still match the
    // definition, because the definition would have been rendered the same way.
    @Test
    fun renderIsAFixedPointTest(@TempDir dir: Path) {
        DisambiguationFiles.write(root, dir)
        for (outcome in DisambiguationFiles.check(root, dir)) {
            val onDisk = Files.readString(outcome.file)
            val again = SdfReader("data").read(outcome.language, onDisk).render()
            assertTrue(again == onDisk) {
                "${outcome.language} does not re-render to itself"
            }
        }
    }

    // A file that lost a production is both stale and short of what the definition
    // says, so this exercises the byte comparison; the fixed point above is what
    // covers a lossy renderer, which no hand edit can simulate.
    @Test
    fun damagedFileTest(@TempDir dir: Path) {
        val before = DisambiguationFiles.write(root, dir)
        val imp = before.first { it.language == "IMP" }
        val text = Files.readString(imp.file)
        val damaged = text.replace("  ``aexp ::= aexp '+' aexp`` {assoc}\n", "")
        assertTrue(damaged != text) { "the test did not modify the file" }
        Files.writeString(imp.file, damaged)

        val after = DisambiguationFiles.check(root, dir).first { it.language == "IMP" }
        assertTrue(after.failures.any { it.contains("differs") }) {
            "a data file that lost an annotation was reported as ok: ${after.failures}"
        }
    }

    // The outcome a caller acts on has to be the one read from the file, not the one
    // still in memory, or a check would pass on data no consumer would ever see.
    @Test
    fun outcomeComesFromTheFileTest(@TempDir dir: Path) {
        val written = DisambiguationFiles.write(root, dir)
        val checked = DisambiguationFiles.check(root, dir)
        for (outcome in checked) {
            val fromDefinition = written.first { it.language == outcome.language }
            assertTrue(outcome.disambiguation.render() == fromDefinition.disambiguation.render()) {
                "${outcome.language} does not round-trip to the same data"
            }
        }
    }

    @Test
    fun slugTest() {
        assertTrue(DisambiguationFiles.slug("IMP") == "imp") { "IMP slug" }
        assertTrue(DisambiguationFiles.slug("OCaml Light") == "ocaml-light") { "OCaml Light slug" }
        assertTrue(DisambiguationFiles.slug("SL") == "sl") { "SL slug" }
    }
}
