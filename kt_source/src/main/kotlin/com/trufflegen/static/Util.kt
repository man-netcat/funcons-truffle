package com.trufflegen.static

fun <T> slice(array: Array<T>, startIndex: Int): Array<T> {
    return array.sliceArray(startIndex until array.size)
}