package main

const val ENTITY = "FCTEntity"
const val CONTEXTUALENTITY = "FCTContextualEntity"
const val MUTABLEENTITY = "FCTMutableEntity"
const val CONTROLENTITY = "FCTControlEntity"
const val FCTNODE = "FCTNode"

enum class EntityType {
    CONTEXTUAL,
    CONTROL,
    MUTABLE
}