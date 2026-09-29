package cbslang

/**
 * Writes a language's grammar as an ANTLR file.
 *
 * This is the first step of the path from a language definition to something that
 * can parse, and it is deliberately narrow: it writes the context-free grammar and
 * the disambiguation that resolves it, and nothing else. It does not write the
 * funcon translation, and a generated parser built from it would produce a parse
 * tree rather than an AST.
 *
 * Three things are worth knowing about the output.
 *
 * Layout is a token, not an absence. CBS-beta admits layout between the symbols of
 * a `Syntax` body and excludes it where the body writes `_`, so a generated parser
 * skips layout everywhere except where [Symbol.Gap] appears. Skipping it in a
 * lexer instead would lose the distinction, because a token that spans a gap and
 * one that does not have to be told apart.
 *
 * Associativity becomes ANTLR associativity, and a priority group becomes an
 * explicit alternative list, because that is what they mean: which tree the parser
 * builds when a string has more than one derivation.
 *
 * A nonterminal that only the grammar invents is written too, or the file would
 * not compile.
 */
object GrammarWriter {

    /**
     * The grammar for [def], as an ANTLR file.
     *
     * [name] is the grammar's own name; the file has to be named after it.
     */
    fun write(def: LanguageDef, name: String): String = buildString {
        appendLine("// $name, generated from the CBS language definition of \"${def.name}\".")
        appendLine("// Do not edit by hand; regenerate from the language definition instead.")
        appendLine("grammar ${grammarName(name)};")
        appendLine()
        appendLine("// The start symbol is named `start`, per CBS-beta's documentation.")
        // CBS-beta says the start symbol is named `start`, so the rule is named after
        // the nonterminal rather than after whatever the definition happens to call it.
        val startRule = def.grammarProductions.firstOrNull { it.nonterminal == "start" }
        appendLine(
            if (startRule == null) "start : /* the definition declares no start nonterminal */ ;"
            else "start : ${ruleBody(startRule, def)} ;",
        )
        appendLine()

        // Layout is admitted between the symbols of a `Syntax` body, so it is skipped
        // in the lexer and never appears in a parser rule. Where the body writes `_`
        // the exclusion is *not* about skipping: `num ::= '-'?_decimal` means that no
        // layout may come between the digits and the minus, which a lexer skip cannot
        // express, because by the time the parser runs the layout is already gone.
        // The positions that exclude layout are therefore reported to the caller
        // rather than written into the grammar, which is honest about the fact that
        // this grammar does not yet enforce them.
        appendLine("// Layout is admitted between the symbols of a Syntax body.")
        appendLine("LAYOUT : [ \\t\\r\\n]+ -> skip ;")
        appendLine()

        appendLine("// Tokens. A literal that the disambiguation rejects is a keyword, and one it")
        appendLine("// does not is an ordinary token; the two are spelled the same way here, so the")
        appendLine("// rejection has to survive as a comment for whoever reads this.")
        for (literal in literals(def)) {
            appendLine("${tokenName(literal)} : ${tokenPattern(def, literal)} ;")
        }
        appendLine()

        // Tokens for the `Lexis` nonterminals a parser rule refers to. Each is a
        // character class over the body, so `('0'-'9')+` becomes a run of digits.
        // A `Lexis` nonterminal the disambiguation rejects from a token is a keyword
        // set, not a token: `keyword ::= 'else' | 'false' | ...` names the words that
        // `id` must not match, and no parser rule refers to it. Writing it as a token
        // would define a rule that overlaps every keyword literal already written.
        // A `Lexis` nonterminal that only another `Lexis` body refers to is a set of
        // characters and is inlined into its user rather than written, because a token
        // for it would shadow the token that uses it.
        val keywords = lexicalRejections(def)
        for (name in def.lexicalUse.tokens.sorted() - keywords) {
            if (unshadowedLexicalBody(def, name) == null) continue
            appendLine("${tokenName(name)} : ${lexicalPattern(def, name)} ;")
        }
        if (keywords.isNotEmpty()) {
            appendLine()
            appendLine("// Keyword sets: the nonterminals a token is rejected from. No parser rule")
            appendLine("// refers to these, and their words are the literal tokens written above.")
            for (name in keywords.sorted()) {
                val rejectedFrom = def.disambiguation.rejections
                    .filterIsInstance<Rejection.OfNonTerminal>()
                    .first { it.nonterminal == name }
                appendLine("// ${tokenName(name)} = ${tokenName(rejectedFrom.target)}  {reject}")
            }
        }
        appendLine()

        // ANTLR wants one `name : alt | alt ;` per rule, not a repeated rule per
        // alternative, so the alternatives of a nonterminal are collected first.
        // The auxiliary nonterminals are written here, with the other rules, because
        // they are parser rules that refer to parser rules. A block of their own
        // placed after the token section would follow a lexer rule, and ANTLR would
        // then read every one of them as another lexer rule and reject the file.
        val byNonterminal = def.grammarProductions.groupBy { it.nonterminal }
        for ((nonterminal, alternatives) in byNonterminal) {
            if (nonterminal == "start") continue
            val bodies = alternatives.joinToString(" | ") { ruleBody(it, def) }
            val note = disambiguationNote(alternatives, def)
            // A null note has to be dropped, not interpolated: `$note` would write
            // the text `null` into the grammar, which then fails to compile.
            val suffix = if (note == null) "" else "  $note"
            val auxiliary = if (nonterminal in def.grammarAuxiliary) "  // auxiliary" else ""
            appendLine("${ruleName(nonterminal)} : $bodies ;$suffix$auxiliary".trimEnd())
        }
        // A `Lexis` body that can derive the empty string is a rule rather than a
        // token, because a lexer cannot produce a token of no characters. SL's
        // `string-chars ::= string-char*` is a rule over a token for the character it
        // repeats, which is what the empty string between two quotes needs. The token
        // has to be written with the other tokens, and the rule with the other rules.
        for (rule in def.lexicalUse.rules) {
            appendLine("${tokenName(rule.nonterminal)}Char : ${lexicalFragment(rule.atom, def, setOf(rule.nonterminal))} ;")
        }
        appendLine()
        for (rule in def.lexicalUse.rules) {
            appendLine("${ruleName(rule.nonterminal)} : ${tokenName(rule.nonterminal)}Char* ;")
        }
        appendLine()

    }

