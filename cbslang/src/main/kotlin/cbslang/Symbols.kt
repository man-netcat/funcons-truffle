package cbslang

/**
 * One symbol in the right-hand side of a production.
 *
 * Both an EBNF body read from a `Syntax` declaration and a production quoted in
 * an SDF3 disambiguation block reduce to a list of these, which is what lets
 * the two be compared. The comparison is what turns a transcribed priority
 * annotation from prose into something checkable: every production named in a
 * disambiguation block has to name exactly one alternative of exactly one
 * declaration.
 */
sealed interface Symbol {
    data class NonTerminal(val name: String) : Symbol
    data class Lit(val value: String) : Symbol
    data class Range(val from: String, val to: String) : Symbol

    /** The `_` of the language grammars, which admits layout between symbols. */
    data object Gap : Symbol

    /**
     * Symbols with a repetition or optional quantifier.
     *
     * A `Syntax` body that is expanded into flat productions loses its quantifier,
     * because `'x'*` becomes an auxiliary nonterminal and there is nothing left on
     * the right-hand side to carry it. A disambiguation block quotes the production
     * as the corpus writes it,
     * ``expression ::= expression '.' identifier '(' expression-list? ')'``, so the
     * quantifier has to survive there. Dropping the production instead loses the
     * top-priority group member that says a call binds tighter than an operator,
     * which is the one thing the disambiguation exists to say.
     *
     * The body is a list because a quantifier binds a group as well as a symbol:
     * SL writes `expr '(' exps? ')'`, and `(a b)?` is one quantified group.
     * [Ebnf.alternative] produces this form from a declared body and
     * [SdfReader] from a quoted production, so the two can be compared.
     */
    data class Quantified(val symbols: List<Symbol>, val quantifier: Char) : Symbol

    /** `[A-Za-z0-9]`, or `~[...]` for its complement. */
    data class CharClass(val negated: Boolean, val members: List<Member>) : Symbol

    data class Member(val from: String, val to: String?)

    fun render(): String = when (this) {
        // A nonterminal is written bare inside a quoted production, which is how
        // the corpus writes them: ``aexp ::= aexp '+' aexp``. Quoting it as well
        // would nest backticks, and the run of them would end the production
        // early when read back.
        is NonTerminal -> name
        is Lit -> "'" + value.replace("\\", "\\\\").replace("'", "\\'") + "'"
        is Range -> "'$from'-'$to'"
        is Gap -> "_"
        // A single symbol needs no brackets, so `expression-list?` is written as the
        // corpus writes it rather than as `(expression-list)?`.
        is Quantified -> {
            val body = if (symbols.size == 1) symbols.single().render()
            else symbols.joinToString(" ", prefix = "(", postfix = ")") { it.render() }
            body + quantifier
        }
        is CharClass -> {
            val body = members.joinToString("") { if (it.to == null) escape(it.from) else "${escape(it.from)}-${escape(it.to!!)}" }
            if (negated) "~$body" else body
        }
    }

    private fun escape(c: String): String = c.replace("\\", "\\\\").replace("^", "\\^").replace("]", "\\]")
}

/** A production, canonicalised to `nonterminal ::= symbol symbol ...`. */
data class Production(val nonterminal: String, val symbols: List<Symbol>) {
    fun render(): String = "``$nonterminal ::= " + symbols.joinToString(" ") { it.render() } + "``"

    override fun toString(): String = render()
}

/**
 * One alternative of an EBNF body, as symbols, with quantifiers kept.
 *
 * A disambiguation block quotes a production the way the corpus writes it, so
 * ``expression ::= expression '.' identifier '(' expression-list? ')'`` keeps its
 * `?`, while the same alternative expanded into a parser rule becomes two
 * productions and one of them names an auxiliary nonterminal. Comparing the two
 * in the expanded domain is therefore not possible, so both sides are compared in
 * the domain the corpus writes in: this is the canonical form of a declared
 * alternative, and [Production] in that same form is what the reader produces.
 *
 * A body that is not a single sequence of symbols — a nested alternative, say —
 * has no form here and yields null, which the caller reports rather than guesses.
 */
fun Ebnf.alternative(): List<Symbol>? {
    fun of(body: Ebnf): List<Symbol> = when (body) {
        is Ebnf.Alternatives -> emptyList()
        is Ebnf.Sequence -> body.parts.flatMap(::of)
        is Ebnf.Optional -> listOf(Symbol.Quantified(of(body.body), '?'))
        is Ebnf.Repeated -> listOf(Symbol.Quantified(of(body.body), body.quantifier.mark))
        is Ebnf.Terminal -> listOf(Symbol.Lit(body.value))
        is Ebnf.Reference -> listOf(Symbol.NonTerminal(body.name))
        is Ebnf.Gap -> listOf(Symbol.Gap)
        is Ebnf.CharRange -> listOf(Symbol.Range(body.from, body.to))
        is Ebnf.Negated -> listOf(
            Symbol.CharClass(
                negated = true,
                members = body.excluded.map { Symbol.Member(it.from, it.to) },
            ),
        )
    }
    if (this is Ebnf.Alternatives) return null
    val symbols = of(this)
    // An empty list means a nested alternative, whose branches are alternatives in
    // their own right and so cannot be one production here.
    return if (symbols.isEmpty()) null else symbols
}

