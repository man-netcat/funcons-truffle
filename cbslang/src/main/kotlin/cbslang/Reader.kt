package cbslang

/** Everything one `.cbs` language-definition file contributes. */
data class FileRead(
    val file: String,
    val language: String?,
    val declarations: List<Declaration> = emptyList(),
    val types: List<TypeDeclaration> = emptyList(),
    val semantics: List<SemanticSignature> = emptyList(),
    val rules: List<Rule> = emptyList(),
    val headings: List<Heading> = emptyList(),
    val index: List<IndexItem> = emptyList(),
    val imports: List<String> = emptyList(),
    val problems: List<Problem> = emptyList(),
)

/**
 * Reads one CBS language-definition file.
 *
 * The format has no terminators: a `Syntax` declaration runs until the next
 * declaration or the next item keyword, and a `Rule` body runs until the next
 * item keyword. Items are therefore delimited by a keyword appearing as the
 * first token of a line, which is how the files are formatted. The reader takes
 * that convention as given and records anything it cannot place as a [Problem]
 * rather than throwing, so a file using unfamiliar notation still yields
 * everything else.
 */
class Reader(private val source: String, private val file: String) {

    private val tokenizer = Tokenizer(source, file)
    private val tokens = tokenizer.tokenize()
    private val lineStarts: Set<Int> =
        tokens.indices.filter { it == 0 || tokens[it].line != tokens[it - 1].line }.toSet()
    private var pos = 0
    private val problems = tokenizer.problems.toMutableList()

    fun read(): FileRead {
        var language: String? = null
        val declarations = mutableListOf<Declaration>()
        val types = mutableListOf<TypeDeclaration>()
        val semantics = mutableListOf<SemanticSignature>()
        val rules = mutableListOf<Rule>()
        val headings = mutableListOf<Heading>()
        val index = mutableListOf<IndexItem>()
        val imports = mutableListOf<String>()

        while (!atEnd()) {
            val t = peek()
            when {
                isItemStart() && t.text == "Language" -> {
                    next()
                    language = expectText("a language name").text
                }

                isItemStart() && t.text == "Syntax" -> readDeclarations(declarations, "Syntax")
                isItemStart() && t.text == "Lexis" -> readDeclarations(declarations, "Lexis")

                isItemStart() && t.text == "Type" -> types += readType()

                isItemStart() && t.text == "Semantics" -> semantics += readSemantics()

                isItemStart() && (t.text == "Rule" || t.text == "Otherwise") && atRule() ->
                    rules += readRule()

                // A `Rule` or `Otherwise` that is not followed by a pattern is
                // one of the funcon equations listed under a `Funcon` heading,
                // e.g. `sl-to-string(null-value) ~> "null"`. These are
                // documentation of the funcon library, not translation rules.
                isItemStart() && (t.text == "Rule" || t.text == "Otherwise") -> {
                    next()
                    note("${t.text} that is not a rule", readFreeText(t.text))
                }

                // `Funcon` and `Assert` introduce prose blocks that are not part
                // of the language definition proper.
                isItemStart() && (t.text == "Funcon" || t.text == "Assert") -> {
                    next()
                    readFreeText(t.text)
                }

                isItemStart() && t.text == "Imports" -> {
                    next()
                    imports += readFreeText("Imports")
                        .split(Regex("\\s+"))
                        .filter { it.isNotEmpty() }
                }

                t.kind == Token.Kind.PUNCT && t.text == "[" -> index += readIndex()

                t.kind == Token.Kind.PUNCT && t.text.startsWith("#") -> headings += readHeading()

                else -> {
                    // Prose rather than notation: the vendored files document
                    // the funcon library in the same comment syntax, under
                    // bare-word headings such as `Auxiliary Funcon`.
                    val text = readFreeText("prose")
                    if (text.isNotBlank()) {
                        note("prose", text)
                    }
                }
            }
        }

        return FileRead(
            file, language, declarations, types, semantics, rules, headings, index, imports, problems,
        )
    }

    // --- Syntax and Lexis ------------------------------------------------

    private fun readDeclarations(into: MutableList<Declaration>, keyword: String) {
        next() // the keyword
        if (peek().kind == Token.Kind.IDENT && peek().text == "SDF") {
            // The commented-out SDF priority declarations. In the vendored
            // languages these sit inside a block comment, so reaching one here
            // means a file declares disambiguation the CBS language has no
            // construct for.
            note("disambiguation", "$keyword SDF ...")
            readFreeText("disambiguation")
            return
        }
        while (!atEnd() && !isItemStart()) {
            val decl = readDeclaration(keyword) ?: break
            into += decl
        }
    }

