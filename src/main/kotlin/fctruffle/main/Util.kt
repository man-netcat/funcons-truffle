package fctruffle.main

fun <T> slice(
    array: Array<T>, startIndex: Int, endIndexOffset: Int = 0
): Array<T> {
    require(startIndex in 0..array.size) { "Start index is out of bounds." }

    val endIndex = array.size - endIndexOffset
    require(endIndexOffset >= 0 && endIndex <= array.size && startIndex <= endIndex) {
        "Invalid end index offset."
    }

    return array.sliceArray(startIndex until endIndex)
}

fun fail(): Nothing {
    throw RuntimeException()
}
