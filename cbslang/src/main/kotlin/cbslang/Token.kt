package cbslang

/**
 * One lexical token of a CBS language-definition file.
 *
 * [start] and [end] are offsets into the file, so a caller can recover the exact
 * source text of any construct. That is used for the right-hand sides of rules,
 * which are funcon terms rather than EBNF and are therefore left as text.
 */
data class Token(
    val kind: Kind,
    val text: String,
    val start: Int,
    val end: Int,
    val line: Int,
    val column: Int,
    val file: String,
) {
    enum class Kind { IDENT, STRING, DQUOTED, PUNCT, EOF }

    /** Source text of this token, recovered from the file. */
    fun source(source: String): String = source.substring(start, end)

    /**
     * The literal's characters. Escapes are resolved by the tokenizer when it
     * scans the literal, so this is just [text]; it exists to document that a
     * STRING token is an unquoted terminal, unlike a PUNCT token whose [text] is
     * the source spelling.
     */
    fun literal(): String = text

    override fun toString(): String = "$kind($text)@$line:$column"
}
