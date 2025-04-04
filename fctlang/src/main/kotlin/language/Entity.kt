package language

abstract class Entity(val value: SequenceNode) {
    fun emptyValue() = value.isEmpty()

    operator fun get(index: Int) = value
}

abstract class ContextualEntity(value: SequenceNode = SequenceNode()) : Entity(value)
abstract class MutableEntity(value: SequenceNode = SequenceNode()) : Entity(value)
abstract class InputEntity(value: SequenceNode = SequenceNode()) : Entity(value)
abstract class OutputEntity(value: SequenceNode = SequenceNode()) : Entity(value)
abstract class ControlEntity(value: SequenceNode = SequenceNode()) : Entity(value)