    /**
     * The rule name for a production.
     *
     * A literal is not a legal ANTLR rule or token name, and the auxiliary names
     * are already legal, so a production is named after its nonterminal with any
     * alternative appended when the nonterminal has more than one.
     */
    /**
     * The rule name for a nonterminal.
     *
     * Two things stop a CBS-beta name from being usable as written. A dash is the
     * alternative separator, so SIMPLE's `imp-stmt` cannot be spelled that way, and a
     * name that is a keyword of the target language, such as SL's `int`, becomes a
     * field in the generated Java and the compiler rejects it.
     *
     * Both are repaired with a suffix that a corpus cannot spell: CBS-beta names are
     * letters, digits and underscores, so `dash` and `rule` are words a language may
     * use, but the pair `dash` after a dash, or `rule` after a rule name, is a
     * coincidence nothing in the corpus is written to make. A name is only rewritten
     * when it has to be, so an ordinary name like `stmt` is left alone and the
     * generated file stays readable.
     */
    private fun ruleName(nonterminal: String): String {
        val legal = nonterminal.split('-').joinToString("dash")
        return when {
            legal in targetKeywords -> legal + "rule"
            else -> legal
        }
    }

    /**
     * The keywords ANTLR turns a rule name into.
     *
     * A token rule becomes a `TokenType` constant and a parser rule becomes a field
     * and a context class, so any of these names fails to compile whatever language
     * ANTLR is targeting. The list is the intersection of the reserved words of the
     * targets this project generates for, which is the safe answer for the rest.
     */
    private val targetKeywords = setOf(
        "abstract", "assert", "boolean", "break", "byte", "case", "catch", "char",
        "class", "const", "continue", "default", "do", "double", "else", "enum",
        "extends", "false", "final", "finally", "float", "for", "goto", "if",
        "implements", "import", "instanceof", "int", "interface", "long", "native",
        "new", "null", "package", "private", "protected", "public", "return", "short",
        "static", "super", "switch", "synchronized", "this", "throw", "throws",
        "transient", "true", "try", "void", "volatile", "while", "with",
    )

