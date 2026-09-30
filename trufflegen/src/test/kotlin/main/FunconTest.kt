package main

import cbs.CBSParser.*
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Test

/**
 * One declaration is one [Funcon], whatever syntax it was written in.
 *
 * These were five separate `when` blocks over the same context types, one each for
 * the name, the aliases, the parameters, the distinction between a declaration
 * and a mention, and the lookup. They did not cover the same contexts: the lookup
 * knew `list` and `set` but threw on the three entity forms, and the parameter
 * reader knew only the six declarations.
 */
class FunconTest {
    @Test
    fun declarationNameTest() {
        assertEquals("f", funconOf(node<FunconDefinitionContext>("Funcon f : value-types")).name)
        assertEquals("t", funconOf(node<TypeDefinitionContext>("Type t <: value-types")).name)
        assertEquals("d", funconOf(node<DatatypeDefinitionContext>("Datatype d ::= 1")).name)
        assertEquals("e", funconOf(node<ControlEntityDefinitionContext>("Entity _ -- e ---> _")).name)
        assertEquals(
            "used-atom-set",
            funconOf(
                node<MutableEntityDefinitionContext>(
                    "Entity < _ , used-atom-set(_:sets(atoms)) > ---> < _ , used-atom-set(_:sets(atoms)) >"
                )
            ).name
        )
        assertEquals(
            "given-value",
            funconOf(node<ContextualEntityDefinitionContext>("Entity given-value(_:values?) |- _ ---> _")).name
        )
    }

    @Test
    fun declarationAliasesTest() {
        assertEquals(
            listOf("q", "r"),
            funconOf(node<FunconDefinitionContext>("Funcon f : value-types Alias q = f Alias r = f")).aliases
        )
    }

    @Test
    fun funconRewriteWithoutReturnTypeTest() {
        // A funcon written as `F ~> term` is that term, and the return type is
        // optional there. The stable corpus always writes both, so a grammar that
        // made `:` mandatory parsed the stable corpus and rejected this. Unstable
        // uses the shorter form (Synchronising.cbs declares `sync-create` that way).
        val ctx = node<FunconDefinitionContext>("Funcon f ~> g")
        assertNull(ctx.returnType)
        assertEquals("g", ctx.rewritesTo.text)
    }

    @Test
    fun auxiliaryTypeModifierTest() {
        // `Auxiliary` is a modifier of a Type as well as of a Funcon, and both
        // appear in Unstable-Funcons-beta. Only `Built-in` was accepted before,
        // so `Auxiliary Type rendezvous-waits ~> nats` was a syntax error.
        val ctx = node<TypeDefinitionContext>("Auxiliary Type t ~> maps(a, b)")
        assertEquals("Auxiliary", ctx.modifier.text)
        assertEquals("maps(a,b)", ctx.definition.text)
    }

    @Test
    fun typeStillRejectsReturnTypeColonTest() {
        // The return type is a Funcon's, not a Type's. Making `COLON` optional
        // above must not make `Type t : x` parse, or `:` would silently vanish
        // for every declaration that follows it.
        assertEquals(1, syntaxErrors("Type t : maps(a, b)"))
    }

    @Test
    fun mutableEntityParamsTest() {
        // A mutable entity writes its parameters on the left of the transition, and
        // nothing else: `params` is empty for it, so reading that instead drops them
        // and the generated entity takes no arguments.
        val funcon = funconOf(
            node<MutableEntityDefinitionContext>(
                "Entity < _ , used-atom-set(_:sets(atoms)) > ---> < _ , used-atom-set(_:sets(atoms)) >"
            )
        )
        assertEquals(listOf("_"), funcon.params.map { it.value })
    }

    @Test
    fun mentionParamsTest() {
        // A mention is not a declaration, so it has no aliases to read, and the
        // arguments it was written with stand in for the declared parameters.
        val ctx = node<FunconDefinitionContext>("Funcon f : values Rule list-append(X, Y) ---> [X, Y]")
        val pattern = (ctx.ruleDefinition(0).conclusion as TransitionPremiseContext).lhs
        val funcon = funconOf(pattern)

        assertEquals("list-append", funcon.name)
        assertEquals(emptyList<String>(), funcon.aliases)
        assertEquals(listOf("X", "Y"), funcon.params.map { it.value })
    }

    @Test
    fun listAndSetNameTest() {
        // `list` and `set` are written `[...]` and `{...}` and are not names the
        // grammar emits, so the dispatch has to supply them.
        val list = node<FunconDefinitionContext>("Funcon f : [1, 2]").returnType
        val set = node<FunconDefinitionContext>("Funcon f : {1, 2}").returnType

        assertEquals("list", funconOf(list).name)
        assertEquals("set", funconOf(set).name)
    }
}
