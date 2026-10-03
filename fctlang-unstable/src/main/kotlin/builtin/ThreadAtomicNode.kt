package builtin

import com.oracle.truffle.api.frame.VirtualFrame

/**
 * `thread-atomic(X)` computes `X` without letting another thread be scheduled in
 * between, and hands back the value it computes.
 *
 * The declaration in `Multithreading.cbs` writes this with `--->1 ; --->2`, which
 * composes two transitions: `X` takes a step, and then `thread-atomic(X')` takes a
 * step. That is the step-composition notation the corpus flags as unimplemented --
 * "when the CBS notation `--->1 ; --->2` for composing transitions has been
 * implemented by the interpreter generation tools, uses of `thread-atomic(X)` are
 * to be replaced by `atomic(X)`". Generated as written, the composition compiles to
 * a rule that steps `thread-atomic(X')`, so each step of the body recursed once
 * more and a body of any depth overflowed the stack.
 *
 * So this is `atomic`, which `AtomicNode` already implements: step the body, and
 * keep stepping while the `yielded` control entity is clear. `atomic` lets an
 * enclosing atomic computation resume at a yield and `thread-atomic` must not, so
 * this aborts instead of interleaving, which is the conservative reading of
 * "no preemption" rather than a resumption point.
 */
class ThreadAtomicNode(@Child var p0: TermNode) : TermNode() {
    override fun reduceRules(frame: VirtualFrame): TermNode = AtomicNode(p0)
}