package cbslang

/**
 * Reads the SDF3 disambiguation blocks that the CBS files keep in comments.
 *
 * The blocks are already in the corpus, in the syntax of the SDF3 meta-language:
 * a `Syntax SDF` heading, then a block comment, then `context-free syntax` and
 * one annotated production per line, as in
 *
 * ```
 * ``aexp ::= aexp '+' aexp``  {assoc}
 * ``bexp ::= bexp '&&' bexp`` {assoc}
 * ``stmt ::= stmt stmt``      {right}
 * ```
 *
 * Reading them rather than transcribing them by hand is what keeps a data file
 * from drifting away from what the language definition says. Only the subset the
 * corpus uses is read; anything outside it is reported rather than guessed at,
 * because a disambiguation annotation that is silently misread would yield a
 * parser that accepts the wrong programs.
 *
 * The same reader takes the normalised data file that [Disambiguation.render]
 * produces, so a data file is by construction something this reader wrote.
 */
class SdfReader(private val file: String) {

    val problems = mutableListOf<Problem>()

    fun read(language: String, source: String): Disambiguation {
        val text = sdfBodies(source)
        val c = Cursor()
        val rejections = mutableListOf<Rejection>()
        val restrictions = mutableListOf<Restriction>()
        val annotated = mutableListOf<AnnotatedProduction>()
        val priorities = PriorityReader()

        // A section's body is the lines after its heading, up to the next
        // heading, so the heading selects a handler and the handler keeps the
        // state that spans lines.
        var section = ""
        while (true) {
            val line = readLine(text, c) ?: break
            if (line.section.isNotEmpty()) {
                section = line.section
                if (section == "context free priorities") priorities.start()
                continue
            }
            when (section) {
                "lexical syntax" -> rejection(line, rejections)
                "lexical restrictions" -> restriction(line, restrictions)
                "context free syntax" -> annotatedProduction(line, annotated)
                "context free priorities" -> priorities.line(line)
                // A heading introducing a part of the disambiguation with
                // nothing this reader needs, such as the SDF3 grammar of the
                // layout nonterminals in OCaml-Light.
                "", "lexis sdf", "syntax sdf", "lexical", "context free", "syntax" -> Unit
                else -> note("unrecognised disambiguation section", section)
            }
        }
        priorities.finish()
        return Disambiguation(language, rejections, restrictions, annotated, priorities.steps)
    }

    /**
     * The contents of the block comments that follow a `Lexis SDF` or
     * `Syntax SDF` heading, which is where the disambiguation is kept.
     *
     * The comments are the input, not something to be stripped: the CBS
     * notation has no disambiguation construct, so the SDF3 text lives in
     * comments and is the only place it is stated. Every other comment in the
     * file, including the one holding the layout grammar in OCaml-Light, is
     * dropped, so an unrelated comment cannot be read as a disambiguation.
     */
    private fun sdfBodies(source: String): String {
        val sb = StringBuilder()
        var i = 0
        var lineStart = 0
        // Whether the current line is the heading that introduces a block.
        var afterSdfHeading = false
        var inComment = false
        while (i <= source.length) {
            if (i == source.length || source[i] == '\n') {
                // A heading only counts outside a comment: the section headings
                // inside a block are the disambiguation's own, and they must not
                // end the block.
                if (inComment) {
                    if (afterSdfHeading) sb.append('\n')
                } else {
                    val line = source.substring(lineStart, i).trim()
                    val heading = normaliseHeading(line)
                    when {
                        heading != null -> {
                            afterSdfHeading = heading == "lexis sdf" || heading == "syntax sdf"
                            if (afterSdfHeading) sb.append(heading).append('\n')
                        }
                        // A blank line between the heading and its block does not
                        // end it, so the state is left alone.
                        line.isNotEmpty() -> afterSdfHeading = false
                    }
                }
                lineStart = i + 1
                i++
                continue
            }
            if (source.startsWith("/*", i)) {
                inComment = true
                // Skipping an unrelated comment keeps its line breaks, so a
                // position reported against this file stays meaningful.
                if (!afterSdfHeading) {
                    val end = source.indexOf("*/", i + 2)
                    val stop = if (end < 0) source.length else end + 2
                    while (i < stop) {
                        if (source[i] == '\n') sb.append('\n')
                        i++
                    }
                    inComment = false
                    continue
                }
                // The opening marker stands in for the line break that the line
                // above already consumed.
                i += 2
                continue
            }
            if (source.startsWith("*/", i)) {
                inComment = false
                if (afterSdfHeading) afterSdfHeading = false
                i += 2
                continue
            }
            if (afterSdfHeading) {
                sb.append(source[i])
                i++
                continue
            }
            i++
        }
        return sb.toString()
    }

