package trufflegen.main

enum class TypeCategory {
    PLUS, // One or More
    STAR, // Zero or More
    QMARK, // Zero or One
    SINGLE, // One value
}