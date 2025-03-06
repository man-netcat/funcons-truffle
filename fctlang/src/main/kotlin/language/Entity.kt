package language

abstract class Entity() {
    abstract val value: Any?
}

abstract class ContextualEntity(override val value: FCTNode) : Entity()
abstract class MutableEntity(override val value: FCTNode) : Entity()
abstract class InputEntity(override vararg val value: FCTNode) : Entity()
abstract class OutputEntity(override vararg val value: FCTNode) : Entity()
abstract class ControlEntity(override val value: FCTNode?) : Entity()