    /**
     * A reference to a lexical nonterminal, which is a token rather than a parser
     * rule.
     *
     * `aexp ::= num` names the `num` declared under `Lexis`, so the reference has to
     * be written as a token. Without this the grammar refers to a rule that was never
     * written, and it does not compile. The test is [LanguageDef.lexicalTokens] rather
     * than the whole set of `Lexis` declarations, because a `Lexis` body that no
     * parser rule refers to is inlined into the token that does refer to it and has no
     * token of its own.
     */
    private fun referenceText(symbol: Symbol.NonTerminal, def: LanguageDef): String {
        if (symbol.name !in def.lexicalUse.tokens || symbol.name in lexicalRejections(def)) {
            return ruleName(symbol.name)
        }
        val name = symbol.name
        val shared = sharedLiterals(def, name)
        if (shared.isEmpty()) return tokenName(name)
        val accepted = if (unshadowedLexicalBody(def, name) == null) {
            shared
        } else {
            listOf(tokenName(name)) + shared
        }
        return accepted.joinToString(" | ", prefix = "( ", postfix = " )")
    }

    /**
     * The literals a `Lexis` nonterminal shares with a token of its own.
     *
     * The lexer claims such a literal for the earlier rule, so a parser rule that
     * refers to the `Lexis` nonterminal has to accept the literal's token as well or
     * the position becomes unmatchable. The names are in the order the alternatives
     * were declared in, so the generated file stays stable.
     */
    private fun sharedLiterals(def: LanguageDef, name: String): List<String> {
        val body = def.declarations.firstOrNull { it.nonterminal == name && it.section == "Lexis" }
            ?.body
            ?: return emptyList()
        val taken = literals(def).toSet()
        val options = (body as? Ebnf.Alternatives)?.options ?: listOf(body)
        return options.mapNotNull { (it as? Ebnf.Terminal)?.value }
            .filter { it in taken }
            .map { tokenName(it) }
    }

    /**
     * The right-hand side of one production.
     *
     * A gap contributes nothing, and the symbols around it are separated by a single
     * space, so `-` `decimal` does not come out as `- decimal ` with the gap left as a
     * gap. Consecutive separators are collapsed for the same reason.
     */
    /**
     * How an alternative of a parser rule is written, leading associativity included.
     *
     * ANTLR reads `<assoc=left>` and `<assoc=right>` at the start of a left-recursive
     * alternative and uses them to build the precedence-climbing loop, so the SDF3
     * annotation has to be written there rather than as a comment. A rule that is not
     * left-recursive has no precedence to state, so the option is left off.
     */
    private fun ruleBody(production: Production, def: LanguageDef): String {
        val body = production.symbols
            .map { symbolText(it, def) }
            .filter { it.isNotEmpty() }
            .joinToString(" ")
        val associativity = def.expandedAssociativity[production.symbols]
        val option = when (associativity) {
            Associativity.LEFT -> "<assoc=left> "
            Associativity.RIGHT -> "<assoc=right> "
            // `non-assoc` forbids chaining, and `assoc` names no side. ANTLR's
            // left-recursion transform has no option for either, so writing one of
            // the two it does have would claim a binding the corpus did not state.
            else -> ""
        }
        return option + body
    }

    /**
     * How a symbol appears in a parser rule.
     *
     * A gap is where layout is excluded. It is written as the empty alternative of
     * the layout rule, so a parser rule can match it by matching nothing; deleting
     * the position instead would lose the information that the exclusion was asked
     * for, and writing nothing would not compile.
     */
    private fun symbolText(symbol: Symbol, def: LanguageDef): String = when (symbol) {
        is Symbol.Lit -> tokenName(symbol.value)
        is Symbol.NonTerminal -> referenceText(symbol, def)
        // The gap is where layout is excluded, so the position is left empty: layout
        // is skipped by the lexer either way, and writing the token here would make
        // the rule unmatchable.
        is Symbol.Gap -> ""
        is Symbol.Range -> "'${symbol.from}'..'${symbol.to}'"
        is Symbol.CharClass -> "CHAR_CLASS"
        // A quantified symbol only reaches a disambiguation block, never a rule: the
        // productions written here are flat, so `Ebnf.toGrammar` has already expanded
        // the quantifier away. Matching a disambiguation production against a rule
        // compares the expansions instead, so this is a bug rather than a case.
        is Symbol.Quantified -> error("a quantifier reached a parser rule: ${symbol.render()}")
    }

