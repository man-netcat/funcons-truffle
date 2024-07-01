package com.trufflegen.stc

class Util {
    companion object {
        fun <T> slice(array: Array<T>, startIndex: Int, nFinal: Int = 0): Array<T> {
            return array.sliceArray(startIndex until array.size - nFinal)
        }
    }
}