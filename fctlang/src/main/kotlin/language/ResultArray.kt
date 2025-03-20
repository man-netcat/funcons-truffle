package language

import com.oracle.truffle.api.interop.InteropLibrary
import com.oracle.truffle.api.interop.InvalidArrayIndexException
import com.oracle.truffle.api.interop.TruffleObject
import com.oracle.truffle.api.library.ExportLibrary
import com.oracle.truffle.api.library.ExportMessage

@ExportLibrary(InteropLibrary::class)
class ResultArray(val value: Array<String>) : TruffleObject {

    @ExportMessage
    fun hasArrayElements(): Boolean = true

    @ExportMessage
    fun isArrayElementReadable(index: Long): Boolean = index >= 0 && index < value.size

    @ExportMessage
    fun getArraySize(): Int = value.size

    @ExportMessage
    fun readArrayElement(index: Long): Any {
        if (!isArrayElementReadable(index)) {
            throw InvalidArrayIndexException.create(index)
        }
        return value[index.toInt()]
    }
}
