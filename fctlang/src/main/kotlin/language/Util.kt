package language

fun <T> Array<T>.sliceFrom(startIndex: Int, endIndexOffset: Int = 0): Array<T> {
    require(startIndex in 0..size) { "Start index is out of bounds." }

    val endIndex = size - endIndexOffset
    require(endIndexOffset >= 0 && endIndex <= size && startIndex <= endIndex) {
        "Invalid end index offset."
    }

    return sliceArray(startIndex until endIndex)
}

fun <T> Array<T>.sliceUntil(endIndex: Int, startIndexOffset: Int = 0): Array<T> {
    require(startIndexOffset in 0..size) { "Start index is out of bounds." }
    require(endIndex in 0..size) { "End index is out of bounds." }
    require(startIndexOffset <= endIndex) { "Start index must be less than or equal to end index." }
    return sliceArray(startIndexOffset until endIndex)
}

inline fun <T> Array<T>.indexOfFirstOrLast(reverse: Boolean, predicate: (T) -> Boolean): Int {
    return if (reverse) indexOfLast(predicate) else indexOfFirst(predicate)
}