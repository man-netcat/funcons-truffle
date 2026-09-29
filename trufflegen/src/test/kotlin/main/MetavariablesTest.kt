package main

import cbs.CBSParser.FunconDefinitionContext
import objects.FunconObject
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

/**
 * A metavariable is visible to every rule of the file that declares it.
 *
 * The metavariable map used to be filled in by the visitor as it walked a file's
 * parse tree, which it does in textual order. So a rule that used a metavariable
 * textually before the `Meta-variables` block that declared it was generated
 * against an empty map, and FunconObject indexed it with `!!`.
 *
 * The corpus happens not to do this, so nothing caught it: of the 43 files, only
 * Bits.cbs defines anything before its `Meta-variables` block, and what it
 * defines does not mention the metavariable. One edit to that file away.
 */
class MetavariablesTest {
    /**
     * `id(T) : T` with a rule on it. The rule's pattern is `id(X:T)`, so the
     * parameter's declared type is the metavariable `T`, which is the path into
     * `metaVariableMap` and the `!!`.
     */
    private fun snippet(metavariablesLast: Boolean): String {
        val funcon = """
            Funcon
              id(T) : T
            Rule
              id(X:T) ---> X
        """.trimIndent()
        val metavariables = """
            Meta-variables
              T <: values
        """.trimIndent()

        // `values` has to be declared: FunconObject resolves the supertype of a
        // metavariable, and the rule needs a declared type to be a rule at all.
        val source = if (metavariablesLast) "$funcon\n\n$metavariables" else "$metavariables\n\n$funcon"
        return "Type values <: empty-type\n\n$source"
    }

    /**
     * Builds through `ObjectCollector`, because the ordering being tested is the
     * visitor's, not the parse tree's. `contentStr` is read on purpose: the rules
     * of a funcon are built when its code is asked for, not when the object is
     * constructed, so a test that only counts them never reaches the failure.
     */
    private fun generatedCodeFor(source: String): String {
        val collector = ObjectCollector(RegistryBuilder(emptySet()))
        collector.collect(parseCbs(source))

        val id = collector.objects.filterIsInstance<FunconObject>().single()
        assertEquals(setOf("T"), id.metavariableMap.keys)
        return id.contentStr
    }

    @Test
    fun metavariableDeclaredFirstTest() {
        val code = generatedCodeFor(snippet(metavariablesLast = false))
        assertEquals(1, Regex("when \\{").findAll(code).count())
    }

    @Test
    fun metavariableDeclaredLastTest() {
        // The same funcon with the block moved below the rule. Before the
        // pre-pass this threw on `metaVariableMap["T"]!!` while building the rule.
        val code = generatedCodeFor(snippet(metavariablesLast = true))
        assertEquals(1, Regex("when \\{").findAll(code).count())
    }
}
