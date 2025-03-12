package main

const val ENTITY = "Entity"
const val CONTEXTUALENTITY = "ContextualEntity"
const val MUTABLEENTITY = "MutableEntity"
const val CONTROLENTITY = "ControlEntity"
const val INPUTENTITY = "InputEntity"
const val OUTPUTENTITY = "OutputEntity"
const val TERMNODE = "TermNode"

enum class EntityType {
    CONTEXTUAL,
    CONTROL,
    MUTABLE,
    INPUT,
    OUTPUT
}