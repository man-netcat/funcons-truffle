package trufflegen.main

enum class TypeCategory {
    PLUS, // One or More
    STAR, // Zero or More
    QMARK, // Zero or One
    POWN, // N values
    SINGLE, // One value
}