    // --- productions ---------------------------------------------------

    /**
     * A production, as one backtick-quoted span: ```` ``aexp ::= aexp '+' aexp`` ````.
     *
     * Because the whole production is quoted, the `::=` is inside the quotes and
     * the nonterminal is the part before it. The corpus also writes grouping
     * meta-productions such as `(expr comma-expr*)`, which are not productions;
     * those are reported and skipped.
     */
    private fun productionAt(src: String, from: Int): Pair<Production?, Int> {
        var i = from
        while (i < src.length && src[i].isWhitespace()) i++
        if (i >= src.length) return null to i
        if (src[i] == '(') {
            val end = matchingParen(src, i)
            note("group meta-production", src.substring(i, end))
            return null to end
        }
        if (src[i] != '`') return null to i
        val (text, after) = quotedName(src, i)
        if (text == null) return null to src.length
        i = after

        // A grouping meta-production is quoted in the corpus, so it is only
        // recognisable once the quotes are off. It states a group of productions
        // rather than one production, so there is nothing to annotate.
        if (text.trimStart().startsWith("(")) {
            note("group meta-production", text.trim().take(60))
            return null to i
        }

        val eq = text.indexOf("::=")
        if (eq < 0) {
            note("expected ::= in a production", text.take(60))
            return null to i
        }
        val nonterminal = text.substring(0, eq).trim()
        if (nonterminal.isEmpty()) {
            note("expected a nonterminal on the left of ::=", text.take(60))
            return null to i
        }

        val symbols = symbolsOf(text.substring(eq + 3).trim())
        if (symbols == null) return null to i
        return finish(nonterminal, symbols) to i
    }

    /**
     * The symbols of a production's right-hand side, or null if it does not
     * parse into a flat sequence.
     *
     * A bare name is a nonterminal reference, which is how the corpus writes
     * them inside a quoted production. A run of two or more backticks also
     * quotes one. A quantifier makes the result unusable, since a disambiguation
     * block can only annotate a flat production.
     */
    private fun symbolsOf(rhs: String): List<Symbol>? {
        val symbols = mutableListOf<Symbol>()
        var i = 0
        while (i < rhs.length) {
            val c = rhs[i]
            when {
                c == ' ' || c == '\t' -> i++

                c == '`' -> {
                    val (name, end) = quotedName(rhs, i)
                    if (name == null) return null
                    symbols += Symbol.NonTerminal(name)
                    i = end
                }

                // SDF3 writes terminals in double quotes and CBS in single
                // quotes; a disambiguation block has to be comparable with the
                // EBNF, so both are read.
                c == '"' || c == '\'' -> {
                    val (lit, end) = quoted(rhs, i, c)
                    symbols += Symbol.Lit(lit)
                    i = end
                }

                c == '~' || c == '[' -> {
                    val (clazz, end) = charClass(rhs, i)
                    if (clazz == null) return null
                    symbols += clazz
                    i = end
                }

                c == '_' && !isNameChar(rhs.getOrNull(i + 1)) -> {
                    symbols += Symbol.Gap
                    i++
                }

                c == '+' || c == '*' || c == '?' -> {
                    // A quantifier binds whatever symbol or group precedes it, so it
                    // is kept rather than dropping the production. Dropping it would
                    // lose a priority group member, and the disambiguation is the only
                    // place the corpus says which rule binds tighter than which.
                    if (symbols.isEmpty()) return null
                    val last = symbols.removeAt(symbols.lastIndex)
                    symbols += when (last) {
                        is Symbol.Quantified -> Symbol.Quantified(last.symbols, c)
                        else -> Symbol.Quantified(listOf(last), c)
                    }
                    i++
                }

                // A parenthesised group is flattened into its parent, exactly as
                // the EBNF reader flattens it, so the two sides of a comparison
                // agree on what a production is.
                c == '(' -> {
                    var depth = 0
                    var j = i
                    while (j < rhs.length) {
                        when (rhs[j]) {
                            '(' -> depth++
                            ')' -> {
                                depth--
                                if (depth == 0) break
                            }
                        }
                        j++
                    }
                    if (depth != 0) return null
                    val inner = symbolsOf(rhs.substring(i + 1, j)) ?: return null
                    val quantified = rhs.getOrNull(j + 1)
                    if (quantified in setOf('+', '*', '?')) {
                        // A group can be quantified as well as a single symbol, so the
                        // quantifier wraps the group rather than the symbol in front of
                        // it, which would be the closing bracket.
                        symbols += Symbol.Quantified(inner, quantified!!)
                    } else {
                        symbols += inner
                    }
                    i = j + 1
                }

                c.isLetter() -> {
                    var j = i
                    while (j < rhs.length && isNameChar(rhs[j])) j++
                    symbols += Symbol.NonTerminal(rhs.substring(i, j))
                    i = j
                }

                // A brace or a step marker ends the right-hand side.
                else -> return if (symbols.isEmpty()) null else symbols
            }
        }
        return if (symbols.isEmpty()) null else symbols
    }

