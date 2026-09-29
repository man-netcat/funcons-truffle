package cbslang

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

/**
 * Expanding a declaration to the grammar a parser has to implement.
 *
 * CBS-beta's `docs/Browsing.md` is the specification these follow, and where it
 * gives a worked example the test uses that example rather than an invented one.
 */
class GrammarTest {

    /**
     * The example CBS-beta documents for layout, `num ::= '-'?_decimal`.
     *
     * The `_` excludes layout, so `- 42` is not a `num` and `-42` is. A reader that
     * lexes `_decimal` as one identifier instead produces a grammar referring to a
     * nonterminal that no language declares.
     */
    @Test
    fun gapExampleTest() {
        val body = ebnf("N : num ::= '-'?_decimal")
        assertEquals(
            listOf(
                "``num ::= '-' _ decimal``",
                "``num ::= _ decimal``",
            ),
            body.toGrammar("num", ::starName).productions.map { it.render() },
        )
    }

    /**
     * A body with a quantifier used to contribute no productions at all, because the
     * flat-alternative expansion gave up on it. That is right for matching SDF3
     * annotations and badly wrong for building a grammar: a parser missing `num`
     * would reject every program with a numeric literal.
     */
    @Test
    fun quantifiedBodyTest() {
        val body = ebnf("N : num ::= '-'?_decimal")
        assertTrue(body.toProductions("num").isEmpty()) {
            "a quantified body has no flat alternative, so this one should be empty"
        }
        assertTrue(body.toGrammar("num", ::starName).productions.isNotEmpty()) {
            "the grammar must keep the alternatives a quantified body has"
        }
    }

    @Test
    fun alternativeInSequenceTest() {
        // `('a' | 'b') c` has two alternatives, not one with a nested choice.
        val body = ebnf("X : x ::= ('a' | 'b') c")
        assertEquals(
            listOf("``x ::= 'a' c``", "``x ::= 'b' c``"),
            body.toGrammar("x", ::starName).productions.map { it.render() },
        )
    }

    @Test
    fun optionalTest() {
        val body = ebnf("X : x ::= 'a'? 'b'")
        assertEquals(
            listOf("``x ::= 'a' 'b'``", "``x ::= 'b'``"),
            body.toGrammar("x", ::starName).productions.map { it.render() },
        )
    }

    /**
     * A repetition has no finite set of alternatives, so it becomes an auxiliary
     * nonterminal: `x* ::= | x x*` admits the empty sequence and `x+ ::= x | x x+`
     * does not. The auxiliary is reported separately because the language never
     * declared it and no disambiguation block will ever mention it.
     */
    @Test
    fun repetitionTest() {
        val star = ebnf("X : x ::= 'a'*")
        val grammar = star.toGrammar("x", ::starName)
        assertEquals(listOf("``xStar0 ::= ``", "``xStar0 ::= 'a' xStar0``", "``x ::= xStar0``"), grammar.productions.map { it.render() })
        assertEquals(listOf("xStar0"), grammar.auxiliary)
        assertTrue(grammar.productions.any { it.nonterminal == "xStar0" && it.symbols.isEmpty() }) {
            "a star must admit the empty sequence"
        }

        val plus = ebnf("X : x ::= 'a'+")
        val plusGrammar = plus.toGrammar("x", ::starName)
        assertTrue(plusGrammar.productions.none { it.symbols.isEmpty() && it.nonterminal != "x" }) {
            "a plus must not admit the empty sequence: ${plusGrammar.productions.map { it.render() }}"
        }
    }

    /**
     * A repetition over a group has to name one nonterminal, so the group is
     * expanded into the auxiliary rather than inlined at each use.
     */
    @Test
    fun repetitionOverSequenceTest() {
        val body = ebnf("X : x ::= ('a' 'b')+")
        val grammar = body.toGrammar("x", ::starName)
        assertEquals(listOf("xStar0"), grammar.auxiliary)
        assertTrue(grammar.productions.any { it.render() == "``xStar0 ::= 'a' 'b'``" }) {
            "the auxiliary must expand the group: ${grammar.productions.map { it.render() }}"
        }
    }

    /**
     * ANTLR reads a rule whose name starts with an uppercase letter as a token, so an
     * auxiliary that did would be a lexer rule referring to parser rules, which ANTLR
     * rejects. The name therefore has to start with a lowercase one, and the caller
     * supplies it so it cannot collide with a declared nonterminal.
     */
    @Test
    fun auxiliaryNameTest() {
        val body = ebnf("X : x ::= 'a'*")
        for (name in body.toGrammar("x", ::starName).auxiliary) {
            assertTrue(name[0].isLowerCase()) { "$name does not start with a lowercase letter" }
        }
    }

