package cbslang

/** A reader failure, reported with the file and line it came from. */
class CBSLangException(
    message: String,
    val file: String,
    val line: Int,
    val column: Int,
) : RuntimeException("$message ($file:$line:$column)")

/**
 * A construct the reader did not recognise. The reader records these instead of
 * failing, so that a language definition using notation this reader has never
 * seen still produces a usable [LanguageDef] and a list of what needs handling.
 */
data class Problem(
    val file: String,
    val line: Int,
    val kind: String,
    val text: String,
)

/**
 * One EBNF construct from a `Syntax` or `Lexis` declaration.
 *
 * The notation across the five vendored languages is small enough to enumerate:
 * alternatives, sequence, optional, repetition, terminals, nonterminal
 * references, `_` as an explicit gap, character ranges, and negation of a
 * character set.
 */
sealed interface Ebnf {
    data class Alternatives(val options: List<Ebnf>) : Ebnf
    data class Sequence(val parts: List<Ebnf>) : Ebnf
    data class Optional(val body: Ebnf) : Ebnf
    data class Repeated(val body: Ebnf, val quantifier: Quantifier) : Ebnf

    /** A `'literal'` terminal, with escapes already unescaped. */
    data class Terminal(val value: String) : Ebnf

    /** A reference to another nonterminal. */
    data class Reference(val name: String) : Ebnf

    /** `_`, which the language grammars use to admit layout between symbols. */
    data object Gap : Ebnf

    /** `'a'-'z'`. */
    data class CharRange(val from: String, val to: String) : Ebnf

    /** `~ ( 'a' | 'b' )`, i.e. any character outside the set. */
    data class Negated(val excluded: List<CharRangeAndTerminal>) : Ebnf

    enum class Quantifier(val mark: Char) {
        ZERO_OR_MORE('*'),
        ONE_OR_MORE('+'),
    }
}

/**
 * Whether a body can derive the empty string.
 *
 * A terminal, a character range and a set of characters all match at least one
 * character, and a reference inherits the answer from the body it names. An optional
 * and a zero-or-more repetition can both be empty, and a sequence or an alternative is
 * empty when every part or any option respectively is.
 *
 * A `Lexis` nonterminal that can be empty is not a token. A lexer cannot produce a
 * token of no characters, and ANTLR rejects a rule that can match nothing: SL's
 * `string-chars ::= string-char*` is the case in the corpus, where a `string` may
 * hold no characters at all.
 */
fun Ebnf.matchesEmpty(resolve: (String) -> Ebnf? = { null }): Boolean = when (this) {
    is Ebnf.Optional -> true
    is Ebnf.Repeated -> quantifier == Ebnf.Quantifier.ZERO_OR_MORE || body.matchesEmpty(resolve)
    is Ebnf.Sequence -> parts.all { it.matchesEmpty(resolve) }
    is Ebnf.Alternatives -> options.any { it.matchesEmpty(resolve) }
    is Ebnf.Reference -> resolve(name)?.matchesEmpty(resolve) ?: false
    // A gap is layout, and layout may be nothing at all.
    is Ebnf.Gap -> true
    is Ebnf.Terminal, is Ebnf.CharRange, is Ebnf.Negated -> false
}

data class CharRangeAndTerminal(val from: String, val to: String?)

/**
 * How the `Lexis` nonterminals a parser needs are written.
 *
 * [tokens] are written as lexer rules. [rules] are written as parser rules over a
 * token for [LexicalRule.atom], because a body that can derive the empty string is not
 * something a lexer can produce.
 */
data class LexicalUse(val tokens: List<String>, val rules: List<LexicalRule>)

/** A `Lexis` nonterminal written as a repetition of [atom] rather than as a token. */
data class LexicalRule(val nonterminal: String, val atom: Ebnf)

/**
 * A nonterminal declaration.
 *
 * Only the first declaration under a `Syntax` or `Lexis` keyword carries a
 * category name, and the same nonterminal is redeclared in more than one file
 * for some languages, so these are collected into a list rather than a map.
 *
 * [section] is the keyword the declaration appeared under. CBS-beta treats the
 * two differently, so the distinction cannot be recovered later: layout is
 * implicitly allowed between the symbols of a `Syntax` body and implicitly
 * excluded everywhere in a `Lexis` body.
 */
data class Declaration(
    val section: String,
    val category: String?,
    val nonterminal: String,
    val body: Ebnf,
    val file: String,
    val line: Int,
)

/** `Type sl-values ~> booleans | integers | ...`. */
data class TypeDeclaration(
    val name: String,
    val members: List<String>,
    val file: String,
    val line: Int,
)

