package language

abstract class Entity() {
    abstract val value: Array<out FCTNode>
}

abstract class ContextualEntity(override vararg val value: FCTNode) : Entity()
abstract class ControlEntity(override vararg val value: FCTNode) : Entity()
abstract class MutableEntity(override vararg val value: FCTNode) : Entity()
abstract class InputEntity(override vararg val value: FCTNode) : Entity()
abstract class OutputEntity(override vararg val value: FCTNode) : Entity()
