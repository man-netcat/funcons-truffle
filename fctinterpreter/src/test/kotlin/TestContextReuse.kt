package interpreter

import builtin.SequenceNode
import builtin.ValueNodeFactory.intNode
import generated.isInNullType
import interpreter.FCTInterpreter.createContext
import interpreter.FCTInterpreter.evalFile
import org.graalvm.polyglot.Context
import org.graalvm.polyglot.Value
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertSame
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import java.nio.file.Path
import java.nio.file.Paths
import kotlin.io.path.extension

/**
 * Evaluating the same source twice in one `Context` has to give the same answer
 * twice.
 *
 * It is not automatic. Polyglot caches the `CallTarget` by source name and content,
 * so the second evaluation re-runs the AST the first one built instead of parsing
 * again. That is only sound if a reduction step leaves the AST alone, and two node
 * classes used to break it: `SequenceNode.popFirst` reassigned `@Children` when a
 * `read` consumed its input, and `AtomicNode` held its two intermediate results in
 * `@Child` fields. Every other test evaluates a program in a fresh `Context`, so
 * nothing else covered it.
 */
class TestContextReuse {

    @Test
    fun sequencePopFirstTest() {
        val head = intNode(1)
        val sequence = SequenceNode(head, intNode(2))

        assertSame(head, sequence.popFirst())
        // The point of the test: a second read still sees the head, and the sequence
        // still has both elements. `popFirst` used to consume it, so a term that had
        // been reduced once could not be reduced again.
        assertSame(head, sequence.popFirst())
        assertEquals(2, sequence.size)
    }

    @Test
    fun emptySequencePopFirstTest() {
        assertTrue(SequenceNode().popFirst().isInNullType())
    }

    @Test
    fun programRerunTest() {
        val programs = configFiles()
        assertTrue(programs.isNotEmpty(), "no .config programs found under $CORPUS")

        // What a program is supposed to produce: one run, in a Context of its own.
        val expected = programs.associateWith { program ->
            createContext().use { context -> run(context, program) }
        }

        // The same program again under the same source name, sharing one Context, so
        // the second evaluation is served from the cached CallTarget.
        val rerun = createContext().use { context ->
            programs.associateWith { program ->
                run(context, program)
                run(context, program)
            }
        }

        val diverged = programs.filter { expected.getValue(it) != rerun.getValue(it) }
        assertEquals(
            emptyList<String>(),
            diverged.map { it.fileName.toString() },
            "these programs reduce differently the second time, so their AST is not reusable",
        )
    }

    private fun run(context: Context, program: Path): String =
        try {
            val result = evalFile(context, program)
            (0 until result.arraySize).joinToString("|") { index -> field(result, index) }
        } catch (failure: Exception) {
            // The corpus has stuck programs, and the suite records them as known
            // divergences. Both runs have to fail the same way for the comparison to
            // mean anything.
            "failed: ${failure::class.simpleName}: ${failure.message?.lineSequence()?.first()}"
        }

    private fun field(result: Value, index: Long): String = result.getArrayElement(index).asString()

    private fun configFiles(): List<Path> =
        CORPUS.toFile().walkTopDown()
            .filter { it.isFile && it.extension == "config" }
            .map { it.toPath() }
            .sorted()
            .toList()

    private companion object {
        val CORPUS: Path = Paths.get("../CBS-beta/Funcons-beta")
    }
}