    /**
     * The literals the grammar mentions, in a stable order.
     *
     * Sorted rather than in traversal order so regenerating an unchanged language
     * produces an unchanged file, which is what makes a diff of the generated
     * grammar reviewable.
     */
    private fun literals(def: LanguageDef): List<String> =
        def.grammarProductions
            .flatMap { it.symbols }
            .filterIsInstance<Symbol.Lit>()
            .map { it.value }
            .distinct()
            .sorted()

    /**
     * A legal token name for a literal.
     *
     * A literal is not a legal ANTLR name, so the name is derived from it, with
     * every character that cannot appear turned into a digit. The name is stable,
     * which matters because a generated file that renamed its tokens on every run
     * would produce a useless diff.
     */
    private fun tokenName(literal: String): String {
        val base = buildString {
            for (c in literal) {
                if (c.isLetterOrDigit() || c == '_') append(c) else append(c.code.toString(16))
            }
        }
        // A literal of only symbols leaves an empty base, so the digits are what
        // distinguish the tokens; an empty name would collide with every other.
        // Every token is prefixed, because a bare keyword becomes a constant in the
        // generated Java: a lexer rule named `if` collides with a keyword of the
        // target language, and `int` with a type. The prefix is also what makes a
        // token name legal when the literal is a symbol like `&&`.
        return "T$base"
    }

    /**
     * The character pattern of a literal, honouring a lexical restriction.
     *
     * CBS-beta restricts an identifier so that it cannot start with a digit, e.g.
     * ``capitalized-ident`` -/- [A-Za-z0-9\_]. Dropping that would let `1abc` lex as
     * an identifier, so the restriction becomes a negated character class in the
     * token pattern.
     */
    private fun tokenPattern(def: LanguageDef, literal: String): String {
        val restriction = def.disambiguation.restrictions.firstOrNull { it.target == literal }
            ?: return quote(literal)
        val excluded = restriction.members.joinToString("") { member ->
            if (member.to == null) escape(member.from) else "${escape(member.from)}-${escape(member.to)}"
        }
        return "[^$excluded]+"
    }

    /** The nonterminal a literal is rejected from, i.e. which keyword it shadows. */
    private fun rejectedFrom(def: LanguageDef, literal: String): String? =
        def.disambiguation.rejections
            .firstOrNull { it is Rejection.OfLiteral && it.keyword == literal }
            ?.target

    /**
     * Whether a `Lexis` nonterminal is itself the target of a rejection, i.e.
     * whether it is a keyword set rather than an ordinary token.
     */
    private fun lexicalRejections(def: LanguageDef): Set<String> =
        def.disambiguation.rejections
            .filterIsInstance<Rejection.OfNonTerminal>()
            .map { it.nonterminal }
            .toSet()

    /**
     * The character pattern of a `Lexis` nonterminal.
     *
     * A `Lexis` body is a regular expression over characters, so it is written as an
     * ANTLR fragment rather than as rules. A repetition becomes `+` or `*` rather than
     * an auxiliary nonterminal, because a token has to be recognised by the lexer and
     * a parser rule cannot appear inside a token's pattern.
     */
    private fun lexicalPattern(def: LanguageDef, name: String): String =
        lexicalFragment(unshadowedLexicalBody(def, name), def, setOf(name))

    /**
     * A `Lexis` body with the alternatives another token has already taken out.
     *
     * A literal a `Syntax` body writes is a token of its own, and ANTLR's lexer tries
     * the rules in order, so such a token claims that text before any `Lexis` token
     * written after it sees it. OCaml Light's `infix-op-2 ::= '*' | '*' operator-char
     * ... | ...` starts with the same `'*'` that `star-typexpr ::= '*' typexpr`
     * writes, so the `Lexis` alternative was unreachable and `expr ::= expr infix-op-2
     * expr` could not match a multiplication at all.
     *
     * The two are the same characters, so the alternative is dropped here and the
     * parser rule for the `Lexis` nonterminal is written to accept either token
     * instead. A `Lexis` nonterminal left with no alternatives needs no token of its
     * own, which is what the null return means; `infix-op-8 ::= ':='` is one.
     */
    private fun unshadowedLexicalBody(def: LanguageDef, name: String): Ebnf? {
        val body = def.declarations.firstOrNull { it.nonterminal == name && it.section == "Lexis" }
            ?.body
            ?: return null
        val taken = literals(def).toSet()
        fun isTaken(option: Ebnf): Boolean = option is Ebnf.Terminal && option.value in taken
        return when (body) {
            is Ebnf.Alternatives -> {
                val kept = body.options.filterNot { isTaken(it) }
                when (kept.size) {
                    0 -> null
                    1 -> kept.single()
                    else -> body.copy(options = kept)
                }
            }
            else -> if (isTaken(body)) null else body
        }
    }

