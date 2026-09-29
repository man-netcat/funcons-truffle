package cbslang

/**
 * Splits a CBS language-definition file into tokens.
 *
 * The format is not the funcon-library format that `CBS.g4` handles; it is the
 * language-definition dialect, and this is its lexer. Two facts drive the
 * design:
 *
 *  * Line breaks are not significant, but item keywords are. The reader relies on
 *    a keyword being the first token of a line to delimit the previous item, so
 *    every token carries its line.
 *  * Funcon terms on the right-hand side of a `Rule` contain `\"...\"` string
 *    literals, which have to lex as one token or the quote characters get lost.
 */
class Tokenizer(private val source: String, private val file: String) {

    /**
     * Inconsistencies found while scanning, reported rather than thrown, so that
     * one malformed terminal does not cost the rest of the file.
     */
    val problems = mutableListOf<Problem>()

    fun tokenize(): List<Token> {
        val tokens = mutableListOf<Token>()
        var i = 0
        var line = 1
        var lineStart = 0

        fun col(pos: Int) = pos - lineStart + 1

        while (i < source.length) {
            val c = source[i]
            when {
                c == '\n' -> {
                    i++
                    line++
                    lineStart = i
                }

                c.isWhitespace() -> i++

                // Comments. Block comments are nested, because the vendored
                // language files wrap commented-out SDF notation in one.
                c == '/' && i + 1 < source.length && source[i + 1] == '*' -> {
                    var depth = 0
                    val from = i
                    while (i < source.length) {
                        when {
                            source[i] == '\n' -> {
                                line++
                                i++
                                lineStart = i
                            }

                            source.startsWith("/*", i) -> {
                                depth++
                                i += 2
                            }

                            source.startsWith("*/", i) -> {
                                depth--
                                i += 2
                                if (depth == 0) break
                            }

                            else -> i++
                        }
                    }
                    if (depth != 0) {
                        throw CBSLangException("unterminated comment", file, line, col(from))
                    }
                }

                c == '/' && i + 1 < source.length && source[i + 1] == '/' -> {
                    while (i < source.length && source[i] != '\n') i++
                }

                // A language terminal: 'abc', with \\ and \' escapes.
                c == '\'' -> {
                    val start = i
                    val startLine = line
                    val startCol = col(i)
                    i++
                    val sb = StringBuilder()
                    while (true) {
                        if (i >= source.length || source[i] == '\n') {
                            throw CBSLangException("unterminated ' literal", file, startLine, startCol)
                        }
                        if (source[i] == '\\' && i + 1 < source.length) {
                            val after = source[i + 1]
                            when {
                                after == '\\' -> {
                                    sb.append('\\')
                                    i += 2
                                }
                                // A `\'` escapes a quote only when a further
                                // `'` follows to close the literal. The vendored
                                // OCaml-Light file writes the backslash terminal
                                // as '\' and the quote terminal as '\''; the two
                                // start identically, so the reader takes the
                                // reading that closes the literal and reports it.
                                after == '\'' && i + 2 < source.length && source[i + 2] == '\'' -> {
                                    sb.append('\'')
                                    i += 2
                                }

                                else -> {
                                    problems += Problem(file, startLine, "backslash in terminal", sb.toString())
                                    sb.append('\\')
                                    i++
                                }
                            }
                            continue
                        }
                        if (source[i] == '\'') {
                            i++
                            break
                        }
                        sb.append(source[i])
                        i++
                    }
                    tokens += Token(Token.Kind.STRING, sb.toString(), start, i, startLine, startCol, file)
                }

                // A double-quoted string: the name in `Language "IMP"`, and the
                // funcon string literals on a rule right-hand side, which the
                // vendored files escape as \"...\". Both open and close with the
                // same two characters, so the first following \" or " is the end.
                c == '"' || (c == '\\' && i + 1 < source.length && source[i + 1] == '"') -> {
                    val start = i
                    val startLine = line
                    val startCol = col(i)
                    i += if (source[start] == '\\') 2 else 1
                    val sb = StringBuilder()
                    while (true) {
                        if (i >= source.length || source[i] == '\n') {
                            throw CBSLangException("unterminated \" literal", file, startLine, startCol)
                        }
                        if (source[i] == '\\' && i + 1 < source.length && source[i + 1] == '"') {
                            i += 2
                            break
                        }
                        if (source[i] == '"') {
                            i++
                            break
                        }
                        sb.append(source[i])
                        i++
                    }
                    tokens += Token(Token.Kind.DQUOTED, sb.toString(), start, i, startLine, startCol, file)
                }

                // A heading number: `#1 Programs`, `#1.1 Booleans`, `## Classes`.
                // The whole number is part of the heading token.
                c == '#' && i + 1 < source.length && source[i + 1].isDigit() -> {
                    val start = i
                    val startLine = line
                    val startCol = col(i)
                    if (source[i + 1] == '#' && i + 2 < source.length && source[i + 2].isDigit()) i++
                    i++
                    while (i < source.length && (source[i].isDigit() || source[i] == '.')) i++
                    tokens += Token(Token.Kind.PUNCT, source.substring(start, i), start, i, startLine, startCol, file)
                }

                // CBS-beta says nonterminals start with lowercase letters, so a
                // leading `_` is the gap marker rather than the start of a name.
                // The corpus writes it attached, as in `num ::= '-'?_decimal`, and
                // lexing it as one identifier would silently name a nonterminal
                // `_decimal` that does not exist.
                c.isLetter() -> {
                    val start = i
                    val startLine = line
                    val startCol = col(i)
                    while (i < source.length && (source[i].isLetterOrDigit() || source[i] == '-' || source[i] == '_')) {
                        // A trailing `-` belongs to a following operator, not the
                        // identifier, so do not swallow it.
                        if (source[i] == '-' &&
                            !(i + 1 < source.length && (source[i + 1].isLetterOrDigit() || source[i + 1] == '_'))
                        ) {
                            break
                        }
                        // A trailing `_` is the gap marker, not part of the name. The
                        // corpus writes it attached on both sides, as in
                        // `string ::= '"'_string-chars_'"'`, and swallowing it would
                        // name a nonterminal `string-chars_` that does not exist.
                        if (source[i] == '_' && (i + 1 >= source.length || !source[i + 1].isLetterOrDigit())) {
                            break
                        }
                        i++
                    }
                    tokens += Token(Token.Kind.IDENT, source.substring(start, i), start, i, startLine, startCol, file)
                }

                // A number, appearing in rule bodies as an initial value, e.g.
                // `allocate-initialised-variable(integers, 0)`.
                c.isDigit() -> {
                    val start = i
                    val startLine = line
                    val startCol = col(i)
                    while (i < source.length && source[i].isDigit()) i++
                    tokens += Token(Token.Kind.PUNCT, source.substring(start, i), start, i, startLine, startCol, file)
                }

                else -> {
                    val start = i
                    val startLine = line
                    val startCol = col(i)
                    val text = PUNCTUATION.firstOrNull { source.startsWith(it, i) }
                        ?: throw CBSLangException("unexpected character '$c'", file, startLine, startCol)
                    i += text.length
                    tokens += Token(Token.Kind.PUNCT, text, start, i, startLine, startCol, file)
                }
            }
        }
        tokens += Token(Token.Kind.EOF, "", source.length, source.length, line, col(i), file)
        return tokens
    }

    private companion object {
        /** Longest match first, so `::=` beats `:` and `[[` beats `[`. */
        val PUNCTUATION = listOf(
            "::=", "~>", "=>", "[[", "]]", "|->", "<:", "...", "##", "#",
            "=", ":", "|", "(", ")", "[", "]", "{", "}", "?", "*", "+", "~", "-", ",", "_",
            "&", "<", ">", "/", "!", "@", "%", ";", ".",
        )
    }
}