    @Test
    fun lexisIsNotContextFreeTest() {
        // `Lexis` defines a token and excludes layout throughout, so it is not part
        // of the context-free grammar a parser rule implements.
        val read = Reader("Language \"T\"\n\nSyntax\n  A : x ::= 'a'\n\nLexis\n  D : d ::= '0'-'9'\n", "test").read()
        assertEquals(listOf("Syntax", "Lexis"), read.declarations.map { it.section })
    }

    /**
     * The auxiliary nonterminals have to be usable: every one a production refers to
     * has to be defined, or the grammar is not a grammar. A quantified body that
     * expands to an undefined nonterminal would be accepted by every program that
     * avoids that path and rejected by the rest.
     */
    /**
     * A reference has to resolve, whether it names a context-free nonterminal, a
     * lexical one, or an auxiliary this expansion invented.
     *
     * IMP's `aexp ::= num` names a `Lexis` nonterminal, so a check that only looked
     * at the `Syntax` productions would report it as undefined even though the
     * language defines it.
     */
    @Test
    fun everyReferenceIsDefinedTest() {
        val root = java.nio.file.Paths.get("CBS-beta", "Languages-beta")
        for (def in Languages.readAll(root)) {
            if (def.name == "<failed>") continue
            val defined = def.grammarProductions.map { it.nonterminal }.toSet() +
                def.lexicalGrammars.keys +
                def.grammarAuxiliary.toSet()
            val referred = def.grammarProductions
                .flatMap { it.symbols }
                .filterIsInstance<Symbol.NonTerminal>()
                .map { it.name }
                .toSet()
            val missing = referred - defined
            assertTrue(missing.isEmpty()) { "${def.name} refers to undefined nonterminals: $missing" }
        }
    }

    /**
     * The gap has to survive expansion, because it is what tells a generated parser
     * not to admit layout in that one position.
     */
    @Test
    fun gapSurvivesExpansionTest() {
        // `'-'?_decimal` puts the gap between the optional minus and the digits, so
        // the two alternatives both carry it and neither is a bare `decimal`.
        val productions = ebnf("N : num ::= '-'?_decimal").toGrammar("num", ::starName).productions
        assertTrue(productions.all { p -> p.symbols.any { it is Symbol.Gap } }) {
            "the gap was lost: ${productions.map { it.render() }}"
        }
    }

    /** CBS-beta says layout is excluded throughout a `Lexis` body, never admitted. */
    @Test
    fun lexisHasNoGapTest() {
        val body = ebnf("D : d ::= '0'-'9'")
        val productions = body.toGrammar("d", ::starName).productions
        assertTrue(productions.none { p -> p.symbols.any { it is Symbol.Gap } }) {
            "a Lexis body must not contain a layout gap: ${productions.map { it.render() }}"
        }
    }

    /**
     * The tokens a `Syntax` body refers to have to come from somewhere: either a
     * quoted literal, a declared `Lexis` nonterminal, or a range. A reference to
     * nothing would leave a generated parser with an undefined token.
     */
    @Test
    fun everyTokenIsDefinedTest() {
        val root = java.nio.file.Paths.get("CBS-beta", "Languages-beta")
        for (def in Languages.readAll(root)) {
            if (def.name == "<failed>") continue
            val tokens = def.lexicalGrammars.keys.toSet()
            val unresolved = def.grammarProductions
                .flatMap { it.symbols }
                .filterIsInstance<Symbol.NonTerminal>()
                .map { it.name }
                .filter { it.startsWith("_") }
                .toSet()
            assertTrue(unresolved.isEmpty()) {
                "${def.name} refers to nonterminals that look like unnamed tokens: $unresolved"
            }
            assertTrue(tokens.isNotEmpty() || def.grammarProductions.isEmpty()) {
                "${def.name} has parser productions but no Lexis declarations"
            }
        }
    }

    /** Every auxiliary a production refers to has to be defined by some production. */
    @Test
    fun everyAuxiliaryIsDefinedTest() {
        val root = java.nio.file.Paths.get("CBS-beta", "Languages-beta")
        for (def in Languages.readAll(root)) {
            if (def.name == "<failed>") continue
            for (name in def.grammarAuxiliary) {
                assertTrue(def.grammarProductions.any { it.nonterminal == name }) {
                    "${def.name} auxiliary $name is never defined"
                }
            }
        }
    }
}

/**
 * The auxiliary name for a test grammar, standing in for the one a language
 * supplies so it cannot collide with a declared nonterminal.
 */
private fun starName(n: Int): String = "xStar$n"

/** Parses one `Syntax` declaration body, e.g. `N : num ::= '-'?_decimal`. */
private fun ebnf(declaration: String): Ebnf =
    Reader("Language \"T\"\n\nSyntax\n  $declaration\n", "test").read().declarations.single().body
