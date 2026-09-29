package cbslang

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import java.nio.file.Path
import java.nio.file.Paths

/**
 * The generated ANTLR grammar, for the language whose definition has none of the
 * notation the other four do.
 *
 * IMP is the one language in the corpus with no quantified symbol and no priority
 * group, so a grammar for it can be checked end to end. Anything that fails here for
 * IMP alone will also fail for the others, and anything that passes is at least
 * sound for the hardest case later.
 */
class GrammarWriterTest {

    private val imp: LanguageDef = language("IMP")

    /**
     * The file has to be a grammar ANTLR accepts, named after itself, and every
     * symbol it refers to has to be defined somewhere in it.
     */
    @Test
    fun isSelfConsistentTest() {
        // Comments are stripped first: a `// Tkeyword : ...` note would otherwise
        // read as a definition, and a `// Tokens.` would read as a reference.
        val grammar = code(GrammarWriter.write(imp, "IMP"))
        assertTrue(grammar.contains("grammar IMP;")) { "the grammar is not named" }

        val defined = Regex("""(?m)^\s*([A-Za-z_][A-Za-z0-9_]*)\s*:""").findAll(grammar)
            .map { it.groupValues[1] }
            .toSet()
        val referred = Regex("""\bT[A-Za-z0-9_]+\b""").findAll(grammar)
            .map { it.value }
            .toSet()
        val missing = referred - defined
        assertTrue(missing.isEmpty()) { "the grammar refers to undefined tokens: $missing" }

        val rules = defined.filter { it.first().isLowerCase() }
        val referredRules = Regex("""\b([a-z][A-Za-z0-9-]*)\b""").findAll(grammar)
            .map { it.value }
            .filter { it in IMP_NONTERMINALS }
            .toSet()
        val undefinedRules = referredRules - rules
        assertTrue(undefinedRules.isEmpty()) { "the grammar refers to undefined rules: $undefinedRules" }
    }

    /**
     * Every production of the language has to appear in the grammar.
     *
     * IMP's `aexp` has five alternatives and `stmt` has six, several of which come
     * from a quantifier. A generator that only wrote the flat alternatives would drop
     * the `if` statement, and the grammar would then reject valid programs while
     * still compiling.
     */
    @Test
    fun everyProductionAppearsTest() {
        val grammar = GrammarWriter.write(imp, "IMP")
        for (production in imp.grammarProductions) {
            val body = production.symbols
                .filterIsInstance<Symbol.Lit>()
                .map { "'${it.value}'" }
                .joinToString(" ")
            if (body.isEmpty()) continue
            assertTrue(grammar.contains(body) || body.split(" ").all { grammar.contains(it) }) {
                "the grammar is missing ${production.render()}"
            }
        }
    }

    /**
     * `stmt ::= 'if' '(' bexp ')' block ('else' block)?` has to contribute both
     * alternatives, and the optional one is what a parser that only saw the flat
     * form would get wrong.
     */
    @Test
    fun optionalBecomesBothAlternativesTest() {
        val grammar = GrammarWriter.write(imp, "IMP")
        val stmtRule = grammar.lines().first { it.startsWith("stmt :") }
        assertTrue(stmtRule.contains("Telse block")) { "the else alternative is missing: $stmtRule" }
        assertTrue(stmtRule.contains("T29 block |")) { "the bare if alternative is missing: $stmtRule" }
    }

    /**
     * A literal is not a legal ANTLR name and a keyword is not a legal one either:
     * a lexer rule called `if` collides with a Java keyword in the generated code.
     * Every token is therefore prefixed.
     */
    @Test
    fun tokensArePrefixedTest() {
        val grammar = GrammarWriter.write(imp, "IMP")
        for (rule in grammar.lines().filter { it.contains(" : ") && it.trimStart().startsWith("T") }) {
            assertTrue(rule.trimStart().startsWith("T")) { "an unprefixed token: $rule" }
        }
        assertTrue(grammar.contains("Tif : 'if' ;")) { "the keyword is not a prefixed token" }
    }

