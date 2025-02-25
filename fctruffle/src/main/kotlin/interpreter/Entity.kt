package interpreter

abstract class Entity(open vararg val value: FCTNode?)

abstract class ContextualEntity(value: FCTNode?) : Entity(value)
abstract class ControlEntity(value: FCTNode?) : Entity(value)
abstract class MutableEntity(value: FCTNode?) : Entity(value)
abstract class InputEntity(vararg value: FCTNode) : Entity(*value)
abstract class OutputEntity(vararg value: FCTNode) : Entity(*value)
