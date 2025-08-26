package language

import builtin.SequenceNode
import builtin.TermNode
import com.oracle.truffle.api.frame.VirtualFrame

fun getEntities(frame: VirtualFrame): MutableMap<String, TermNode> {
    return frame.getObject(FCTLanguage.entitiesFrameslot) as? MutableMap<String, TermNode>
        ?: mutableMapOf<String, TermNode>().also {
            frame.setObject(FCTLanguage.entitiesFrameslot, it)
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

fun getEntity(frame: VirtualFrame, key: String): TermNode {
    return getEntities(frame)[key] ?: SequenceNode()
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