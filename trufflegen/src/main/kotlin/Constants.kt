package main

const val ENTITY = "Entity"
const val CONTEXTUALENTITY = "ContextualEntity"
const val MUTABLEENTITY = "MutableEntity"
const val CONTROLENTITY = "ControlEntity"
const val INPUTENTITY = "InputEntity"
const val OUTPUTENTITY = "OutputEntity"
const val TERMNODE = "TermNode"
const val SEQUENCE = "SequenceNode"

enum class EntityType {
    CONTEXTUAL,
    CONTROL,
    MUTABLE,
    INPUT,
    OUTPUT
}