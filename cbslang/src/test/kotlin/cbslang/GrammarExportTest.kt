package cbslang

import org.junit.jupiter.api.Test
import java.nio.file.Paths

/**
 * Writes every grammar in the corpus to a directory, so the ANTLR tool can be run
 * over all of them and the ones it rejects are visible at once.
 *
 * This is a scratch tool rather than a test: it asserts nothing, and the result is
 * meant to be read from the console, not from a report. The build task it stands in
 * for is the one that has to replace it.
 */
class GrammarExportTest {

    @Test
    fun exportTest() {
        val dir = Paths.get("build", "grammars")
        java.nio.file.Files.createDirectories(dir)
        for (def in Languages.readAll(Paths.get("CBS-beta", "Languages-beta"))) {
            if (def.name == "<failed>") continue
            val name = DisambiguationFiles.slug(def.name).replaceFirstChar { it.uppercase() }
            val file = dir.resolve(GrammarWriter.fileName(def, name))
            java.nio.file.Files.writeString(file, GrammarWriter.write(def, name))
            println("wrote $file")
        }
    }
}