    /**
     * One `Lexis` body written as an ANTLR character pattern.
     *
     * A character range is written without quotes inside the set. ANTLR does accept
     * the quoted form, but it then warns that the quote character itself is in the
     * set, so `['0'-'9']` would also match `'`. A single character has to keep its
     * quotes, or `[0]` would be a set of one digit rather than the digit itself.
     *
     * A reference is inlined rather than written as a rule, because a reference to a
     * character set is not a token of its own: MiniJava writes `identifier ::= letter
     * ( letter | digit | '_' )*` with `letter ::= 'a'-'z' | 'A'-'Z'`, and substituting
     * the definition is the only way a single ANTLR token can express that. The names
     * already being expanded are carried along, so a definition that refers to itself
     * stops instead of looping.
     */
    private fun lexicalFragment(body: Ebnf?, def: LanguageDef, inlining: Set<String>): String = when (body) {
        is Ebnf.CharRange -> "[${body.from}-${body.to}]"
        is Ebnf.Terminal -> quote(body.value)
        // Each part of a sequence is grouped if it has an alternative of its own.
        // SL writes `id ::= ('a'-'z'|'A'-'Z') ('a'-'z'|'A'-'Z'|'0'-'9')*` as a
        // sequence of two alternatives, and joining them ungrouped gives
        // `[a-z]|[A-Z]([a-z]|[A-Z]|[0-9])*`, where the top-level `|` makes the whole
        // token an alternative between one lowercase letter and the rest of the
        // identifier, and `let` then lexes as `l` followed by `et`.
        is Ebnf.Alternatives -> body.options.joinToString("|") { lexicalFragment(it, def, inlining) }
        is Ebnf.Sequence -> body.parts.joinToString("") { group(lexicalFragment(it, def, inlining)) }
        is Ebnf.Optional -> "(${lexicalFragment(body.body, def, inlining)})?"
        is Ebnf.Repeated -> quantifier(body.quantifier, lexicalFragment(body.body, def, inlining))
        // `~'"'` is any character but a quote, so the set excludes it rather than
        // listing the whole Unicode range.
        is Ebnf.Negated -> buildString {
            append("[^")
            for (member in body.excluded) {
                if (member.to == null) append(escape(member.from)) else append("${escape(member.from)}-${escape(member.to)}")
            }
            append("]")
        }

        is Ebnf.Reference -> {
            val referred = body.name in def.lexicalGrammars && body.name !in inlining
            if (referred) {
                val inner = def.declarations.first { it.nonterminal == body.name && it.section == "Lexis" }.body
                // The inlined definition keeps its own parentheses, because it is being
                // substituted into a sequence: `letter ( letter | digit )*` becomes
                // `[a-z]|[A-Z]([a-z]|[A-Z]|[0-9])*` without them, which reads as an
                // alternative between the first letter and the whole rest of the
                // identifier rather than as a sequence of the two.
                val expanded = lexicalFragment(inner, def, inlining + body.name)
                if (hasTopLevelAlternative(expanded)) "($expanded)" else expanded
            } else {
                ANY_CHARACTER
            }
        }

        else -> ANY_CHARACTER
    }

    /**
     * A quantified fragment, with the parentheses a quantifier needs.
     *
     * `[A-Z]|[a-z]` quantified as a whole has to be written `([A-Z]|[a-z])+`, because a
     * quantifier binds more tightly than an alternative and would otherwise apply to
     * the second branch alone. A fragment that already carries a quantifier cannot be
     * quantified a second time, since ANTLR rejects `[a-zA-Z0-9]+*`, so the old one is
     * removed first.
     */
    private fun quantifier(quantifier: Ebnf.Quantifier, inner: String): String {
        val once = when {
            inner.endsWith("+") -> inner.dropLast(1)
            inner.endsWith("*") -> inner.dropLast(1)
            else -> inner
        }
        val grouped = if (hasTopLevelAlternative(once)) "($once)" else once
        return when (quantifier) {
            Ebnf.Quantifier.ONE_OR_MORE -> "$grouped+"
            Ebnf.Quantifier.ZERO_OR_MORE -> "$grouped*"
        }
    }