    private fun readDeclaration(keyword: String): Declaration? {
        val start = peek()
        if (start.kind != Token.Kind.IDENT) return null
        // Guard against having walked into the middle of a body: a declaration
        // is `IDENT ::=` or `IDENT : IDENT ::=`, and neither sequence occurs
        // inside a body, where a reference is always followed by something else.
        if (!declarationStartsHere()) return null

        var category: String? = null
        var name = next().text
        if (check(":")) {
            next()
            category = name
            name = expect(Token.Kind.IDENT, "a nonterminal name").text
        }
        expect("::=", "'::=' after $name")
        val body = readEbnf()
        return Declaration(category, name, body, file, start.line)
    }

    private fun declarationStartsHere(): Boolean {
        val a = peek()
        val b = peek(1)
        val c = peek(2)
        val d = peek(3)
        if (b.kind == Token.Kind.PUNCT && b.text == "::=") return true
        return b.kind == Token.Kind.PUNCT && b.text == ":" &&
            c.kind == Token.Kind.IDENT &&
            d.kind == Token.Kind.PUNCT && d.text == "::="
    }

    private fun readEbnf(): Ebnf {
        val options = mutableListOf<Ebnf>()
        while (true) {
            options += readEbnfSequence()
            if (check("|")) {
                next()
            } else {
                break
            }
        }
        return if (options.size == 1) options[0] else Ebnf.Alternatives(options)
    }

    private fun readEbnfSequence(): Ebnf {
        val parts = mutableListOf<Ebnf>()
        // `declarationStartsHere` matters: bodies are not bracketed, so without
        // it the next declaration's `IDENT :` would be read as a stray colon
        // part-way through the current one.
        while (!atEnd() && !check("|") && !check(")") && !check("::=") && !isItemStart() &&
            !declarationStartsHere()
        ) {
            parts += readEbnfPostfix() ?: break
        }
        return if (parts.size == 1) parts[0] else Ebnf.Sequence(parts)
    }

    /** A primary, with any quantifier applied to it. */
    private fun readEbnfPostfix(): Ebnf? {
        val primary = readEbnfPrimary() ?: return null
        return when {
            check("?") -> {
                next()
                Ebnf.Optional(primary)
            }

            check("*") -> {
                next()
                Ebnf.Repeated(primary, Ebnf.Quantifier.ZERO_OR_MORE)
            }

            check("+") -> {
                next()
                Ebnf.Repeated(primary, Ebnf.Quantifier.ONE_OR_MORE)
            }

            else -> primary
        }
    }

    private fun readEbnfPrimary(): Ebnf? {
        val t = peek()
        return when {
            t.kind == Token.Kind.STRING -> {
                next()
                // `'a'-'z'` is a range rather than two adjacent terminals.
                if (check("-") && peek(1).kind == Token.Kind.STRING) {
                    next()
                    val to = next()
                    Ebnf.CharRange(t.text, to.text)
                } else {
                    Ebnf.Terminal(t.text)
                }
            }

            t.kind == Token.Kind.IDENT -> {
                next()
                Ebnf.Reference(t.text)
            }

            check("_") -> {
                next()
                Ebnf.Gap
            }

            check("(") -> {
                next()
                val inner = readEbnf()
                expect(")", "')'")
                inner
            }

            check("~") -> {
                next()
                val excluded = mutableListOf<CharRangeAndTerminal>()
                if (check("(")) {
                    next()
                    while (!atEnd() && !check(")")) {
                        excluded += readCharSetItem()
                        if (check("|")) next()
                    }
                    expect(")", "')' closing '~'")
                } else {
                    // `~'"'` occurs as well as `~ ( '"' )`.
                    excluded += readCharSetItem()
                }
                Ebnf.Negated(excluded)
            }

            else -> {
                note("unexpected token in grammar", t.toString())
                next()
                null
            }
        }
    }

    // --- Type, Semantics, Rule -------------------------------------------

