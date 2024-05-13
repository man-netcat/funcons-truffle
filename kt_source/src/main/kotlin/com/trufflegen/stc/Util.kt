package com.trufflegen.stc

class Util {
    companion object {
        fun <T> slice(array: Array<T>, startIndex: Int): Array<T> {
            return array.sliceArray(startIndex until array.size)
        }
    }
}