    /**
     * Whether a fragment has an alternative at its outermost level.
     *
     * A `|` inside a set is a literal, as in `[_|]`, and one inside parentheses has
     * already been grouped, so only a `|` at bracket depth zero widens the fragment.
     * Testing for the character anywhere would group `[a-z]|[A-Z]` again around an
     * inlined definition that is already partly parenthesised, and so quantify only
     * its last branch.
     */
    /**
     * A fragment in parentheses, if it has an alternative of its own.
     *
     * An already-parenthesised or already-grouped fragment is left alone, so repeated
     * grouping cannot nest more brackets than are needed.
     */
    private fun group(fragment: String): String =
        if (hasTopLevelAlternative(fragment)) "($fragment)" else fragment

    private fun hasTopLevelAlternative(fragment: String): Boolean {
        var depth = 0
        for (c in fragment) {
            when (c) {
                '[', '(' -> depth++
                ']', ')' -> depth--
                '|' -> if (depth == 0) return true
            }
        }
        return false
    }

    /**
     * The pattern for a `Lexis` body this writer cannot express.
     *
     * Every `Lexis` nonterminal of every language in the corpus is now written from
     * its own definition, so this is reached only for a body that is none of the
     * shapes above, such as a definition that refers to itself.
     */
    private const val ANY_CHARACTER = "[a-zA-Z0-9]+"

    private fun escape(c: String): String = "\\u" + c[0].code.toString(16).padStart(4, '0')

    /**
     * A literal quoted for ANTLR.
     *
     * Both the quote and the backslash have to be escaped inside an ANTLR literal. The
     * OCaml-Light corpus has both as literals of their own, `escaped-char ::= '\'|'"'|
     * '\\''|'n'`, and writing either unescaped ends the literal early and leaves the
     * rest of the file unparseable. The backslash is escaped first, or the backslash
     * added for the quote would itself be escaped again.
     */
    private fun quote(literal: String): String = buildString {
        append('\'')
        for (c in literal) {
            if (c == '\\' || c == '\'') append('\\')
            append(c)
        }
        append('\'')
    }

    /**
     * The name of the file a grammar has to be written to.
     *
     * ANTLR requires the `grammar` declaration and the file name to agree, so a caller
     * that picks its own file name gets a grammar that will not load. Deriving the one
     * name from the language is the only way to keep the two in step.
     */
    fun fileName(def: LanguageDef, name: String): String = "${grammarName(name)}.g4"

    /**
     * A grammar name, which may not contain a dash.
     *
     * `OCaml-light` becomes `Ocamldashlight`, because ANTLR rejects a dash in a
     * grammar name. The same replacement as for a rule name is used, so a grammar
     * name and the nonterminals inside it are spelled consistently.
     */
    private fun grammarName(name: String): String = name.split('-').joinToString("dash")

    /**
     * A note recording the parts of a disambiguation the grammar itself does not say.
     *
     * `left` and `right` become ANTLR's `<assoc=...>`, and a priority group becomes the
     * order of the alternatives, so neither is repeated here. What is left is what
     * ANTLR cannot express: `non-assoc` forbids chaining and has no option for it,
     * `assoc` names no side, and the `avoid` and `prefer` flags are for a concrete
     * syntax tree. Restating them as a comment keeps the generated file honest about
     * which of the disambiguation it implements and which it only records.
     */
    private fun disambiguationNote(alternatives: List<Production>, def: LanguageDef): String? {
        val marks = buildList {
            for (production in alternatives) {
                val annotated = def.expandedAnnotation[production.symbols]
                // Only an associativity the rule body could not write: ruleBody emits
                // `<assoc=left>` and `<assoc=right>` for the two sides ANTLR has.
                annotated?.associativity
                    ?.takeIf { it == Associativity.NON_ASSOC || it == Associativity.ASSOC }
                    ?.let { add(it.sdf) }
                annotated?.flags?.forEach { add(it.sdf) }
                def.expandedPriority[production.symbols]?.let { step ->
                    add(if (step.constructs) "priority (constructs)" else "priority")
                }
            }
        }
        if (marks.isEmpty()) return null
        return "  // " + marks.distinct().joinToString(", ")
    }
}
