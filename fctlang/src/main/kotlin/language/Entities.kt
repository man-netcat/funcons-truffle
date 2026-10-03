package language

import builtin.MapNode
import builtin.SequenceNode
import builtin.TermNode
import com.oracle.truffle.api.frame.VirtualFrame

private const val ENVIRONMENT = "environment"

@Suppress("UNCHECKED_CAST")
fun getEntities(frame: VirtualFrame): MutableMap<String, TermNode> {
    return frame.getObject(FCTLanguage.ENTITIES_SLOT) as? MutableMap<String, TermNode>
        ?: mutableMapOf<String, TermNode>().also {
            frame.setObject(FCTLanguage.ENTITIES_SLOT, it)
        }
}

fun printEntities(frame: VirtualFrame) {
    val entities = getEntities(frame)
    if (entities.isNotEmpty()) {
        val str = "Entities: {\n" + entities.map { (name, entity) -> "    $name: $entity" }
            .joinToString("\n") + "\n}"
        println(str)
    } else println("Entities: {}")
}

/*
  The environment is the one map-typed entity, so an unset scope is the empty environment
  rather than an empty sequence. Handing back SequenceNode() made `scope(empty, X)` fail the
  `isInEnvironments()` test, which is `isInMaps()`, so `closure` got stuck whenever no
  identifier was in scope. The reference evaluates `closure(12)` to
  `abstraction(closed(scope("...",12)))` and `enact(closure 12)` to 12.
*/
fun getEntity(frame: VirtualFrame, key: String): TermNode {
    return getEntities(frame)[key] ?: if (key == ENVIRONMENT) MapNode() else SequenceNode()
}

fun putEntity(frame: VirtualFrame, key: String, value: TermNode) {
    getEntities(frame)[key] = value
}

fun appendEntity(frame: VirtualFrame, key: String, entity: TermNode) {
    val existing = getEntity(frame, key) as? SequenceNode ?: SequenceNode()
    val newSequence = existing.append(entity.toSequence())
    putEntity(frame, key, newSequence)
}

fun restoreEntities(frame: VirtualFrame, snapshot: Map<String, TermNode>) {
    val entities = getEntities(frame)
    entities.clear()
    entities.putAll(snapshot)
}

fun checkEntitySnapshot(frame: VirtualFrame, snapshot: Map<String, TermNode>): Boolean {
    val current = getEntities(frame)
    if (snapshot.size != current.size) return false

    for ((key, snapValue) in snapshot) {
        val currValue = current[key] ?: return false
        if (snapValue != currValue) return false
    }
    return true
}

/*
  Which entities a refocus step is not allowed to move. This mirrors the entity
  taxonomy the CBS libraries declare: control entities are the ones raised with
  `-->`, output entities are the `!`-declared ones, and input entities are read
  back through `?-`.

  Mutable entities are the one group deliberately left out: `store` and
  `used-atom-set` change what a later step can see, but they are only ever read by
  the funcon that wrote them, so a run of steps that only updates them is safe to
  refocus over.

  Inherited entities are the other judgement call, and the reference does not guard
  them. They still have to be here, because `ScopeNode`, `GiveNode`, `ClosedNode` and
  `NoGivenNode` step a body once and put the entity back -- the scoping idiom that
  `withInhTerm` and `withInh` compile to. Refocussing across that swap reads
  `given-value` or `environment` under a different scope than the step was meant to
  run in: `recursive` is the case that shows it, where `re-close` shares one eagerly
  reduced `bind-to-forward-links` between `scope` and `set-forward-links`, and
  refocussing past the swap gives `bind-value(given, ...)` a different `given` than the
  single step would have. Guarding them stops each run at the swap, which is exactly
  what the idiom wants, and MiniJava goes back to printing.
 */
private val REFOCUS_ENTITIES = arrayOf(
    "abrupted", "plug-signal", "control-signal", "yielded", "postponing",
    "standard-out",
    "standard-in",
    "given-value", "environment",
)

/*
  The boundary a run of refocussing steps stops at.

  funcons-tools' `if_violates_refocus` ends refocussing when the step raised a
  control signal, wrote to an output entity, or left an input entity unread, and
  `handle-abrupt` is the case that makes the rule necessary: it reads `abrupted`
  after stepping its body, so a run that stepped straight past the `abrupt` that
  raised the signal would never see it. The comparable guard in the generator is
  the second premise of every step, which refocuses on the result through the
  result's own stepper.

  Comparing by identity is deliberate. Every write goes through `putEntity` or
  `appendEntity` and stores a node built for that write, so a run of steps that
  never wrote these entities compares equal to itself. `checkEntitySnapshot`
  compares the whole entity map instead, which also stops on `store` and
  `used-atom-set`; that is conservative rather than wrong, but it stops refocussing
  almost immediately in the I/O-heavy programs, where it would matter most.
 */
class RefocusBoundary private constructor(private val before: Array<TermNode?>) {
    fun steppedOverBoundary(frame: VirtualFrame): Boolean {
        val entities = getEntities(frame)
        for (index in before.indices) {
            if (entities[REFOCUS_ENTITIES[index]] !== before[index]) return true
        }
        return false
    }

    companion object {
        fun take(frame: VirtualFrame): RefocusBoundary {
            val entities = getEntities(frame)
            return RefocusBoundary(Array(REFOCUS_ENTITIES.size) { entities[REFOCUS_ENTITIES[it]] })
        }
    }
}

/*
  Refocussing is on unless the config says otherwise, which is the funcons-tools
  default. Only `refocus` is read; the other options the unstable configs carry
  (`seed`, `non-deterministic`, `display-mutable-entity`) are still parsed and
  still ignored.
 */
fun refocusEnabled(frame: VirtualFrame): Boolean =
    if (frame.isBoolean(FCTLanguage.REFOCUS_SLOT)) frame.getBoolean(FCTLanguage.REFOCUS_SLOT) else true