    private fun isNameChar(c: Char?): Boolean = c != null && (c.isLetterOrDigit() || c == '_' || c == '-')

    private fun finish(nonterminal: String, symbols: List<Symbol>): Production? =
        if (symbols.isEmpty()) null else Production(nonterminal, symbols)

    /**
     * A backtick-quoted span, and the index after it.
     *
     * A run of two or more backticks opens a quote that the same-length run
     * closes, which is how SDF3 quotes a production or a nonterminal while still
     * allowing backticks to be nested. A run of one is a stray character.
     */
    private fun quotedName(src: String, from: Int): Pair<String?, Int> {
        var run = 0
        while (from + run < src.length && src[from + run] == '`') run++
        if (run < 2) return null to from
        val end = src.indexOf("`".repeat(run), from + run)
        if (end < 0) return null to src.length
        return src.substring(from + run, end) to end + run
    }

    private fun matchingParen(src: String, from: Int): Int {
        var depth = 0
        var i = from
        while (i < src.length) {
            when (src[i]) {
                '(' -> depth++
                ')' -> {
                    depth--
                    if (depth == 0) return i + 1
                }
            }
            i++
        }
        return src.length
    }

    private fun endOfLine(src: String, from: Int): Int {
        val nl = src.indexOf('\n', from)
        return if (nl < 0) src.length else nl
    }

    private fun unescape(c: Char): String = when (c) {
        'n' -> "\n"
        't' -> "\t"
        'r' -> "\r"
        '0' -> " "
        else -> c.toString()
    }

    /**
     * A quoted terminal. The quote character is given rather than assumed
     * because a single-quoted literal may contain an escaped double quote, as
     * OCaml-Light's `'\\'''` does.
     */
    private fun quoted(src: String, from: Int, quote: Char): Pair<String, Int> {
        var i = from + 1
        val sb = StringBuilder()
        while (i < src.length) {
            val c = src[i]
            if (c == '\\' && i + 1 < src.length) {
                sb.append(unescape(src[i + 1]))
                i += 2
                continue
            }
            if (c == quote) {
                i++
                break
            }
            sb.append(c)
            i++
        }
        return sb.toString() to i
    }

    /**
     * A character class: `[A-Za-z0-9\_]` or `~[\(\)\*]`. Members are single
     * escaped characters and `a-z` ranges. A negated class stays negated so that
     * it cannot be mistaken for a positive set.
     */
    private fun charClass(src: String, from: Int): Pair<Symbol.CharClass?, Int> {
        var i = from
        val negated = src[i] == '~'
        if (negated) i++
        if (i >= src.length || src[i] != '[') {
            note("expected [ in a character class", src.substring(from, minOf(src.length, from + 24)))
            return null to endOfLine(src, i)
        }
        i++
        val members = mutableListOf<Symbol.Member>()
        while (i < src.length && src[i] != ']') {
            val first = classChar(src, i) ?: break
            i += if (first.second) 2 else 1
            if (i < src.length && src[i] == '-' && i + 1 < src.length && src[i + 1] != ']') {
                i++
                val last = classChar(src, i) ?: break
                i += if (last.second) 2 else 1
                members += Symbol.Member(first.first, last.first)
            } else {
                members += Symbol.Member(first.first, null)
            }
        }
        if (i < src.length && src[i] == ']') i++
        return Symbol.CharClass(negated, members) to i
    }

