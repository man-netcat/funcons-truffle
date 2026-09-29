package main.dataclasses

import cbs.CBSParser.*

/**
 * What a type expression says that changes the code generated for it.
 *
 * A type expression in CBS carries a lot the generator does not act on: `X*`,
 * `X+`, `X?` and `X^64` only tell the reader which kind of argument this is, and
 * `A | B` and `~A` are conditions the caller builds from the expression itself
 * rather than from anything summarised here. Only the three properties below
 * reach the generated code, so they are all this class holds.
 *
 * @property isSequence the parameter is one or more arguments collected into a
 *   `SequenceNode`, which changes how the generated constructor and accessor are
 *   written and how the rewrite addresses them.
 * @property isVararg the expression is a variadic marker, i.e. `X+` where `X`
 *   is not a parameter, which becomes a `vararg` constructor argument.
 * @property computes the argument is passed by need rather than by value, so the
 *   generated getter takes a `VirtualFrame` and the node has to be reducible
 *   before it is read.
 */
class Type(private val expr: ExprContext?, val isParam: Boolean = false) {

    var computes = false
        private set

    var isSequence = false
        private set

    var isVararg = false
        private set

    /**
     * `inner` of `X*` when `X` is a computation, e.g. the `abstraction(...)` in
     * `abstraction(...)+`. A computation behind a repetition is the one case where
     * the argument is both variadic and passed by need.
     */
    private fun handleUnaryComputesExpression(inner: UnaryComputesExpressionContext) {
        computes = true
        if (inner.operand is SuffixExpressionContext) isVararg = true
    }

    init {
        if (expr != null) {
            when (expr) {
                // A single-argument funcon call whose argument is a computation,
                // i.e. `atomic(...)` as a type. The grammar's left recursion makes
                // this the only place a call is known to take exactly one
                // argument, so a longer call is not recognised here.
                is FunconExpressionContext -> {
                    val args = expr.args()
                    if (args is SingleArgsContext && args.expr() is UnaryComputesExpressionContext) {
                        computes = true
                    }
                }

                is SuffixExpressionContext -> {
                    if (isParam) isSequence = true else isVararg = true
                    val operand = expr.operand
                    if (operand is NestedExpressionContext) {
                        val nested = operand.expr()
                        if (nested is UnaryComputesExpressionContext) handleUnaryComputesExpression(nested)
                    }
                }

                is PowerExpressionContext -> {
                    if (isParam) isSequence = true else isVararg = true
                    val operand = expr.operand
                    if (operand is NestedExpressionContext) {
                        val nested = operand.expr()
                        if (nested is UnaryComputesExpressionContext) handleUnaryComputesExpression(nested)
                    }
                }

                is UnaryComputesExpressionContext -> handleUnaryComputesExpression(expr)

                // `A + B` and the other infix computations, i.e. `length(A)`.
                is BinaryComputesExpressionContext -> computes = true
            }
            if (isSequence && !isParam) throw IllegalStateException("A non-parameter type cannot be vararg")
        }
    }

    override fun toString(): String = expr?.text ?: "null"
}