    /** One member of a character set: `'a'`, or the range `'a'-'z'`. */
    private fun readCharSetItem(): CharRangeAndTerminal {
        val s = next()
        if (s.kind != Token.Kind.STRING) {
            note("character set member", s.text)
            return CharRangeAndTerminal(s.text, null)
        }
        if (check("-") && peek(1).kind == Token.Kind.STRING) {
            next()
            return CharRangeAndTerminal(s.text, next().text)
        }
        return CharRangeAndTerminal(s.text, null)
    }

    private fun readType(): TypeDeclaration {
        val start = next() // Type
        val name = expect(Token.Kind.IDENT, "a type name").text
        if (!check("~>")) {
            // The whole declaration sits in a `//` comment, as in
            // `Type implemented-floats // ~> floats(implemented-floats-format)`.
            note("Type without ~>", name)
            readFreeText("Type")
            return TypeDeclaration(name, emptyList(), file, start.line)
        }
        next()
        val members = mutableListOf<String>()
        while (!atEnd() && !isItemStart()) {
            members += next().text
        }
        return TypeDeclaration(name, members, file, start.line)
    }

    private fun readSemantics(): SemanticSignature {
        val start = next() // Semantics
        val function = expect(Token.Kind.IDENT, "a semantic function name").text
        val (patternVariable, category) = readSignatureArgument()
        var returnType: String? = null
        if (check(":")) {
            next()
            if (check("=>")) next()
            returnType = readReturnType()
        }
        // A definition on a following line, rather than in a separate `Rule`.
        var definition: String? = null
        if (check("=")) {
            next()
            definition = readFreeText("semantics definition")
        }
        return SemanticSignature(function, patternVariable, category, returnType, file, start.line, definition)
    }

    private fun readRule(): Rule {
        val start = next() // Rule
        var function: String? = null
        if (peek().kind == Token.Kind.IDENT) function = next().text
        val (pattern, patternTokens) = readBracketed()
        var category: String? = null
        var returnType: String? = null
        if (check(":")) {
            next()
            if (check("=>")) {
                next()
                returnType = readReturnType()
            } else {
                category = expect(Token.Kind.IDENT, "a category name").text
                if (check("=>")) {
                    next()
                    returnType = readReturnType()
                }
            }
        }
        expect("=", "'=' in a Rule")
        val body = readFreeText("rule body")
        return Rule(function, pattern, category, returnType, body, file, start.line, patternTokens)
    }

    /** The `P:program` or `_:stmt` inside a semantic function's `[[ ]]`. */
    private fun readSignatureArgument(): Pair<String?, String?> {
        if (!check("[[")) return null to null
        val (text, _) = readBracketed()
        val colon = indexOfTopLevelColon(text)
        if (colon < 0) return text to null
        return text.substring(0, colon).trim() to text.substring(colon + 1).trim()
    }

    private fun indexOfTopLevelColon(text: String): Int {
        var depth = 0
        var inString = false
        text.forEachIndexed { i, c ->
            when {
                c == '\'' -> inString = !inString
                inString -> Unit
                c == '(' || c == '[' -> depth++
                c == ')' || c == ']' -> depth--
                c == ':' && depth == 0 && i > 0 -> return i
            }
        }
        return -1
    }

    /**
     * The funcon type after `=>`, or after a bare `:` as in
     * `Semantics capture[[...]] : implemented-characters`. It sits on the same
     * line as the `=>`; the line bound matters, because `Semantics` is the only
     * thing that delimits one signature from the next and an unbounded scan
     * would swallow the rest of the block.
     */
    private fun readReturnType(): String {
        val on = peek().line
        val sb = StringBuilder()
        while (!atEnd() && !isItemStart() && peek().line == on) {
            val t = next()
            if (sb.isNotEmpty()) sb.append(' ')
            sb.append(t.text)
        }
        return sb.toString().trim()
    }

    /** A `[[ ... ]]` block: its text, and its tokens as strings. */
    private fun readBracketed(): Pair<String, List<String>> {
        expect("[[", "'[['")
        val collected = mutableListOf<String>()
        var from = -1
        var lastEnd = -1
        while (!atEnd() && !check("]]")) {
            val t = next()
            if (from < 0) from = t.start
            lastEnd = t.end
            collected += if (t.kind == Token.Kind.STRING) "'${t.text}'" else t.text
        }
        expect("]]", "']]'")
        // Bounded by the last token inside the brackets, so that the closing
        // `]]` is not part of the pattern.
        val text = if (from < 0 || lastEnd < 0) "" else source.substring(from, lastEnd).trim()
        return text to collected
    }

