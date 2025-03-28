package language

abstract class Entity(val value: SequenceNode) {
    fun emptyValue() = value.isEmpty()
}

abstract class ContextualEntity(value: SequenceNode = SequenceNode()) : Entity(value)
abstract class MutableEntity(value: SequenceNode = SequenceNode()) : Entity(value)
abstract class InputEntity(value: SequenceNode = SequenceNode()) : Entity(value)
abstract class OutputEntity(value: SequenceNode = SequenceNode()) : Entity(value)
abstract class ControlEntity(value: SequenceNode = SequenceNode()) : Entity(value)