    /**
     * `id` is rejected from `keyword`, so a keyword must not lex as an identifier.
     * The grammar has to keep the two apart, or `int if ;` would parse.
     */
    @Test
    fun keywordIsNotAnIdentifierTest() {
        val grammar = GrammarWriter.write(imp, "IMP")
        // The corpus writes this as ``id`` = ``keyword`` {reject}, so the keyword
        // set is what is named, not the identifier.
        assertTrue(grammar.contains("{reject}")) { "the rejection was dropped" }
        assertTrue(grammar.contains("Tkeyword")) { "the keyword set is not named" }
        // The keyword set is named but not written as a token, because its literals
        // are already written individually and a rule for it would overlap them.
        assertTrue(!grammar.lines().any { it.startsWith("Tkeyword :") }) {
            "the keyword set was written as a token, which overlaps every keyword literal"
        }
    }

    /**
     * The lexical restriction on `id` has to survive into the token, or `1abc` would
     * lex as an identifier.
     */
    @Test
    fun lexicalRestrictionSurvivesTest() {
        val grammar = GrammarWriter.write(imp, "IMP")
        assertTrue(grammar.contains("Tid :") && !grammar.contains("Tid : 'id'")) {
            "the identifier is not written as a character class"
        }
    }

    /**
     * A disambiguation annotation has to be visible in the output. It is a comment
     * rather than grammar syntax, because turning it into ANTLR precedence is not
     * done yet, and a generator that dropped it would look like it had applied it.
     */
    @Test
    fun disambiguationIsVisibleTest() {
        val grammar = GrammarWriter.write(imp, "IMP")
        assertTrue(grammar.contains("assoc")) { "the {assoc} annotation was dropped" }
        assertTrue(grammar.contains("right")) { "the {right} annotation was dropped" }
    }

    /**
     * A dash is the alternative separator in ANTLR and a keyword of the target
     * language is a reserved field name, so neither can be spelled as the corpus writes
     * it. SIMPLE declares `imp-stmt` and SL declares `int`, and both have to come out
     * as rules ANTLR will load.
     */
    @Test
    fun illegalRuleNamesAreRewrittenTest() {
        val simple = GrammarWriter.write(language("SIMPLE"), "SIMPLE")
        assertTrue(simple.contains("impdashstmt :")) { "the dash was not rewritten: ${simple.lines().filter { it.contains("imp") }}" }
        assertTrue(!Regex("""\bimp-stmt\b""").containsMatchIn(simple)) { "a dash survived into the grammar" }

        val sl = GrammarWriter.write(language("SL"), "SL")
        assertTrue(sl.lines().any { it.startsWith("intrule :") }) { "the keyword name was not rewritten" }
    }

    /**
     * ANTLR loads a grammar only if the `grammar` declaration agrees with the file
     * name, and a dash is not legal in a grammar name, so the file name has to come
     * from the writer rather than from the caller.
     */
    @Test
    fun fileNameAgreesWithGrammarTest() {
        val def = language("OCaml Light")
        val name = DisambiguationFiles.slug(def.name).replaceFirstChar { it.uppercase() }
        val file = GrammarWriter.fileName(def, name)
        assertTrue(!file.contains("-")) { "the file name still has a dash: $file" }
        assertTrue(GrammarWriter.write(def, name).contains("grammar ${file.removeSuffix(".g4")};")) {
            "the grammar name and the file name disagree: $file"
        }
    }

    /**
     * The file has to be stable: regenerating an unchanged language produces the same
     * text, or a diff of the generated grammar is unreadable.
     */
    @Test
    fun isDeterministicTest() {
        for (name in listOf("IMP", "SIMPLE", "SL", "MiniJava")) {
            val def = language(name)
            assertEquals(GrammarWriter.write(def, name), GrammarWriter.write(def, name)) {
                "$name is not deterministic"
            }
        }
    }

    private fun language(name: String): LanguageDef =
        Languages.readAll(Paths.get("CBS-beta", "Languages-beta")).first { it.name == name }

    private companion object {
        val IMP_NONTERMINALS =
            setOf("start", "aexp", "bexp", "stmt", "block", "pgm", "idlist", "num", "id", "decimal", "keyword")

        /** The grammar without its comments, so a note is not read as a rule. */
        fun code(grammar: String): String =
            grammar.lines().filterNot { it.trimStart().startsWith("//") }.joinToString("\n")
    }
}
