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
    fun vendoredLanguagesTest() {
        // Nothing else: the corpus also holds funcon library .cbs files,
        // which declare no language and are skipped.
        assertEquals(
            listOf("IMP", "MiniJava", "OCaml Light", "SIMPLE", "SL"),
            languages.map { it.name },
        )
    }

    @Test
    fun allFilesReadTest() {
        // A file that cannot be read becomes an unreadable language rather than
        // being dropped, so this asserts that path is not taken.
        val failures = languages.flatMap { def ->
            def.problems.filter { it.kind == "unreadable" }.map { "${it.file}: ${it.text}" }
        }
        assertEquals(emptyList<String>(), failures)
    }

    @Test
    fun unplacedGrammarTest() {
        // Grammar text the reader cannot place means part of a language definition
        // was silently dropped.
        val unplaced = languages.flatMap { def ->
            def.problems
                .filter { it.kind == "unexpected token in grammar" || it.kind == "unexpected token" }
                .map { "${def.name} ${it.file}:${it.line} ${it.text}" }
        }
        assertEquals(emptyList<String>(), unplaced)
    }

    @Test
    fun knownProblemsTest() {
        // An allowlist of the corpus's known defects. A new problem kind, or a
        // known kind turning up in another language, fails here rather than
        // passing unnoticed.
        val kinds = languages.associate { def ->
            def.name to def.problems.groupingBy { it.kind }.eachCount().keys
        }
        // `disambiguation` is no longer a problem: the SDF3 blocks in the
        // comments are read by SdfReader, and what it cannot read is reported
        // under its own kinds.
        val expected = mapOf(
            "IMP" to emptySet(),
            "MiniJava" to emptySet(),
            "OCaml Light" to setOf(
                // The '\' and '\'' terminals are the same three characters.
                "backslash in terminal",
                // Nine `Type` declarations in OC-L-02 are entirely commented out.
                "Type without ~>",
                // Two blocks of funcon documentation under bare-word headings.
                "prose",
                // Funcon equations listed under `Funcon` headings.
                "Rule that is not a rule",
                // `(pattern comma-pattern*)` and `Pattern (':' pattern)*` are
                // grouping meta-productions, which have no flat form to annotate.
                "group meta-production",
                // The disambiguation prioritises `expr '.' field '<-' expr`, but
                // the grammar declares `expr '.(' expr ')' '<-' expr`. The
                // annotation cannot be applied to anything the parser produces.
                "production the disambiguation names is not declared",
            ),
            "SIMPLE" to setOf("Rule that is not a rule"),
            "SL" to setOf("backslash in terminal", "Rule that is not a rule"),
        )
        assertEquals(expected, kinds)
    }

    @Test
    fun disambiguationPresentTest() {
        // Each language states its disambiguation in SDF3 comments. An empty one
        // here would mean the blocks were never found, and every other
        // disambiguation test would then pass on nothing.
        for (def in languages) {
            val d = def.disambiguation
            assertTrue(
                !d.isEmpty,
                "${def.name}: no disambiguation read from ${def.files}",
            )
            assertEquals(def.name, d.language)
        }
    }

    @Test
    fun declaredProductionsTest() {
        // The vendored corpus has exactly one mismatch, in OCaml Light: the
        // disambiguation prioritises `expr '.' field '<-' expr` while the grammar
        // declares `expr '.(' expr ')' '<-' expr`. `merge` reports it, so it is
        // asserted here as a known defect rather than silently tolerated.
        val known: Map<String, Set<String>> = mapOf(
            "IMP" to emptySet(),
            "MiniJava" to emptySet(),
            "OCaml Light" to setOf("``expr ::= expr '.' field '<-' expr``"),
            "SIMPLE" to emptySet(),
            "SL" to emptySet(),
        )
        for (def in languages) {
            val undeclared = def.undeclaredProductions.map { it.render() }.toSet()
            val expected: Set<String> = known[def.name] ?: emptySet<String>()
            assertEquals(expected, undeclared, "${def.name}: undeclared productions")
        }
    }

    @Test
    fun startNonTerminalTest() {
        // Each language has a `*-Start.cbs`; that is where it says which nonterminal
        // parsing begins at.
        for (def in languages) {
            val startFile = def.files.single { it.endsWith("-Start.cbs") }
            assertTrue(
                def.declarations.any { it.file == startFile },
                "$def.name: no declaration in $startFile",
            )
        }
    }

    @Test
    fun impReadTest() {
        // IMP is the reference language: small enough that its counts can be exact
        // rather than a smoke test.
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
    fun impAdditionTest() {
        // The body stays raw source text. The funcon term is interpreted later, so the
        // reader must not unescape or parse it here.
        val imp = language("IMP")
        val rule = imp.rules.single { it.pattern == "AExp1 '+' AExp2" }
        assertEquals("eval-arith", rule.function)
        assertEquals("integer-add(eval-arith[[ AExp1 ]], eval-arith[[ AExp2 ]])", rule.body)
    }

    @Test
    fun impStringTest() {
        val imp = language("IMP")
        val rule = imp.rules.single { it.function == "id" }
        assertEquals("I", rule.pattern)
        // Raw source, so the file's `\"` escaping survives.
        assertEquals("\\\"I\\\"", rule.body)
    }

    @Test
    fun backslashVsQuoteTest() {
        // OCaml-Light writes the backslash terminal as '\' and the quote terminal
        // as '\''. They differ only in a following quote, so the two must not be
        // merged.
        val ocaml = language("OCaml Light")
        val backslash = ocaml.rules.single { it.function == "capture" && it.body == "backslash" }
        assertEquals("'\\' '\\'", backslash.pattern)
        val quote = ocaml.rules.single { it.function == "capture" && it.body == "'\\''" }
        assertEquals("'\\' '\\''", quote.pattern)
    }

    @Test
    fun negatedCharSetTest() {
        // SL's `string-char` excludes the quote and the newline. A negated set that
        // lost its negation would accept both.
        val sl = language("SL")
        val stringChars = sl.declarations.single { it.nonterminal == "string-char" }
        assertEquals(
            Ebnf.Negated(listOf(CharRangeAndTerminal("\"", null), CharRangeAndTerminal("\\n", null))),
            stringChars.body,
        )
    }

    @Test
    fun miniJavaSourceTest() {
        val minijava = language("MiniJava")
        val fromSyntax = minijava.declarations.filter { it.file.contains("Syntax") }
        // MiniJava-Syntax.cbs is entirely commented out except for reserved-id.
        assertEquals(listOf("reserved-id"), fromSyntax.map { it.nonterminal })
        val files = minijava.declarations.map { it.file }.toSet()
        assertTrue(files.any { it.contains("Dynamics") })
        assertTrue(files.any { it.contains("Start") })
    }
}
