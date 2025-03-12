package language

abstract class Entity() {
    abstract val value: Any?
}

abstract class ContextualEntity(override val value: TermNode) : Entity()
abstract class MutableEntity(override val value: TermNode) : Entity()
abstract class InputEntity(override vararg val value: TermNode) : Entity()
abstract class OutputEntity(override vararg val value: TermNode) : Entity()
abstract class ControlEntity(override val value: TermNode?) : Entity()
