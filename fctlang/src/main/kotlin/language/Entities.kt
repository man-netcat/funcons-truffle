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