/**
 * `Semantics start[[ P:start ]] : => null-type`.
 *
 * The return type is the funcon-level type the semantic function evaluates to.
 * It is not used by the translator, so it is kept as text.
 *
 * Some files define the function inline rather than in a following `Rule`, as
 * in MiniJava's
 *
 * ```
 * Semantics
 *   id[[ID:identifier]] : => ids
 *     = \"ID\"
 * ```
 *
 * in which case [definition] holds the body. That is a whole-pattern definition
 * rather than pattern-directed dispatch, so it is kept on the signature instead
 * of being turned into a [Rule].
 */
data class SemanticSignature(
    val function: String,
    val patternVariable: String?,
    val category: String?,
    val returnType: String?,
    val file: String,
    val line: Int,
    val definition: String? = null,
)

/**
 * A `Rule`.
 *
 * Three shapes occur in the vendored languages:
 *
 *  * `execute[[ I '=' AExp ';' ]] = assign(bound(id[[ I ]]), eval-arith[[ AExp ]])`
 *  * `[[ '(' P ')' ]] : pattern = [[ P ]]`, a disambiguation rule with no function
 *  * `run[[ P:program ]] : => null-type = ...` is not seen, but the reader
 *    accepts a return type on either form
 *
 * The left-hand side is a pattern over the concrete syntax tree and the
 * right-hand side is a funcon term. Neither is interpreted here; both are kept
 * as source text plus a token list, which is enough to report on the notation
 * and enough to match against a parse tree later. [body] and [pattern] are raw
 * source, so a funcon string literal keeps the `\"` escaping the file uses.
 */
data class Rule(
    val function: String?,
    val pattern: String,
    val category: String?,
    val returnType: String?,
    val body: String,
    val file: String,
    val line: Int,
    val patternTokens: List<String>,
) {
    /** True for the `[[ pat ]] : cat = [[ pat ]]` shape used for disambiguation. */
    val isDisambiguation: Boolean get() = function == null
}

/** A `#1 Programs` heading, or a `## Classes` sub-heading. */
data class Heading(val level: Int, val text: String, val line: Int)

/** A `[ #1 Programs ... ]` file index. */
data class IndexItem(val number: String, val title: String)

/**
 * A complete CBS language definition, assembled from every file that declares
 * the same `Language "..."`.
 */