    private fun readIndex(): List<IndexItem> {
        expect("[", "'['")
        val items = mutableListOf<IndexItem>()
        while (!atEnd() && !check("]")) {
            val t = next()
            val number = if (t.kind == Token.Kind.PUNCT && t.text.startsWith("#")) t.text.trimStart('#') else t.text
            val title = StringBuilder()
            while (!atEnd() && !check("]") && !atHeadingNumber()) {
                title.append(next().text).append(' ')
            }
            items += IndexItem(number, title.toString().trim())
        }
        expect("]", "']'")
        return items
    }

    private fun atHeadingNumber(): Boolean {
        val t = peek()
        return t.kind == Token.Kind.PUNCT && t.text.startsWith("#")
    }

    private fun readHeading(): Heading {
        val hash = next()
        val level = if (hash.text.startsWith("##")) 2 else 1
        val number = hash.text.trimStart('#')
        val title = StringBuilder()
        while (!atEnd() && tokens[pos].line == hash.line) {
            title.append(next().text).append(' ')
        }
        val text = listOf(number, title.toString().trim()).filter { it.isNotEmpty() }.joinToString(" ")
        return Heading(level, text, hash.line)
    }

    /**
     * Source text up to the next item keyword that starts a line. Used for rule
     * bodies, `Imports` text and anything unplaced.
     */
    private fun readFreeText(what: String): String {
        val from = peek().start
        val to = mutableListOf(peek().end)
        while (!atEnd() && !isItemStart()) {
            to += next().end
        }
        if (from >= source.length) return ""
        val end = to.last().coerceAtMost(source.length)
        return source.substring(from, end).trim()
    }

    // --- plumbing --------------------------------------------------------

    /** True when the current token opens a new top-level item. */
    private fun isItemStart(): Boolean {
        if (pos !in lineStarts) return false
        val t = peek()
        if (t.kind == Token.Kind.PUNCT && (t.text.startsWith("#") || t.text == "[")) return true
        return t.kind == Token.Kind.IDENT && t.text in KEYWORDS
    }

    private fun note(kind: String, text: String) {
        val line = if (pos < tokens.size) tokens[pos].line else (tokens.lastOrNull()?.line ?: 0)
        problems += Problem(file, line, kind, text.take(200))
    }

    private fun atEnd(): Boolean = peek().kind == Token.Kind.EOF

    private fun peek(offset: Int = 0): Token = tokens[(pos + offset).coerceAtMost(tokens.size - 1)]

    private fun next(): Token = peek().also { if (!atEnd()) pos++ }

    private fun check(text: String): Boolean {
        val t = peek()
        return t.kind == Token.Kind.PUNCT && t.text == text
    }

    private fun expect(kind: Token.Kind, what: String): Token {
        val t = peek()
        if (t.kind != kind) throw CBSLangException("expected $what, found ${t.text}", file, t.line, t.column)
        return next()
    }

    /** A quoted string of either flavour: `'x'` for a terminal, `"x"` or `\"x\"` otherwise. */
    private fun expectText(what: String): Token {
        val t = peek()
        if (t.kind != Token.Kind.STRING && t.kind != Token.Kind.DQUOTED) {
            throw CBSLangException("expected $what, found ${t.text}", file, t.line, t.column)
        }
        return next()
    }

    private fun expect(text: String, what: String): Token {
        val t = peek()
        if (t.kind != Token.Kind.PUNCT || t.text != text) {
            throw CBSLangException("expected $what, found ${t.text}", file, t.line, t.column)
        }
        return next()
    }

    private companion object {
        val KEYWORDS = setOf(
            "Language", "Syntax", "Lexis", "Type", "Funcon", "Semantics",
            "Rule", "Otherwise", "Assert", "Imports",
        )
    }

    /**
     * True when the `Rule` or `Otherwise` at the current position is a real rule
     * rather than a funcon equation: a real one is `f[[ pattern ]]` or a bare
     * `[[ pattern ]]`. Called with the cursor still on the keyword, so the
     * pattern is one or two tokens further on.
     */
    private fun atRule(): Boolean {
        if (peek(1).kind == Token.Kind.PUNCT && peek(1).text == "[[") return true
        return peek(1).kind == Token.Kind.IDENT && peek(2).kind == Token.Kind.PUNCT && peek(2).text == "[["
    }
}
