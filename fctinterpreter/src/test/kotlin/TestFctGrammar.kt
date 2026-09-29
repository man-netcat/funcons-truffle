import fct.FCTLexer
import fct.FCTParser
import org.antlr.v4.runtime.BaseErrorListener
import org.antlr.v4.runtime.CharStreams
import org.antlr.v4.runtime.CommonTokenStream
import org.antlr.v4.runtime.RecognitionException
import org.antlr.v4.runtime.Recognizer
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import java.nio.file.Files
import java.nio.file.Paths
import kotlin.io.path.extension
import kotlin.io.path.name

/**
 * Conformance guards for the hand-written FCT grammar.
 *
 * CBS deliberately leaves associativity and relative priority to be "specified
 * separately" (CBS-beta/docs/Browsing.md), and the reference parser is a GLL
 * parser that tolerates ambiguity rather than resolving it in the grammar. So
 * where the CBS repository does not pin something down, these tests pin the
 * behaviour we currently have, so it cannot change silently.
 */
class TestFctGrammar {

    private fun parse(source: String): FCTParser.RootContext {
        val listener = object : BaseErrorListener() {
            override fun syntaxError(
                recognizer: Recognizer<*, *>?,
                offendingSymbol: Any?,
                line: Int,
                charPositionInLine: Int,
                msg: String,
                e: RecognitionException?
            ) = throw AssertionError("line $line:$charPositionInLine $msg")
        }
        val lexer = FCTLexer(CharStreams.fromString(source))
        lexer.removeErrorListeners()
        lexer.addErrorListener(listener)
        val parser = FCTParser(CommonTokenStream(lexer))
        parser.removeErrorListeners()
        parser.addErrorListener(listener)
        return parser.root()
    }

    /** The `root` rule now ends in EOF, so `text` carries a trailing `<EOF>` marker. */
    private fun FCTParser.RootContext.sourceText(): String = text.removeSuffix("<EOF>")

    /**
     * A bare-expression file must contain exactly one term. Without the trailing
     * EOF, `1 2` parsed as `1` and silently discarded the rest.
     */
    @Test
    fun trailingInputTest() {
        val e = assertThrowsSyntaxError { parse("1 2") }
        assertTrue(e.contains("extraneous input") || e.contains("mismatched input"), "got: $e")
    }

    @Test
    fun singleTermTest() {
        assertEquals("1", parse("1").sourceText())

        // Juxtaposed application `abstraction 12` must parse as an application of
        // `abstraction` to `12`. Asserted on the tree shape rather than getText(),
        // which concatenates children and so reports "abstraction12" now that the
        // lexer has discarded the whitespace.
        val expr = (parse("abstraction 12") as FCTParser.FCTFileContext).expr()
        assertTrue(expr is FCTParser.FunconExpressionContext)
        expr as FCTParser.FunconExpressionContext
        assertEquals("abstraction", expr.name.text)
        assertTrue(expr.args() is FCTParser.SingleArgsContext)
        assertEquals("12", (expr.args() as FCTParser.SingleArgsContext).expr().text)
    }

    /**
     * `{}` is matched by the EmptySet alternative. The rule used to read
     * `'{' WS* '}'`, but the lexer skips WS, so that repetition could never
     * match anything and the whitespace was silently ignored anyway.
     */
    @Test
    fun emptySetLiteralTest() {
        // Every spelling of an empty set literal parses, whatever the inner whitespace.
        for (source in listOf("{}", "{ }", "{\t}", "{\n}")) {
            assertTrue(parse(source).sourceText().startsWith("{"), "failed for ${source.replace("\n", "\\n")}")
        }
    }

    /**
     * CBS funcon names are `[a-z][a-zA-Z0-9-]*` and the FCT lexer declares
     * GENERAL/INPUTS/TESTS/... before IDENTIFIER, so a keyword always wins.
     * No funcon collides today; if one ever does it would silently become
     * unparseable, so fail loudly here instead.
     */
    @Test
    fun keywordCollisionTest() {
        val cbsRoot = Paths.get("../CBS-beta/Funcons-beta")
        // Both `Funcon f` and `Type t` introduce lowercase names into the same
        // namespace that the FCT lexer has to tokenize as IDENTIFIER.
        val declarations = listOf(
            Regex("""^\s*(?:Built-in\s+|Auxiliary\s+)?Funcon\s+([a-z][a-zA-Z0-9-]*)""", RegexOption.MULTILINE),
            Regex("""^\s*(?:Built-in\s+|Auxiliary\s+)?Type\s+([a-z][a-zA-Z0-9-]*)""", RegexOption.MULTILINE)
        )
        val keywordNames = listOf(
            "general", "inputs", "tests", "funcon-term",
            "standard-in", "result-term", "standard-out", "store"
        )
        val declared = mutableSetOf<String>()
        Files.walk(cbsRoot).use { paths ->
            paths.filter { it.extension == "cbs" }.forEach { path ->
                val source = Files.readString(path)
                declarations.forEach { regex ->
                    regex.findAll(source).forEach { declared.add(it.groupValues[1]) }
                }
            }
        }
        assertTrue(declared.size > 300, "expected the full funcon index, found ${declared.size}")
        val collisions = declared.filter { it in keywordNames }
        assertEquals(emptyList<String>(), collisions, "these names are shadowed by FCT keywords")
    }

    /**
     * `&` and `|` group left-associatively while `=>` groups right-associatively.
     * This falls out of ANTLR's default resolution rather than any declared
     * precedence, and CBS does not specify it, so it is pinned rather than fixed.
     * No corpus config uses these operators; see the CBS-type note on the class.
     */
    @Test
    fun typeOperatorGroupingTest() {
        // Pinned because CBS leaves relative priority to be specified separately, so
        // this is our behaviour and not the corpus's.
        assertEquals("a&b|c", parse("a & b | c").sourceText())
        assertEquals("a|b&c", parse("a | b & c").sourceText())
        assertEquals("a&b=>c", parse("a & b => c").sourceText())
        assertEquals("a=>b=>c", parse("a => b => c").sourceText())
        assertEquals("~a&b", parse("~a & b").sourceText())
    }

    private fun assertThrowsSyntaxError(block: () -> Unit): String {
        try {
            block()
        } catch (e: AssertionError) {
            return e.message ?: ""
        }
        throw AssertionError("expected a syntax error, but the input parsed")
    }
}