data class LanguageDef(
    val name: String,
    val files: List<String>,
    val declarations: List<Declaration>,
    val types: List<TypeDeclaration>,
    val semantics: List<SemanticSignature>,
    val rules: List<Rule>,
    val headings: List<Heading>,
    val index: List<IndexItem>,
    val imports: List<String>,
    val problems: List<Problem>,
    val disambiguation: Disambiguation,
) {
    /** Distinct nonterminals, in declaration order. */
    val nonterminals: List<String> get() = declarations.map { it.nonterminal }.distinct()

    /** Categories that appear with a declaration, e.g. `AExp` for `aexp`. */
    val categories: Map<String, String> get() =
        declarations.mapNotNull { d -> d.category?.let { it to d.nonterminal } }.toMap()

    /**
     * Every alternative of every declaration that is already a flat sequence.
     * This is the space a disambiguation block refers to, so it is what a
     * mentioned production has to be found in.
     *
     * It is not the grammar: a body with a quantifier contributes nothing here
     * because an SDF3 annotation has no flat line to attach to. See
     * [grammarProductions] for what a parser has to implement.
     */
    val productions: List<Production> get() = declarations.flatMap { it.body.toProductions(it.nonterminal) }

    /**
     * Every production a parser for this language has to implement, including the
     * alternatives that [productions] leaves out because they are quantified, and
     * the auxiliary nonterminals invented to express a repetition.
     *
     * Only `Syntax` declarations contribute. A `Lexis` body defines a token, and
     * CBS-beta excludes layout throughout it, so it belongs to the lexer rather
     * than to the context-free grammar.
     */
    val grammarProductions: List<Production> get() = grammar.productions

    /** The nonterminals [grammarProductions] invented, which the language never declared. */
    val grammarAuxiliary: List<String> get() = grammar.auxiliary

    /**
     * The whole context-free grammar, expanded once.
     *
     * The auxiliary nonterminals are named from a `star` prefix that the language
     * does not use, and the prefix grows until it does, so a language that declares
     * a `star0` of its own does not end up with two nonterminals of one name.
     */
    val grammar: Grammar get() {
        val declared = declarations.map { it.nonterminal }.toSet()
        var prefix = "star"
        while (declared.any { it.startsWith(prefix) }) prefix += "star"
        val syntax = declarations.filter { it.section == "Syntax" }
        var counter = 0
        val names = syntax.associate { it.nonterminal to { n: Int -> "$prefix${counter++}_${it.nonterminal}_$n" } }
        val parts = syntax.map { it.prioritised().toGrammar(it.nonterminal, names.getValue(it.nonterminal)) }
        return Grammar(parts.flatMap { it.productions }, parts.flatMap { it.auxiliary })
    }

    /**
     * The index of the first `context-free priorities` step that names a production,
     * or [NOT_PRIORITISED] when no step names it.
     *
     * The corpus states an order rather than a ranking, so the index is the ranking:
     * a step written first binds tighter than a step written after it.
     */
    private val priorityRank: Map<Production, Int> by lazy {
        buildMap {
            for ((index, step) in disambiguation.priorities.withIndex()) {
                for (production in step.productions) putIfAbsent(production, index)
            }
        }
    }

    private companion object {
        const val NOT_PRIORITISED = Int.MAX_VALUE
    }

    /**
     * This declaration with its alternatives in disambiguation order.
     *
     * ANTLR gives a left-recursive alternative the precedence of its position in the
     * rule, the first being the tightest, so an SDF3 priority step becomes the
     * position of the alternatives it names. Alternatives no step names keep their
     * declared order after the ones a step does, because the corpus states no
     * precedence for them and inventing one would be a guess. Sorting is stable, so
     * alternatives in one step stay in the order the step writes them.
     */
    private fun Declaration.prioritised(): Ebnf {
        val body = this.body
        if (body !is Ebnf.Alternatives) return body
        val ranked = body.options.sortedBy { option ->
            val production = option.alternative()?.let { Production(nonterminal, it) }
            production?.let { priorityRank[it] } ?: NOT_PRIORITISED
        }
        return Ebnf.Alternatives(ranked)
    }

    /**
     * Each `Syntax` alternative the disambiguation annotates, and the productions that
     * alternative expands into.
     *
     * The match has to be made on the declared alternative rather than on the text of
     * either side, because a group can be quantified: the disambiguation names
     * `expression-list?` and the grammar spells the repetition with an auxiliary
     * nonterminal, so the two render differently and neither rendering is the other's.
     */
    private fun annotatedAlternatives(): Sequence<Pair<Ebnf, AnnotatedProduction>> = sequence {
        for (declaration in declarations.filter { it.section == "Syntax" }) {
            val options = when (val body = declaration.body) {
                is Ebnf.Alternatives -> body.options
                else -> listOf(body)
            }
            for (option in options) {
                val symbols = option.alternative() ?: continue
                val production = Production(declaration.nonterminal, symbols)
                val annotated = disambiguation.annotated.firstOrNull { it.production == production }
                    ?: continue
                yield(option to annotated)
            }
        }
    }

    /**
     * The associativity the disambiguation gives each expanded production, keyed by
     * the symbols the expansion produced.
     *
     * A quantified alternative expands into several productions and all of them carry
     * the annotation, because all of them are the one production the disambiguation
     * talks about. `assoc` names no side and ANTLR has no option for it, so it is
     * passed through and the writer leaves it out rather than guessing one.
     */
    val expandedAssociativity: Map<List<Symbol>, Associativity> by lazy {
        buildMap {
            for ((option, annotated) in annotatedAlternatives()) {
                val associativity = annotated.associativity ?: continue
                for (production in option.expanded()) {
                    put(production.symbols, associativity)
                }
            }
        }
    }

    /**
     * The annotation on each expanded production, for the parts of it the grammar
     * cannot express.
     */
    val expandedAnnotation: Map<List<Symbol>, AnnotatedProduction> by lazy {
        buildMap {
            for ((option, annotated) in annotatedAlternatives()) {
                for (production in option.expanded()) put(production.symbols, annotated)
            }
        }
    }

    /**
     * The priority step each expanded production belongs to, keyed by the symbols the
     * expansion produced, for the first step that names it.
     */
    val expandedPriority: Map<List<Symbol>, PriorityStep> by lazy {
        buildMap {
            for (declaration in declarations.filter { it.section == "Syntax" }) {
                val options = when (val body = declaration.body) {
                    is Ebnf.Alternatives -> body.options
                    else -> listOf(body)
                }
                for (option in options) {
                    val symbols = option.alternative() ?: continue
                    val production = Production(declaration.nonterminal, symbols)
                    val step = disambiguation.priorities.firstOrNull { it.productions.contains(production) }
                        ?: continue
                    for (expanded in option.expanded()) put(expanded.symbols, step)
                }
            }
        }
    }

    /** The productions one alternative expands into, with names that are never emitted. */
    private fun Ebnf.expanded(): List<Production> {
        var n = 0
        return toGrammar("aux") { "aux${n++}" }.productions
    }

    /**
     * The lexical nonterminals, i.e. the `Lexis` declarations.
     *
     * These are the tokens a generated lexer has to define. A `Syntax` body may
     * refer to them, as IMP's `aexp ::= num` refers to the `num` declared under
     * `Lexis`, so they are part of the grammar even though they are not part of
     * the context-free productions.
     */
    val lexicalNonterminals: List<String> get() =
        declarations.filter { it.section == "Lexis" }.map { it.nonterminal }.distinct()

    /**
     * How the `Lexis` nonterminals a parser needs are written.
     *
     * A `Lexis` nonterminal is a token, unless its body can derive the empty string: a
     * lexer cannot produce a token of no characters, so SL's `string-chars ::=
     * string-char*` and SIMPLE's `string-chars ::= (~'"')*` become a parser rule over
     * a token for the character they repeat. A `Lexis` nonterminal nothing else names
     * is neither: MiniJava's `letter` and `digit` are inlined into the `identifier`
     * that uses them, because a token for `digit` would shadow
     * `integer-literal ::= digit+` and `1` would lex as something no parser rule
     * accepts.
     */
    val lexicalUse: LexicalUse get() {
        val bodies = declarations.filter { it.section == "Lexis" }
            .associate { it.nonterminal to it.body }
        val resolve = { name: String -> bodies[name] }
        // The expanded grammar rather than the flat alternatives, because a `Syntax`
        // body whose only symbol is a `Lexis` nonterminal is one of the shapes the flat
        // view drops: IMP's `num ::= '-'?_decimal` carries a quantifier, so it has no
        // flat production and `decimal` would never be reached.
        val queue = ArrayDeque(
            grammar
                .productions
                .flatMap { it.symbols }
                .filterIsInstance<Symbol.NonTerminal>()
                .map { it.name }
                .filter { it in bodies }
                .distinct(),
        )
        val tokens = mutableSetOf<String>()
        val rules = mutableListOf<LexicalRule>()
        val done = mutableSetOf<String>()
        while (queue.isNotEmpty()) {
            val name = queue.removeFirst()
            if (!done.add(name)) continue
            val body = bodies.getValue(name)
            if (!body.matchesEmpty(resolve)) {
                tokens += name
                continue
            }
            // A repetition or an optional at the top of the body is the one shape that
            // can be written as a rule over the token it repeats. Anything else that can
            // be empty is left as a token, where the tool will say so, rather than
            // being written as a rule this does not know how to build.
            val atom = when (body) {
                is Ebnf.Repeated -> if (body.quantifier == Ebnf.Quantifier.ZERO_OR_MORE) body.body else null
                is Ebnf.Optional -> body.body
                else -> null
            }
            if (atom == null || atom.matchesEmpty(resolve)) {
                tokens += name
                continue
            }
            rules += LexicalRule(name, atom)
            // A reference names another `Lexis` body, which may not be wanted by any
            // parser rule and so has to be put in the token set here.
            (atom as? Ebnf.Reference)?.let { if (it.name in bodies && it.name !in done) queue.add(it.name) }
        }
        return LexicalUse(tokens = bodies.keys.filter { it in tokens }, rules = rules)
    }

    /**
     * The productions a lexer has to define, one set per `Lexis` nonterminal.
     *
     * A `Lexis` body is a regular expression over characters and character
     * ranges rather than a context-free body, so it is expanded to a regular
     * definition of its own instead of being run through [grammarProductions].
     */
    val lexicalGrammars: Map<String, Grammar> get() =
        declarations.filter { it.section == "Lexis" }
            .associate { it.nonterminal to it.body.toGrammar(it.nonterminal) { n -> "lex${it.nonterminal}$n" } }

    /**
     * A production the disambiguation mentions that the language does not
     * declare.
     *
     * An annotation on a production that does not exist is a discrepancy in the
     * language definition rather than a notation the reader failed on, so it is
     * reported separately: a parser generator would otherwise silently apply a
     * priority that can never be reached.
     */
    val undeclaredProductions: List<Production>
        get() = disambiguation.mentioned.filter { it !in declaredAlternatives }.distinct()

    /**
     * The alternatives of every `Syntax` declaration, in the symbol form the
     * disambiguation quotes.
     *
     * This is the space a disambiguation refers to, so it is where a mentioned
     * production has to be found, and it is deliberately not [grammarProductions]:
     * that one expands a quantifier into an auxiliary nonterminal, and
     * ``expression-list?`` is not a production any more once it has.
     */
    val declaredAlternatives: Set<Production> get() = declarations
        .filter { it.section == "Syntax" }
        .flatMap { declaration ->
            when (val body = declaration.body) {
                is Ebnf.Alternatives -> body.options.mapNotNull { option ->
                    option.alternative()?.let { Production(declaration.nonterminal, it) }
                }

                else -> listOfNotNull(
                    body.alternative()?.let { Production(declaration.nonterminal, it) },
                )
            }
        }
        .toSet()
}