/**
 * The alternatives of one declaration, flattened to productions.
 *
 * Returns only the alternatives that are already flat sequences, which is the
 * space a disambiguation block can annotate: an SDF3 annotation names one
 * `nonterminal ::= symbol symbol ...` line, so a quantified body has no
 * production there to annotate.
 *
 * This is deliberately *not* the language's grammar. A body such as
 * `num ::= '-'?_decimal` has no flat alternative and would contribute nothing
 * here, while being a real production of the language. Use [Ebnf.toGrammar] to
 * get the productions a parser has to implement.
 */
fun Ebnf.toProductions(nonterminal: String): List<Production> =
    alternatives().map { Production(nonterminal, it) }

/**
 * The alternatives of this EBNF, each as a flat list of symbols.
 *
 * A group is flattened into its parent sequence. A quantifier has no flat
 * alternative, so a body containing one contributes none; see
 * [Ebnf.toProductions] for why that is the right space for this and
 * [Ebnf.toGrammar] for the one that is not.
 */
private fun Ebnf.alternatives(): List<List<Symbol>> {
    if (this is Ebnf.Alternatives) {
        val result = mutableListOf<List<Symbol>>()
        for (option in options) result.addAll(option.alternatives())
        return result
    }
    if (this is Ebnf.Sequence) {
        val flat = mutableListOf<Symbol>()
        for (part in parts) {
            // An alternative inside a sequence, or a quantifier, has no flat
            // production, so the whole declaration contributes none.
            if (part is Ebnf.Alternatives) return emptyList()
            val symbols = part.toSymbols() ?: return emptyList()
            flat.addAll(symbols)
        }
        return listOf(flat)
    }
    val symbols = toSymbols() ?: return emptyList()
    return listOf(symbols)
}

/**
 * The productions one declaration expands to, and the nonterminals invented to
 * express it.
 *
 * [productions] covers every alternative of the body, and [auxiliary] names the
 * nonterminals that were not in the language definition. A generated parser has
 * to define those; a disambiguation block will never mention them, so they are
 * kept apart rather than mixed into the declared productions.
 */
data class Grammar(val productions: List<Production>, val auxiliary: List<String>)

/**
 * Expands a body to the context-free grammar a parser has to implement.
 *
 * Unlike [Ebnf.toProductions] this loses nothing: a body with a quantifier or a
 * nested alternative expands to all of its alternatives.
 *
 * The auxiliary nonterminals are named by [auxiliaryName], which the caller supplies
 * because only it knows which names the language already uses. A name has to start
 * with a lowercase letter to be a parser rule at all, since ANTLR reads a rule
 * starting with an uppercase one as a token.
 */
fun Ebnf.toGrammar(nonterminal: String, auxiliaryName: (Int) -> String): Grammar {
    val auxiliary = mutableListOf<String>()
    // A counter rather than a name derived from the body, so the same
    // declaration always yields the same grammar.
    var nextAux = 0
    fun aux(): String {
        val name = auxiliaryName(nextAux++)
        auxiliary += name
        return name
    }

    val result = mutableListOf<Production>()

    fun expand(e: Ebnf): List<List<Symbol>> = when (e) {
        is Ebnf.Alternatives -> e.options.flatMap { expand(it) }
        is Ebnf.Sequence -> {
            // A cross product: every combination of one alternative per part.
            var acc = listOf(emptyList<Symbol>())
            for (part in e.parts) {
                val options = expand(part)
                acc = acc.flatMap { prefix -> options.map { prefix + it } }
            }
            acc
        }

        // The body may itself be a sequence or an alternative, and a quantifier
        // over one of those has to be a single nonterminal, so it is expanded
        // into an auxiliary and referenced once.
        is Ebnf.Optional -> expand(e.body) + listOf(emptyList())
        is Ebnf.Repeated -> {
            val name = aux()
            val inner = expand(e.body)
            when (e.quantifier) {
                // `x* ::= | x x*` admits the empty sequence and `x+ ::= x | x x+`
                // does not. Both are right-recursive, which is what a repetition
                // means, and neither needs the body's own alternatives to be
                // finite.
                Ebnf.Quantifier.ZERO_OR_MORE -> {
                    result += Production(name, emptyList())
                    for (one in inner) {
                        result += Production(name, one + listOf(Symbol.NonTerminal(name)))
                    }
                }

                Ebnf.Quantifier.ONE_OR_MORE -> {
                    for (one in inner) result += Production(name, one)
                    for (one in inner) {
                        result += Production(name, one + listOf(Symbol.NonTerminal(name)))
                    }
                }
            }
            listOf(listOf(Symbol.NonTerminal(name)))
        }

        // On `e` and not on a bare `toSymbols()`: this is a local function, so an
        // unqualified call would bind to the receiver of the enclosing extension,
        // which is the whole body, and take the grouped branch that returns null.
        else -> listOf(e.toSymbols() ?: emptyList())
    }

    for (alternative in expand(this)) result += Production(nonterminal, alternative)
    return Grammar(result, auxiliary)
}

private fun Ebnf.toSymbols(): List<Symbol>? = when (this) {
    is Ebnf.Terminal -> listOf(Symbol.Lit(value))
    is Ebnf.Reference -> listOf(Symbol.NonTerminal(name))
    is Ebnf.Gap -> listOf(Symbol.Gap)
    is Ebnf.CharRange -> listOf(Symbol.Range(from, to))
    is Ebnf.Negated -> listOf(
        Symbol.CharClass(
            negated = true,
            members = excluded.map { Symbol.Member(it.from, it.to) },
        ),
    )
    // A group is not a symbol, and a quantifier is not a flat production.
    is Ebnf.Optional, is Ebnf.Repeated, is Ebnf.Alternatives, is Ebnf.Sequence -> null
}
