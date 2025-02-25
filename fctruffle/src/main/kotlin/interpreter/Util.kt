package interpreter

fun <T> Array<T>.sliceFrom(startIndex: Int, endIndexOffset: Int = 0): Array<T> {
    require(startIndex in 0..size) { "Start index is out of bounds." }

    val endIndex = size - endIndexOffset
    require(endIndexOffset >= 0 && endIndex <= size && startIndex <= endIndex) {
        "Invalid end index offset."
    }

    return sliceArray(startIndex until endIndex)
}

fun abort(): Nothing {
    throw RuntimeException()
}
