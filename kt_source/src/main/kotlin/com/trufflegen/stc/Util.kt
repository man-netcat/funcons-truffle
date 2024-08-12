package com.trufflegen.stc

import kotlin.IllegalArgumentException
import kotlin.RuntimeException

class Util {
    companion object {
        fun <T> slice(array: Array<T>, startIndex: Int, endIndexOffset: Int = 0): Array<T> {
            if (startIndex < 0 || startIndex > array.size) {
                throw IllegalArgumentException("Start index is out of bounds.")
            }

            val endIndex = array.size - endIndexOffset
            if (endIndexOffset < 0 || endIndex > array.size || startIndex > endIndex) {
                throw IllegalArgumentException("Invalid end index offset.")
            }

            return array.sliceArray(startIndex until endIndex)
        }

        fun fail(): Nothing {
            throw RuntimeException()
        }
    }
}
