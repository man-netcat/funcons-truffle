package cbslang

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class ReaderTest {

    private fun read(source: String): FileRead = Reader(source, "test").read()

    /**
     * Shaped like the vendored files: one `Semantics` keyword per signature, and
     * a `Rule` keyword per rule, each on its own line.
     */
    private val toy = """
        Language "Toy"

        #1 Expressions

        [ #1 Expressions ]

        Lexis
          Z : int ::= ('0'-'9')+
          W : white ::= ( ' ' | '\t' )+
        Syntax
          A : aexp ::= int | aexp '+' aexp | '(' aexp ')' | id
          id ::= ('a'-'z')+
        Semantics
          run[[ P:program ]] : => null-type
        Semantics
          eval[[ A:aexp ]] : => integers
        Type
          ints ~> integers | nats
        Rule
          run[[ P ]] = execute[[ P ]]
        Rule
          eval[[ N ]] = int-val[[ N ]]
        Rule
          eval[[ A1 '+' A2 ]] =
            integer-add(
              eval[[ A1 ]],
              eval[[ A2 ]])
        Rule
          [[ '(' P ')' ]] : pattern = [[ P ]]
        Imports
          integers
          booleans
    """.trimIndent()

    @Test
    fun `reads the language name and the sections`() {
        val read = read(toy)
        assertEquals("Toy", read.language)
        assertEquals(4, read.declarations.size)
        assertEquals(2, read.semantics.size)
        assertEquals(1, read.types.size)
        assertEquals(4, read.rules.size)
        assertEquals(1, read.index.size)
        assertEquals(listOf("integers", "booleans"), read.imports)
        assertEquals(listOf("1 Expressions"), read.headings.map { it.text })
    }

    @Test
    fun `reads a category, a nonterminal and an EBNF body`() {
        val read = read(toy)
        val aexp = read.declarations.single { it.category == "A" }
        assertEquals("aexp", aexp.nonterminal)
        assertEquals(
            Ebnf.Alternatives(
                listOf(
                    Ebnf.Reference("int"),
                    Ebnf.Sequence(
                        listOf(
                            Ebnf.Reference("aexp"),
                            Ebnf.Terminal("+"),
                            Ebnf.Reference("aexp"),
                        ),
                    ),
                    Ebnf.Sequence(
                        listOf(
                            Ebnf.Terminal("("),
                            Ebnf.Reference("aexp"),
                            Ebnf.Terminal(")"),
                        ),
                    ),
                    Ebnf.Reference("id"),
                ),
            ),
            aexp.body,
        )
    }

    @Test
    fun `reads a character range`() {
        val read = read(toy)
        val int = read.declarations.single { it.nonterminal == "int" }
        // Parentheses group, they do not sequence, so `('0'-'9')` is the range
        // itself rather than a one-element sequence.
        assertEquals(
            Ebnf.Repeated(Ebnf.CharRange("0", "9"), Ebnf.Quantifier.ONE_OR_MORE),
            int.body,
        )
    }

    @Test
    fun `reads a negated character set with and without parentheses`() {
        for (source in listOf("~( 'a' | 'b' )", "~'a'")) {
            val read = read("Language \"X\"\nSyntax\n  c ::= $source\n")
            assertEquals(1, read.declarations.size, "failed on $source")
            assertTrue(read.declarations.single().body is Ebnf.Negated, "failed on $source")
        }
        val bracketed = read("Language \"X\"\nSyntax\n  c ::= ~( 'a' | 'b' )\n")
        assertEquals(
            Ebnf.Negated(listOf(CharRangeAndTerminal("a", null), CharRangeAndTerminal("b", null))),
            bracketed.declarations.single().body,
        )
    }

    @Test
    fun `reads a semantic function argument and return type`() {
        val read = read(toy)
        val run = read.semantics.single { it.function == "run" }
        assertEquals("P", run.patternVariable)
        assertEquals("program", run.category)
        assertEquals("null-type", run.returnType)
    }

    @Test
    fun `reads a type declaration and its members`() {
        val read = read(toy)
        val ints = read.types.single()
        assertEquals("ints", ints.name)
        assertEquals(listOf("integers", "|", "nats"), ints.members)
    }

    @Test
    fun `reads a rule pattern and body, keeping the body as text`() {
        val read = read(toy)
        val add = read.rules.single { it.function == "eval" && it.pattern.contains("'+'") }
        assertEquals(listOf("A1", "'+'", "A2"), add.patternTokens)
        assertEquals(
            // Raw source, so the body's own layout and indentation survive.
            "integer-add(\n" +
                "      eval[[ A1 ]],\n" +
                "      eval[[ A2 ]])",
            add.body,
        )
    }

    @Test
    fun `reads a rule that spans several lines`() {
        val add = read(toy).rules.single { it.pattern.contains("'+'") }
        assertTrue("\n" in add.body, "expected a multi-line body, got ${add.body}")
    }

    @Test
    fun `recognises the disambiguation rule form, which has no function`() {
        val read = read(toy)
        val disambiguation = read.rules.single { it.isDisambiguation }
        assertEquals("pattern", disambiguation.category)
        assertEquals("[[ P ]]", disambiguation.body)
    }

    @Test
    fun `treats a funcon equation listed under Funcon as documentation`() {
        val read = read(
            "Language \"X\"\nFuncon\n  f(V:values) : => strings\nRule\n  f(null-value) ~> \"null\"\n",
        )
        assertEquals(0, read.rules.size)
        assertEquals(
            listOf("Rule that is not a rule"),
            read.problems.map { it.kind },
        )
    }

    @Test
    fun `records SDF disambiguation rather than parsing it as a grammar`() {
        val read = read(
            "Language \"X\"\nLexis SDF\n  context-free\nSyntax SDF\n  left-recursive\n",
        )
        assertEquals(0, read.declarations.size)
        assertEquals(listOf("disambiguation", "disambiguation"), read.problems.map { it.kind })
    }

    @Test
    fun `reports a file position when the notation is wrong`() {
        val error = runCatching {
            read("Language \"X\"\nSemantics\n  f[[ P ]] : => integers\nRule\n  g[[ Q ]]\n")
        }.exceptionOrNull()
        assertTrue(error is CBSLangException, "expected a CBSLangException, got $error")
        assertTrue(
            (error as CBSLangException).message.orEmpty().startsWith("expected '=' in a Rule"),
            "unexpected message: ${error.message}",
        )
        assertEquals("test", error.file)
    }

    @Test
    fun `reads a semantic function defined inline rather than by a Rule`() {
        val read = read(
            "Language \"X\"\nSemantics\n  id[[ID:identifier]] : => ids\n    = \\\"ID\\\"\n",
        )
        val id = read.semantics.single()
        assertEquals("id", id.function)
        assertEquals("ids", id.returnType)
        // The body is raw source, so the file's `\"` escaping is still there.
        assertEquals("\\\"ID\\\"", id.definition)
        assertEquals(0, read.rules.size)
        assertEquals(emptyList<Problem>(), read.problems)
    }

    @Test
    fun `records prose that is not part of the language definition`() {
        val read = read("Language \"X\"\nMeta-variables\n  R, S <: values\n")
        assertEquals(emptyList<Declaration>(), read.declarations)
        assertEquals(listOf("prose"), read.problems.map { it.kind })
    }
}
