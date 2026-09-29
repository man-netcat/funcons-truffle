package cbslang

/**
 * How a production groups its repetitions.
 *
 * The corpus uses SDF3's spelling: `left`, `right`, `non-assoc` and `assoc`.
 * `assoc` is not one of the three usual choices; it appears in IMP without
 * saying which, so it is kept as its own value rather than guessed at.
 */
enum class Associativity(val sdf: String) {
    LEFT("left"),
    RIGHT("right"),
    NON_ASSOC("non-assoc"),
    ASSOC("assoc"),
    ;

    companion object {
        fun of(sdf: String): Associativity? = entries.firstOrNull { it.sdf == sdf }
    }
}

/** The other annotations an SDF3 `context-free syntax` block carries. */
enum class Flag(val sdf: String) {
    AVOID("avoid"),
    PREFER("prefer"),
}

/**
 * `id = "function"  reject`, or ``id = ``keyword``  reject``, which is how the
 * corpus usually writes it: the reserved words are themselves a lexis
 * declaration, and the annotation says the lexer must not read one of them as
 * an `id`. The second form is the useful one, because it keeps the word list
 * with the lexis that declares it.
 */
sealed interface Rejection {
    val target: String

    data class OfLiteral(override val target: String, val keyword: String) : Rejection
    data class OfNonTerminal(override val target: String, val nonterminal: String) : Rejection
}

/** `id -/- [A-Za-z0-9]`: the token `id` does not continue with these characters. */
data class Restriction(val target: String, val members: List<Symbol.Member>)

/** A production with the annotations given beside it, as in `{left}`. */
data class AnnotatedProduction(
    val production: Production,
    val associativity: Associativity?,
    val flags: Set<Flag>,
)

/**
 * One step of an SDF3 `context-free priorities` block.
 *
 * A step is one or more productions, optionally a group `{ ... }` of them, and
 * carries the associativity of the step. `<0>` marks a step as constructing
 * rather than destructing, and `. >` is a step with no associativity.
 */
data class PriorityStep(
    val productions: List<Production>,
    val associativity: Associativity?,
    val group: Boolean,
    val constructs: Boolean,
)

/**
 * A language's disambiguation, as specified in the SDF3 blocks in the comments
 * of its `Syntax SDF` and `Lexis SDF` sections.
 *
 * The corpus states all of this in prose inside comments, and the notation
 * itself is not part of CBS: SL's disambiguation file says so outright. It is
 * read because a parser generator needs it, and it is written back out as a
 * normalised data file per language so that the rest of the front-end has a
 * stable input.
 */
data class Disambiguation(
    val language: String,
    val rejections: List<Rejection>,
    val restrictions: List<Restriction>,
    val annotated: List<AnnotatedProduction>,
    val priorities: List<PriorityStep>,
) {
    val isEmpty: Boolean
        get() = rejections.isEmpty() && restrictions.isEmpty() && annotated.isEmpty() && priorities.isEmpty()

    /**
     * The productions the disambiguation refers to, in the order they are
     * mentioned. Each has to name exactly one alternative of exactly one
     * declaration for the annotation to mean anything.
     */
    val mentioned: List<Production>
        get() = annotated.map { it.production } + priorities.flatMap { it.productions }

    /**
     * A normalised rendering of this disambiguation.
     *
     * It is written in the same form the corpus uses, a `Syntax SDF` heading and
     * a block comment, so [SdfReader] reads a generated data file back with no
     * separate path: whatever this writes, the reader has already read.
     */
    fun render(): String = buildString {
        appendLine("# Disambiguation for \"$language\".")
        appendLine("# Generated from the SDF3 blocks in the CBS comments; do not edit by hand.")
        appendLine("Syntax SDF")
        appendLine("/*")
        if (rejections.isNotEmpty()) {
            appendLine("lexical syntax")
            for (r in rejections) {
                val keyword = when (r) {
                    is Rejection.OfLiteral -> "\"${r.keyword}\""
                    is Rejection.OfNonTerminal -> "``${r.nonterminal}``"
                }
                appendLine("  ``${r.target}`` = $keyword {reject}")
            }
        }
        if (restrictions.isNotEmpty()) {
            appendLine("lexical restrictions")
            for (r in restrictions) {
                appendLine("  ``${r.target}`` -/- [${r.members.joinToString("") { m -> m.render() }}]")
            }
        }
        if (annotated.isNotEmpty()) {
            appendLine("context-free syntax")
            for (a in annotated) {
                val marks = (listOfNotNull(a.associativity?.sdf) + a.flags.map { it.sdf }).joinToString(",")
                appendLine("  ${a.production.render()}${if (marks.isEmpty()) "" else " {$marks}"}")
            }
        }
        if (priorities.isNotEmpty()) {
            appendLine("context-free priorities")
            priorities.forEachIndexed { index, step ->
                if (index > 0) appendLine(if (step.constructs) "  <0> >" else "  >")
                // A group opens and closes around the step's productions, and the
                // label is written on the first production as the corpus writes
                // it, so the same reader reads both.
                if (step.group) appendLine("  {")
                val label = step.associativity?.sdf
                for ((i, p) in step.productions.withIndex()) {
                    val mark = if (i == 0 && label != null) " {$label}" else ""
                    appendLine("    ${p.render()}$mark")
                }
                if (step.group) appendLine("  }")
            }
        }
        appendLine("*/")
    }
}

private fun Symbol.Member.render(): String =
    if (to == null) escapeInClass(from) else "${escapeInClass(from)}-${escapeInClass(to)}"

private fun escapeInClass(c: String): String =
    c.replace("\\", "\\\\").replace("^", "\\^").replace("]", "\\]").replace("-", "\\-")
