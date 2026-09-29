package main

import cbs.CBSLexer
import cbs.CBSParser
import cbs.CBSParser.ObjectContext
import org.antlr.v4.runtime.CharStreams
import org.antlr.v4.runtime.CommonTokenStream
import org.antlr.v4.runtime.ParserRuleContext

/**
 * Parses CBS source with the same parser the generator uses.
 *
 * The tests below are about which parse tree node ends up naming what, so they
 * have to go through the real grammar: a hand-built context object would not
 * notice the grammar changing underneath it.
 */
internal fun parseCbs(source: String): CBSParser.RootContext =
    CBSParser(CommonTokenStream(CBSLexer(CharStreams.fromString(source)))).root()

/** The nth declaration in [source], as the context type it actually parses as. */
internal fun <T : ParserRuleContext> node(source: String, index: Int = 0): T {
    // `root` is a sequence of `index` and `object`, so the declarations are
    // exactly its ObjectContext children. Calling the generated `object()` is a
    // syntax error, since `object` is a keyword.
    val objects = parseCbs(source).children.filterIsInstance<ObjectContext>()
    require(index < objects.size) { "only ${objects.size} declaration(s) in the snippet" }
    @Suppress("UNCHECKED_CAST")
    return objects[index] as T
}
