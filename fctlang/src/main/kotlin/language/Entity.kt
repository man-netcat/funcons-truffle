package language

abstract class Entity {
    abstract val value: TermNode?
}

abstract class ContextualEntity(override val value: TermNode) : Entity()
abstract class MutableEntity(override val value: TermNode) : Entity()
abstract class InputEntity(override val value: SequenceNode) : Entity()
abstract class OutputEntity(override val value: SequenceNode) : Entity()
abstract class ControlEntity(override val value: TermNode?) : Entity()
