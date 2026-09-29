package cbslang

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class TokenizerTest {

    private fun lex(source: String): List<Token> =
        Tokenizer(source, "test").tokenize().filter { it.kind != Token.Kind.EOF }

    private fun texts(source: String): List<String> = lex(source).map { it.text }

    @Test
    fun terminalAndRangeTest() {
        // A range's bounds are string tokens and the `-` between them is
        // punctuation, so neither bound swallows the dash.
        val tokens = lex("Z : int ::= ('0'-'9')+")
        assertEquals(
            listOf("Z", ":", "int", "::=", "(", "0", "-", "9", ")", "+"),
            tokens.map { it.text },
        )
        assertEquals(Token.Kind.STRING, tokens[5].kind)
        assertEquals(Token.Kind.PUNCT, tokens[6].kind)
    }

    @Test
    fun longestMatchTest() {
        // `::=` must not lex as `:` then `=`, and `~>` not as `~` then `>`.
        assertEquals(
            listOf("a", "::=", "b", "~>", "c", "|->", "d", "<:", "e", "=", "f"),
            texts("a ::= b ~> c |-> d <: e = f"),
        )
    }

    @Test
    fun identifierHyphenTest() {
        // A hyphen is part of a name but may not end one, so `aexp-1` is a single
        // token.
        assertEquals(listOf("aexp-1", "-", ">", "b"), texts("aexp-1 -> b"))
        assertEquals(listOf("n", "*"), texts("n *"))
    }

    @Test
    fun headingNumberTest() {
        // Sections are numbered `#1`, subsections `#1.1`, and levels written `##`.
        assertEquals(listOf("#1", "Programs"), texts("#1 Programs"))
        assertEquals(listOf("#1.1", "Booleans"), texts("#1.1 Booleans"))
        assertEquals(listOf("##", "Classes"), texts("## Classes"))
    }

    @Test
    fun languageNameTest() {
        // The language name is in bare double quotes, which is how files are grouped
        // into one language.
        assertEquals(listOf("Language", "IMP"), texts("""Language "IMP" """))
    }

    @Test
    fun escapedStringTest() {
        // IMP writes these as \"I\" in the rule body.
        val tokens = lex("""  id[[ I ]] = \"I\"""")
        val strings = tokens.filter { it.kind == Token.Kind.DQUOTED }
        assertEquals(listOf("I"), strings.map { it.text })
    }

    @Test
    fun backslashEscapeTest() {
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
    fun trailingBackslashTest() {
        // A trailing backslash would escape the closing quote, so the terminal is
        // taken literally and a problem is recorded.
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
    fun literalBackslashTest() {
        // A backslash that does not begin a quote escape is itself, so the terminal is
        // two characters.
        val terminal = "'" + "\\" + "n" + "'"
        assertEquals(
            listOf("\\n"),
            lex("a " + terminal).filter { it.kind == Token.Kind.STRING }.map { it.text },
        )
    }

    @Test
    fun commentTest() {
        // Block comments nest in CBS, and a `//` comment runs to the end of its line.
        val tokens = lex("a /* one /* two */ still */ b // gone\nc")
        assertEquals(listOf("a", "b", "c"), tokens.map { it.text })
    }

    @Test
    fun inconsistentTerminalTest() {
        // A terminal whose backslash escapes its own closing quote is reported rather
        // than throwing.
        val tokenizer = Tokenizer("""capture[[ '\' ]]""", "test")
        tokenizer.tokenize()
        assertEquals(
            listOf("backslash in terminal"),
            tokenizer.problems.map { it.kind },
        )
    }

    @Test
    fun tokenLineTest() {
        // Every token carries its line, which is what lets a file be split into items by
        // line.
        val tokens = lex("Syntax\n  a ::= 'x'\nRule\n  a[[ 'x' ]] = a")
        assertEquals(listOf(1, 2, 2, 2, 3, 4, 4, 4, 4, 4, 4), tokens.map { it.line })
    }
}
