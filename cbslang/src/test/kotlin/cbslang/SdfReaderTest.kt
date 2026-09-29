package cbslang

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class SdfReaderTest {

    /**
     * Reads a disambiguation block in the form the corpus uses: introduced by a
     * `Syntax SDF` heading and kept in a block comment, because the CBS notation
     * has no disambiguation construct of its own.
     */
    private fun read(body: String) = SdfReader("test.cbs").read("Test", "Syntax SDF\n/*\n$body\n*/\n")

    @Test
    fun rejectionNonTerminalTest() {
        // The corpus states a keyword rejection both ways: by lexis nonterminal,
        // and by literal.
        val d = read(
            """
            Syntax SDF
            lexical syntax
              ``id`` = ``keyword`` {reject}
            """.trimIndent(),
        )
        assertEquals(listOf(Rejection.OfNonTerminal("id", "keyword")), d.rejections)
    }

    @Test
    fun rejectionLiteralTest() {
        // The literal spelling, the other half of the pair above.
        val d = read(
            """
            Syntax SDF
            lexical syntax
              ``id`` = "function" {reject}
            """.trimIndent(),
        )
        assertEquals(listOf(Rejection.OfLiteral("id", "function")), d.rejections)
    }

    @Test
    fun lexicalRuleSkipTest() {
        // A `Lexis SDF` block also holds the SDF3 lexical definitions. They are not
        // rejections, so they are skipped rather than read as one.
        val d = read(
            """
            Syntax SDF
            lexical syntax
              LAYOUT = LEX-block-comment
              LEX-block-comment = "(*" LEX-comment-part* "*)"
              ``id`` = ``keyword`` {reject}
            """.trimIndent(),
        )
        assertEquals(listOf(Rejection.OfNonTerminal("id", "keyword")), d.rejections)
    }

    @Test
    fun lexicalRestrictionTest() {
        // A restriction removes characters from a lexis nonterminal, which is how the
        // corpus stops a keyword lexing as an identifier.
        val d = read(
            """
            Syntax SDF
            lexical restrictions
              ``capitalized-ident`` -/- [A-Za-z0-9\_]
            """.trimIndent(),
        )
        assertEquals(
            listOf(Restriction("capitalized-ident", listOf(Symbol.Member("A", "Z"), Symbol.Member("a", "z"), Symbol.Member("0", "9"), Symbol.Member("_", null)))),
            d.restrictions,
        )
    }

    @Test
    fun bareNonTerminalTest() {
        // A bare nonterminal under `lexical restrictions` restricts nothing, so it
        // is skipped.
        // OCaml-Light lists the three identifier nonterminals as bare
        // nonterminals and puts the restriction on the last one.
        val d = read(
            """
            Syntax SDF
            lexical restrictions
              ``ident``
              ``lowercase-ident``
              ``capitalized-ident`` -/- [A-Za-z0-9\_]
            """.trimIndent(),
        )
        assertEquals(1, d.restrictions.size)
        assertEquals("capitalized-ident", d.restrictions[0].target)
    }

    @Test
    fun annotatedProductionTest() {
        // An annotated production carries an associativity and any flags; both are
        // optional.
        val d = read(
            """
            Syntax SDF
            context-free syntax
              ``aexp ::= aexp '+' aexp``  {assoc}
              ``bexp ::= bexp '&&' bexp`` {assoc}
              ``stmt ::= stmt stmt``      {right}
            """.trimIndent(),
        )
        assertEquals(3, d.annotated.size)
        assertEquals(Associativity.ASSOC, d.annotated[0].associativity)
        assertEquals(Associativity.ASSOC, d.annotated[1].associativity)
        assertEquals(Associativity.RIGHT, d.annotated[2].associativity)
        assertEquals(Production("aexp", listOf(Symbol.NonTerminal("aexp"), Symbol.Lit("+"), Symbol.NonTerminal("aexp"))), d.annotated[0].production)
    }

    @Test
    fun productionFlagTest() {
        // Flags belong to the production they are written on, so two productions in one
        // block may disagree.
        val d = read(
            """
            Syntax SDF
            context-free syntax
              ``exp ::= exp '|' exp`` {left, prefer}
              ``exp ::= exp '&' exp`` {left, avoid}
            """.trimIndent(),
        )
        assertEquals(Associativity.LEFT, d.annotated[0].associativity)
        assertEquals(setOf(Flag.PREFER), d.annotated[0].flags)
        assertEquals(setOf(Flag.AVOID), d.annotated[1].flags)
    }

    @Test
    fun priorityGroupTest() {
        // A step is a production, a `{...}` group of them, or a `<0>` marker that resets
        // the group.
        val d = read(
            """
            Syntax SDF
            context-free-priorities
              ``aexp ::= aexp '+' aexp``  {left}
              >
              {
              ``aexp ::= aexp '*' aexp``
              ``aexp ::= aexp '^' aexp``
              } {left}
              >
              ``aexp ::= aexp '+' aexp '->' aexp`` {right}
            """.trimIndent(),
        )
        assertEquals(3, d.priorities.size)
        assertEquals(Associativity.LEFT, d.priorities[0].associativity)
        assertEquals(1, d.priorities[0].productions.size)
        assertTrue(d.priorities[1].group)
        assertEquals(2, d.priorities[1].productions.size)
        assertEquals(Associativity.RIGHT, d.priorities[2].associativity)
    }

    @Test
    fun nestedPriorityGroupTest() {
        // OCaml-Light opens a group after a separator and closes it on the same
        // line as the following one: `> {` and `} >`.
        val d = read(
            """
            context-free priorities
            ``expr ::= expr '.' field``
            > {
            ``expr ::= expr argument``
            ``expr ::= 'assert' expr``
            } >
            ``expr ::= '-' expr``
            > 
            ``expr ::= expr infix-op-1 expr``
            """.trimIndent(),
        )
        // The group is a step of its own, so there are four steps here.
        assertEquals(4, d.priorities.size, "steps: ${d.priorities.map { it.productions.size }}")
        assertEquals(1, d.priorities[0].productions.size)
        assertTrue(d.priorities[1].group)
        assertEquals(2, d.priorities[1].productions.size)
        assertEquals(1, d.priorities[2].productions.size)
        assertEquals(1, d.priorities[3].productions.size)
    }

    @Test
    fun repeatedPriorityTest() {
        // OCaml-Light states priorities more than once under the same heading,
        // and each block starts a fresh set of steps.
        val d = read(
            """
            context-free priorities
            ``typexpr ::= typexpr typeconstr``
            >
            ``typexpr ::= typeconstr``

            context-free priorities
            ``star-typexpr ::= '*' typexpr``
            >
            ``typexpr ::= typexpr 'as' typexpr``
            """.trimIndent(),
        )
        assertEquals(4, d.priorities.size, "steps: ${d.priorities.size}")
    }

    @Test
    fun groupMetaProductionTest() {
        // A grouping meta-production states a group of productions rather than one
        // production, so there is nothing to annotate. It is reported rather than
        // misread as a production.
        val reader = SdfReader("test.cbs")
        val d = reader.read(
            "Test",
            """
            Syntax SDF
            /*
            context-free priorities
            {
            ``comma-pattern ::= ',' pattern``
            ``(pattern comma-pattern*)``
            } >
            ``pattern ::= pattern 'as' pattern``
            */
            """.trimIndent(),
        )
        assertEquals(2, d.priorities.size, "steps: ${d.priorities.map { it.productions.size }}")
        assertTrue(
            reader.problems.any { it.kind == "group meta-production" },
            "problems: ${reader.problems}",
        )
    }

    @Test
    fun quantifiedProductionTest() {
        // ``expr ::= expr argument+`` is a production of the language: the `+` is
        // implemented by an auxiliary nonterminal when the grammar is expanded, but
        // the disambiguation still has to name it. Dropping it would lose the
        // annotation, and in a `context-free priorities` group it would lose a whole
        // level of the ordering.
        val reader = SdfReader("test.cbs")
        val d = reader.read(
            "Test",
            """
            Syntax SDF
            /*
            context-free syntax
            ``expr ::= expr argument+`` {non-assoc,avoid}
            */
            """.trimIndent(),
        )
        assertEquals(1, d.annotated.size)
        assertEquals(
            "expr ::= expr argument+",
            d.annotated.single().production.render().removeSurrounding("``"),
        )
        assertEquals(
            Associativity.NON_ASSOC,
            d.annotated.single().associativity,
        )
        assertEquals(setOf(Flag.AVOID), d.annotated.single().flags)
        assertTrue(
            reader.problems.none { it.kind == "quantified symbol in a production" },
            "problems: ${reader.problems}",
        )
    }

    @Test
    fun flagSpacingTest() {
        // `{left,prefer}`, with no space after the comma.
        val d = read(
            """
            context-free syntax
            ``expr ::= expr infix-op-3 expr``    {left,prefer}
            """.trimIndent(),
        )
        assertEquals(Associativity.LEFT, d.annotated.single().associativity)
        assertEquals(setOf(Flag.PREFER), d.annotated.single().flags)
    }

    @Test
    fun negatedCharClassTest() {
        // A negated class in a production has to stay negated, or the excluded
        // characters are accepted.
        val d = read(
            """
            Syntax SDF
            context-free syntax
              ``name ::= ~[\(\)\*]``
            """.trimIndent(),
        )
        val symbols = d.annotated.single().production.symbols
        assertEquals(1, symbols.size)
        val clazz = symbols[0] as Symbol.CharClass
        assertTrue(clazz.negated)
        assertEquals(listOf(Symbol.Member("(", null), Symbol.Member(")", null), Symbol.Member("*", null)), clazz.members)
    }

    @Test
    fun braceTerminalTest() {
        // SL and OCaml-Light both write record productions with '{' and '}' as
        // quoted terminals. The annotation is the *trailing* brace group, so the
        // braces inside the quotes have to be stepped over.
        val reader = SdfReader("test.cbs")
        val d = reader.read(
            "Test",
            "Syntax SDF\n/*\ncontext-free syntax\n  ``expr ::= '{' field '=' expr '}'``  {right}\n*/\n",
        )
        assertEquals(emptyList<Problem>(), reader.problems)
        val annotated = d.annotated.single()
        assertEquals(Associativity.RIGHT, annotated.associativity)
        assertEquals(
            listOf(
                Symbol.Lit("{"),
                Symbol.NonTerminal("field"),
                Symbol.Lit("="),
                Symbol.NonTerminal("expr"),
                Symbol.Lit("}"),
            ),
            annotated.production.symbols,
        )
    }

    @Test
    fun renderRoundTripTest() {
        // The generated data file is read back by this same reader. Whatever render
        // writes, read has already handled, so the two cannot drift apart.
        val original = read(
            """
            Syntax SDF
            lexical syntax
              ``id`` = ``keyword`` {reject}
            lexical restrictions
              ``capitalized-ident`` -/- [A-Za-z0-9\_]
            context-free syntax
              ``aexp ::= aexp '+' aexp``  {assoc}
              ``bexp ::= bexp '&&' bexp`` {assoc}
            context-free-priorities
              ``aexp ::= aexp '+' aexp``  {left}
              >
              {
              ``aexp ::= aexp '*' aexp``
              } {left}
            """.trimIndent(),
        )
        // The data file is read back by the same reader, with no special case.
        val roundTripped = SdfReader("data").read("Test", original.render())
        assertEquals(original, roundTripped)
    }
}
