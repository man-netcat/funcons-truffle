package language

import com.oracle.truffle.api.interop.InteropLibrary
import com.oracle.truffle.api.interop.InvalidArrayIndexException
import com.oracle.truffle.api.interop.TruffleObject
import com.oracle.truffle.api.interop.UnknownIdentifierException
import com.oracle.truffle.api.library.ExportLibrary
import com.oracle.truffle.api.library.ExportMessage

@ExportLibrary(InteropLibrary::class)
class ResultArray(
    val value: Array<String>,
    val expectations: TestExpectations = TestExpectations.NONE,
    val comparison: ComparisonReport = ComparisonReport.NOT_CHECKED
) : TruffleObject {

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

    @ExportMessage
    fun hasMembers(): Boolean = true

    @ExportMessage
    fun getMembers(includeInternal: Boolean): Boolean = true

    @ExportMessage
    fun isMemberReadable(member: String): Boolean = when (member) {
        "checked", "passing", "failureReport" -> true
        else -> false
    }

    @ExportMessage
    fun readMember(member: String): Any = when (member) {
        "checked" -> comparison.hasExpectations
        "passing" -> comparison.passed
        "failureReport" -> comparison.describe()
        else -> throw UnknownIdentifierException.create(member)
    }
}
