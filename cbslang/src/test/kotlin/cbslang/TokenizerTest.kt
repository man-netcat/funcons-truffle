package cbslang

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class TokenizerTest {

    private fun lex(source: String): List<Token> =
        Tokenizer(source, "test").tokenize().filter { it.kind != Token.Kind.EOF }

    private fun texts(source: String): List<String> = lex(source).map { it.text }

    @Test
    fun `lexes a quoted terminal and a character range`() {
        val tokens = lex("Z : int ::= ('0'-'9')+")
        assertEquals(
            listOf("Z", ":", "int", "::=", "(", "0", "-", "9", ")", "+"),
            tokens.map { it.text },
        )
        assertEquals(Token.Kind.STRING, tokens[5].kind)
        assertEquals(Token.Kind.PUNCT, tokens[6].kind)
    }

    @Test
    fun `lexes the longest punctuation first`() {
        // `::=` must not lex as `:` then `=`, and `~>` not as `~` then `>`.
        assertEquals(
            listOf("a", "::=", "b", "~>", "c", "|->", "d", "<:", "e", "=", "f"),
            texts("a ::= b ~> c |-> d <: e = f"),
        )
    }

    @Test
    fun `allows hyphens inside an identifier but not at the end`() {
        assertEquals(listOf("aexp-1", "-", ">", "b"), texts("aexp-1 -> b"))
        assertEquals(listOf("n", "*"), texts("n *"))
    }

    @Test
    fun `lexes heading numbers including sections and levels`() {
        assertEquals(listOf("#1", "Programs"), texts("#1 Programs"))
        assertEquals(listOf("#1.1", "Booleans"), texts("#1.1 Booleans"))
        assertEquals(listOf("##", "Classes"), texts("## Classes"))
    }

    @Test
    fun `lexes a language name in bare double quotes`() {
        assertEquals(listOf("Language", "IMP"), texts("""Language "IMP" """))
    }

    @Test
    fun `lexes an escaped funcon string on a rule right-hand side`() {
        // IMP writes these as \"I\" in the rule body.
        val tokens = lex("""  id[[ I ]] = \"I\"""")
        val strings = tokens.filter { it.kind == Token.Kind.DQUOTED }
        assertEquals(listOf("I"), strings.map { it.text })
    }

    @Test
    fun `distinguishes a backslash terminal from an escaped quote`() {
        // The vendored OCaml-Light file writes the backslash terminal as '\' and
        // the quote terminal as '\''. They start the same way, so the only
        // discriminator is whether a further quote follows.
        val backslash = "'" + "\\" + "'"
        val escapedQuote = "'" + "\\'" + "'"
        val strings = lex("(" + escapedQuote + " | " + backslash + ")")
            .filter { it.kind == Token.Kind.STRING }
            .map { it.text }
        assertEquals(listOf("'", "\\"), strings)
    }

    @Test
    fun `takes a trailing backslash as a literal and says so`() {
        val backslash = "'" + "\\" + "'"
        val tokenizer = Tokenizer(backslash, "test")
        val tokens = tokenizer.tokenize()
        assertEquals(
            listOf("\\"),
            tokens.filter { it.kind == Token.Kind.STRING }.map { it.text },
        )
        assertEquals(
            listOf("backslash in terminal"),
            tokenizer.problems.map { it.kind },
        )
    }

    @Test
    fun `keeps a backslash in a terminal that is not a quote escape`() {
        val terminal = "'" + "\\" + "n" + "'"
        assertEquals(
            listOf("\\n"),
            lex("a " + terminal).filter { it.kind == Token.Kind.STRING }.map { it.text },
        )
    }

    @Test
    fun `skips nested block comments and line comments`() {
        val tokens = lex("a /* one /* two */ still */ b // gone\nc")
        assertEquals(listOf("a", "b", "c"), tokens.map { it.text })
    }

    @Test
    fun `records a problem rather than failing on an inconsistent terminal`() {
        val tokenizer = Tokenizer("""capture[[ '\' ]]""", "test")
        tokenizer.tokenize()
        assertEquals(
            listOf("backslash in terminal"),
            tokenizer.problems.map { it.kind },
        )
    }

    @Test
    fun `carries the line of every token so items can be delimited by line`() {
        val tokens = lex("Syntax\n  a ::= 'x'\nRule\n  a[[ 'x' ]] = a")
        assertEquals(listOf(1, 2, 2, 2, 3, 4, 4, 4, 4, 4, 4), tokens.map { it.line })
    }
}