    /** One character of a class, and whether it was written with a backslash. */
    private fun classChar(src: String, at: Int): Pair<String, Boolean>? {
        if (at >= src.length) return null
        if (src[at] == '\\' && at + 1 < src.length) return unescape(src[at + 1]) to true
        return src[at].toString() to false
    }

    // --- sections ------------------------------------------------------

    /**
     * `lexical syntax` holds both keyword rejections and, in OCaml-Light, the
     * SDF3 definitions of the layout nonterminals. Only a line that rejects is a
     * rejection; the definitions are already expressed by the `Lexis`
     * declarations, so they are skipped rather than reported line by line.
     */
    private fun rejection(line: Line, out: MutableList<Rejection>) {        val body = line.body
        if (body.isEmpty()) return
        // `{reject}` says the line is a rejection. Without it the line is an SDF3
        // definition, which the `Lexis` declarations already express.
        if (!line.rejects) return
        val eq = body.indexOf('=')
        if (eq < 0) {
            note("expected 'target = keyword'", body)
            return
        }
        // The target is the quoted name at the start of the line.
        val (target, afterTarget) = quotedName(body, 0)
        if (target == null || body.substring(afterTarget, eq).isNotBlank()) {
            note("expected 'target = keyword'", body)
            return
        }
        // The right-hand side starts after the `=`, not after the target.
        val rhs = body.substring(eq + 1).trim()
        out += when {
            rhs.length >= 2 && rhs.startsWith('`') && rhs.endsWith('`') -> {
                val (keyword, _) = quotedName(rhs, 0)
                if (keyword == null) {
                    note("expected a quoted keyword nonterminal", body)
                    return
                }
                Rejection.OfNonTerminal(target, keyword)
            }

            rhs.length >= 2 && rhs.startsWith('"') && rhs.endsWith('"') ->
                Rejection.OfLiteral(target, rhs.substring(1, rhs.length - 1))

            else -> {
                note("expected a keyword or a keyword nonterminal", body)
                return
            }
        }
    }

    private fun restriction(line: Line, out: MutableList<Restriction>) {
        val body = line.body
        if (body.isEmpty()) return
        val mark = body.indexOf("-/-")
        if (mark < 0) {
            // A `lexical restrictions` block in OCaml-Light also restates the
            // lexis: bare nonterminals and the keyword list, spread over several
            // lines. Those are already expressed by the `Lexis` declarations, so
            // they are skipped rather than reported line by line.
            return
        }
        val target = body.substring(0, mark).trim().trim('`')
        // `-/-` is followed by a space, so the class starts at the next
        // non-space character, not three characters along.
        var start = mark + 3
        while (start < body.length && body[start].isWhitespace()) start++
        if (start < 0 || (body[start] != '[' && body[start] != '~')) {
            note("expected a character class", body)
            return
        }
        val (clazz, _) = charClass(body, start)
        if (clazz == null) {
            note("expected a character class", body)
            return
        }
        out += Restriction(target, clazz.members)
    }

    private fun annotatedProduction(line: Line, out: MutableList<AnnotatedProduction>) {
        if (line.body.isEmpty()) return
        val (production, _) = productionAt(line.body, 0)
        if (production == null) return
        out += AnnotatedProduction(production, line.associativity, line.flags)
    }

