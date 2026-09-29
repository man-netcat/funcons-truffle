package cbslang

import java.nio.file.Paths
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

/**
 * Reads the five vendored language definitions end to end. These are the
 * languages the front-end has to support, so the reader is expected to account
 * for every construct they use; anything it cannot place is a defect to fix
 * rather than a limitation to work around.
 */
class CorpusTest {

    private val root = Paths.get("CBS-beta", "Languages-beta")

    private val languages: List<LanguageDef> by lazy { Languages.readAll(root) }

    private fun language(name: String): LanguageDef =
        languages.singleOrNull { it.name == name }
            ?: error("no language '$name' among ${languages.map { it.name }}")

    @Test
    fun `finds the five vendored languages and nothing else`() {
        assertEquals(
            listOf("IMP", "MiniJava", "OCaml Light", "SIMPLE", "SL"),
            languages.map { it.name },
        )
    }

    @Test
    fun `no vendored file fails to read`() {
        val failures = languages.flatMap { def ->
            def.problems.filter { it.kind == "unreadable" }.map { "${it.file}: ${it.text}" }
        }
        assertEquals(emptyList<String>(), failures)
    }

    @Test
    fun `no vendored file leaves grammar text unplaced`() {
        val unplaced = languages.flatMap { def ->
            def.problems
                .filter { it.kind == "unexpected token in grammar" || it.kind == "unexpected token" }
                .map { "${def.name} ${it.file}:${it.line} ${it.text}" }
        }
        assertEquals(emptyList<String>(), unplaced)
    }

    @Test
    fun `every problem the reader reports is a known characteristic of the corpus`() {
        val kinds = languages.associate { def ->
            def.name to def.problems.groupingBy { it.kind }.eachCount().keys
        }
        val expected = mapOf(
            "IMP" to setOf("disambiguation"),
            "MiniJava" to setOf("disambiguation"),
            "OCaml Light" to setOf(
                "disambiguation",
                // The '\' and '\'' terminals are the same three characters.
                "backslash in terminal",
                // Nine `Type` declarations in OC-L-02 are entirely commented out.
                "Type without ~>",
                // Two blocks of funcon documentation under bare-word headings.
                "prose",
                // Funcon equations listed under `Funcon` headings.
                "Rule that is not a rule",
            ),
            "SIMPLE" to setOf("disambiguation", "Rule that is not a rule"),
            "SL" to setOf("disambiguation", "backslash in terminal", "Rule that is not a rule"),
        )
        assertEquals(expected, kinds)
    }

    @Test
    fun `every language declares a start nonterminal in a start file`() {
        for (def in languages) {
            val startFile = def.files.single { it.endsWith("-Start.cbs") }
            assertTrue(
                def.declarations.any { it.file == startFile },
                "$def.name: no declaration in $startFile",
            )
        }
    }

    @Test
    fun `IMP is read completely`() {
        val imp = language("IMP")
        assertEquals(6, imp.files.size)
        assertEquals(11, imp.nonterminals.size)
        assertEquals(25, imp.rules.size)
        assertEquals(8, imp.semantics.size)
        assertEquals(
            listOf("aexp", "num", "decimal", "id", "keyword", "bexp", "stmt", "block", "pgm", "idlist", "start"),
            imp.nonterminals,
        )
    }

    @Test
    fun `IMP's addition rule keeps its funcon body as text`() {
        val imp = language("IMP")
        val rule = imp.rules.single { it.pattern == "AExp1 '+' AExp2" }
        assertEquals("eval-arith", rule.function)
        assertEquals("integer-add(eval-arith[[ AExp1 ]], eval-arith[[ AExp2 ]])", rule.body)
    }

    @Test
    fun `IMP's string rule keeps its escaped literal intact`() {
        val imp = language("IMP")
        val rule = imp.rules.single { it.function == "id" }
        assertEquals("I", rule.pattern)
        // Raw source, so the file's `\"` escaping survives.
        assertEquals("\\\"I\\\"", rule.body)
    }

    @Test
    fun `OCaml-Light's backslash and quote terminals are distinguished`() {
        val ocaml = language("OCaml Light")
        val backslash = ocaml.rules.single { it.function == "capture" && it.body == "backslash" }
        assertEquals("'\\' '\\'", backslash.pattern)
        val quote = ocaml.rules.single { it.function == "capture" && it.body == "'\\''" }
        assertEquals("'\\' '\\''", quote.pattern)
    }

    @Test
    fun `SL's negated character set keeps both members`() {
        val sl = language("SL")
        val stringChars = sl.declarations.single { it.nonterminal == "string-char" }
        assertEquals(
            Ebnf.Negated(listOf(CharRangeAndTerminal("\"", null), CharRangeAndTerminal("\\n", null))),
            stringChars.body,
        )
    }

    @Test
    fun `MiniJava's grammar comes from Dynamics, not from the commented Syntax file`() {
        val minijava = language("MiniJava")
        val fromSyntax = minijava.declarations.filter { it.file.contains("Syntax") }
        // MiniJava-Syntax.cbs is entirely commented out except for reserved-id.
        assertEquals(listOf("reserved-id"), fromSyntax.map { it.nonterminal })
        val files = minijava.declarations.map { it.file }.toSet()
        assertTrue(files.any { it.contains("Dynamics") })
        assertTrue(files.any { it.contains("Start") })
    }
}
