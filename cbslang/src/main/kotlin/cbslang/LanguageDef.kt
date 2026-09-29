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

    enum class Quantifier { ZERO_OR_MORE, ONE_OR_MORE }
}

data class CharRangeAndTerminal(val from: String, val to: String?)

/**
 * A nonterminal declaration.
 *
 * Only the first declaration under a `Syntax` or `Lexis` keyword carries a
 * category name, and the same nonterminal is redeclared in more than one file
 * for some languages, so these are collected into a list rather than a map.
 */
data class Declaration(
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
) {
    /** Distinct nonterminals, in declaration order. */
    val nonterminals: List<String> get() = declarations.map { it.nonterminal }.distinct()

    /** Categories that appear with a declaration, e.g. `AExp` for `aexp`. */
    val categories: Map<String, String> get() =
        declarations.mapNotNull { d -> d.category?.let { it to d.nonterminal } }.toMap()
}
