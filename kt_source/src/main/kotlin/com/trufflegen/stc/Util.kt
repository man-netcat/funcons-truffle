package com.trufflegen.stc

import kotlin.IllegalArgumentException
import kotlin.RuntimeException

class Util {
    companion object {
        /**
         * Returns a new array that is a subsequence of the given array. The subsequence starts at
         * the specified start index and ends at the position array.size - endIndexOffset.
         *
         * @param array The array to slice.
         * @param startIndex The start index of the slice (inclusive).
         * @param endIndexOffset The number of elements to exclude from the end of the array
         * (default is 0).
         * @return A new array containing the specified subsequence.
         * @throws IllegalArgumentException if startIndex or endIndexOffset are out of bounds.
         */
        fun <T> slice(array: Array<T>, startIndex: Int, endIndexOffset: Int = 0): Array<T> {
            if (startIndex < 0 || startIndex > array.size) {
                throw IllegalArgumentException("Start index is out of bounds.")
            }
            if (endIndexOffset < 0 || endIndexOffset > array.size) {
                throw IllegalArgumentException("End index offset is out of bounds.")
            }
            return array.sliceArray(startIndex until array.size - endIndexOffset)
        }

        /**
         * Throws a RuntimeException.
         *
         * @throws RuntimeException always.
         */
        fun fail(): Nothing {
            throw RuntimeException()
        }
    }
}
