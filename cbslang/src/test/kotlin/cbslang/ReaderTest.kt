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
    fun languageAndSectionsTest() {
        // One pass over a whole file: every section keyword is recognised and counted.
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
    fun declarationTest() {
        // Alternatives and sequences are different shapes: `aexp '+' aexp` is a
        // sequence inside an alternative, not an alternative of one.
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
    fun charRangeTest() {
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
    fun negatedCharSetTest() {
        // Both spellings occur in the corpus: `~( 'a' | 'b' )` with a group, and
        // `~'a'` bare.
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
    fun semanticSignatureTest() {
        // A signature's argument, its category and its return type are kept apart;
        // the argument is a variable name, the category the nonterminal it ranges over.
        val read = read(toy)
        val run = read.semantics.single { it.function == "run" }
        assertEquals("P", run.patternVariable)
        assertEquals("program", run.category)
        assertEquals("null-type", run.returnType)
    }

    @Test
    fun typeDeclarationTest() {
        // Members are kept as raw tokens, `|` included, because a type declaration
        // lists subtypes rather than a parsed type expression.
        val read = read(toy)
        val ints = read.types.single()
        assertEquals("ints", ints.name)
        assertEquals(listOf("integers", "|", "nats"), ints.members)
    }

    @Test
    fun ruleTest() {
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
    fun multiLineRuleTest() {
        val add = read(toy).rules.single { it.pattern.contains("'+'") }
        assertTrue("\n" in add.body, "expected a multi-line body, got ${add.body}")
    }

    @Test
    fun disambiguationRuleTest() {
        // The disambiguation Rule form has no function name: a pattern, `:` and a
        // category. It is read as a rule so the file keeps one shape, but it
        // defines no semantics.
        val read = read(toy)
        val disambiguation = read.rules.single { it.isDisambiguation }
        assertEquals("pattern", disambiguation.category)
        assertEquals("[[ P ]]", disambiguation.body)
    }

    @Test
    fun funconEquationTest() {
        // A funcon library lists equations under a `Funcon` heading. They document the
        // funcon rather than define this language, so they are not read as rules.
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
    fun sdfCommentTest() {
        // The disambiguation is not part of the CBS language-definition
        // notation: SL's disambiguation file says outright that CBS does not
        // support it. It is kept in the comments, so SdfReader reads it and this
        // reader neither turns it into declarations nor reports it as a problem.
        val read = read("Language \"X\"\nLexis SDF\n  context-free\nSyntax SDF\n  left-recursive\n")
        assertEquals(0, read.declarations.size)
        assertEquals(emptyList<Problem>(), read.problems)

        val sdf = SdfReader("X.cbs")
        val disambiguation = sdf.read("X", "Syntax SDF\n/*\ncontext-free syntax\n``aexp ::= aexp '+' aexp`` {assoc}\n*/\n")
        assertEquals(1, disambiguation.annotated.size)
        assertEquals(Associativity.ASSOC, disambiguation.annotated[0].associativity)
    }

    @Test
    fun problemPositionTest() {
        // A notation error carries its file and line, so a failure in a real language
        // definition can be found in the source.
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
    fun inlineSemanticsTest() {
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
    fun proseTest() {
        // Prose under a bare heading is recorded rather than parsed, so it stays visible
        // without being mistaken for part of the language definition.
        val read = read("Language \"X\"\nMeta-variables\n  R, S <: values\n")
        assertEquals(emptyList<Declaration>(), read.declarations)
        assertEquals(listOf("prose"), read.problems.map { it.kind })
    }
}