    /**
     * A `context-free-priorities` block, which spans lines and so cannot be read
     * a line at a time like the other sections.
     *
     * Steps are separated by `>` or `<0> >`, may be a group `{ ... }`, and carry
     * an associativity label. OCaml-Light writes a group's closing brace and the
     * following separator on one line (`} <0> >`), so braces are stripped from a
     * line before it is tested for being a separator.
     */
    private inner class PriorityReader {
        val steps = mutableListOf<PriorityStep>()
        private var prods: MutableList<Production>? = null
        private var assoc: Associativity? = null
        private var group = false
        private var constructs = false

        /** A new priorities block; anything pending belongs to the previous one. */
        fun start() = flush()

        fun finish() = flush()

        fun line(line: Line) {
            // A step's label is written as a trailing annotation, which the line
            // reader has already separated from the production.
            if (line.associativity != null) assoc = line.associativity
            // The body has the annotation removed, so what is left is either a
            // production or the structure around one.
            var rest = line.body.trim()
            if (rest.isEmpty()) return

            if (rest.startsWith("`")) {
                addProduction(rest)
                return
            }

            // Structure runs left to right, so one line may hold several parts of
            // it: OCaml-Light writes `} <0> >` and `} > {` on a single line.
            while (rest.isNotEmpty()) {
                val c = rest[0]
                when {
                    c == '{' -> {
                        group = true
                        rest = rest.substring(1).trimStart()
                    }

                    c == '}' -> rest = rest.substring(1).trimStart()

                    c == '>' -> {
                        flush()
                        rest = rest.substring(1).trimStart()
                    }

                    c == '<' && rest.startsWith("<0") -> {
                        // `<0> >` separates steps, and marks the next one as
                        // constructing rather than destructing. The `>` is consumed
                        // here rather than by the branch below: a second flush would
                        // clear the flag that was just set, before the step that
                        // carries it has read any productions.
                        flush()
                        constructs = true
                        rest = rest.drop(3).trimStart()
                        if (rest.startsWith(">")) rest = rest.substring(1).trimStart()
                    }

                    c == '.' -> {
                        // `. >` and `<0>. >` mean the same as `>` and `<0> >`.
                        val after = rest.dropWhile { it == '.' || it.isWhitespace() }
                        if (after.startsWith(">")) {
                            flush()
                            rest = after.substring(1).trimStart()
                        } else if (after.startsWith("<0")) {
                            flush()
                            constructs = true
                            rest = after.drop(3).trimStart()
                            if (rest.startsWith(">")) rest = rest.substring(1).trimStart()
                        } else {
                            rest = after
                        }
                    }

                    else -> {
                        // A label written bare, as in `non-assoc:`.
                        val label = labelOf(rest) ?: bareLabelOf(rest)
                        if (label == null) {
                            note("unparsed priority step", rest.take(60))
                            return
                        }
                        assoc = Associativity.of(label)
                        rest = rest.removePrefix(label).trimStart().trimStart(':', ',').trimStart()
                    }
                }
            }
        }

        private fun addProduction(text: String) {
            val before = problems.size
            val (p, _) = productionAt(text, 0)
            if (p == null) {
                // A production that was rejected for a specific, already-reported
                // reason should not also be reported as unparsed.
                if (problems.size == before) note("unparsed priority step", text.take(60))
                return
            }
            if (prods == null) prods = mutableListOf()
            prods?.add(p)
        }


        private fun flush() {
            val current = prods
            if (current != null && current.isNotEmpty()) {
                steps += PriorityStep(current.toList(), assoc, group, constructs)
            }
            prods = null
            assoc = null
            // A group or a label belongs to the step it was written with, so it
            // does not carry over to the next one.
            group = false
            constructs = false
        }
    }

    /** A leading `{left` or `{non-assoc:` label on a step. */
    private fun labelOf(body: String): String? {
        if (!body.startsWith("{")) return null
        return labelAfter(body.removePrefix("{").trim())
    }

    /** A label written bare, as OCaml-Light writes `non-assoc:`. */
    private fun bareLabelOf(body: String): String? {
        val colon = body.indexOf(':')
        if (colon < 0) return null
        return labelAfter(body.substring(0, colon).trim())
    }

    private fun labelAfter(after: String): String? {
        for (candidate in Associativity.entries) {
            if (after.startsWith(candidate.sdf)) {
                val rest = after.removePrefix(candidate.sdf)
                if (rest.isEmpty() || rest.startsWith(",") || rest.startsWith(":") || rest.startsWith(" ")) {
                    return candidate.sdf
                }
            }
        }
        return null
    }

    // --- plumbing ------------------------------------------------------

    private class Cursor(var pos: Int = 0)

    /**
     * One line of a disambiguation block.
     *
     * [raw] is the line as written and is what the group and label logic looks
     * at, since both are written as braces around or before a production.
     * [body] is the line with any trailing annotation removed and the outer
     * backticks kept, which is what a production is read from. [rejects] is the
     * `{reject}` of a `lexical syntax` line, which is a different thing from an
     * annotation: it says the line is a rejection rather than a definition.
     */
    private class Line(
        val section: String,
        val body: String,
        val raw: String,
        val associativity: Associativity?,
        val flags: Set<Flag>,
        val rejects: Boolean,
    )

    private fun readLine(src: String, c: Cursor): Line? {
        while (c.pos < src.length && src[c.pos].isWhitespace()) c.pos++
        if (c.pos >= src.length) return null
        val end = src.indexOf('\n', c.pos).let { if (it < 0) src.length else it }
        val raw = src.substring(c.pos, end).trim()
        c.pos = if (end < src.length) end + 1 else end
        if (raw.isEmpty() || raw.startsWith("//")) return readLine(src, c)

        // A section heading is a bare heading word or words, with no symbols in
        // it. Anything else is a continuation of the current section.
        if (raw.none { it == '`' || it == '\'' || it == '"' || it == '~' || it == '[' || it == '}' || it == '>' }) {
            val normalised = normaliseHeading(raw)
            if (normalised != null) return Line(normalised, "", raw, null, emptySet(), false)
        }

        val span = annotationSpan(raw)
        if (span == null) return Line("", raw, raw, null, emptySet(), false)
        val (brace, close) = span
        val names = raw.substring(brace + 1, close).split(",").map { it.trim() }.filter { it.isNotEmpty() }
        return Line(
            section = "",
            // The production keeps its own backticks: they are the SDF3 quoting
            // of the whole production, not part of a symbol.
            body = (raw.substring(0, brace) + raw.substring(close + 1)).trim(),
            raw = raw,
            associativity = names.firstNotNullOfOrNull { Associativity.of(it) },
            flags = names.mapNotNull { n -> Flag.entries.firstOrNull { it.sdf == n } }.toSet(),
            rejects = names.any { it == "reject" },
        )
    }

    /**
     * The span of the trailing `{…}` annotation on a line, or null if it has none.
     *
     * The braces have to be found outside quotes, because a production may quote
     * a brace as a terminal — SL and OCaml-Light both write
     * ``expr ::= '{' field '=' expr '}'`` — and a brace inside such a terminal is
     * part of the production, not the start of an annotation.
     */
    private fun annotationSpan(raw: String): Pair<Int, Int>? {
        var i = 0
        while (i < raw.length) {
            when (raw[i]) {
                '\'', '"' -> i = quoted(raw, i, raw[i]).second
                '`' -> i = quotedName(raw, i).second
                '{' -> {
                    val close = raw.indexOf('}', i)
                    return if (close < 0) null else i to close
                }

                else -> i++
            }
        }
        return null
    }

    /** The section heading a bare line names, or null if it is not one. */
    private fun normaliseHeading(raw: String): String? {
        val words = raw.lowercase()
            .replace('_', ' ')
            .replace('-', ' ')
            .split(Regex("\\s+"))
            .filter { it.isNotEmpty() }
        if (words.size > 3) return null
        val joined = words.joinToString(" ")
        return when (joined) {
            // The `Lexis SDF` and `Syntax SDF` headings introduce the block; the
            // sections inside it are named without the `sdf`.
            "lexis sdf", "lexical sdf" -> "lexis sdf"
            "syntax sdf" -> "syntax sdf"
            "lexical syntax" -> "lexical syntax"
            "lexical restriction", "lexical restrictions" -> "lexical restrictions"
            "context free syntax" -> "context free syntax"
            "context free priorities" -> "context free priorities"
            "context free restriction", "context free restrictions" -> "context free"
            "lexical", "lexis" -> "lexical"
            "syntax" -> "syntax"
            "context free" -> "context free"
            else -> null
        }
    }

    private fun note(kind: String, detail: String) {
        problems += Problem(file, 0, kind, detail.take(200))
    }
}
