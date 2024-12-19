fun mapArguments(
    numParams: Int, variadicParamIndex: Int, numParamsAfterVariadic: Int, numArguments: Int, arrayLikeArgIndex: Int
): String {
    var curParamIndex = 0
    var curArgIndex = 0
    var curVariadicRelativeIndex = 0
    val stringList = mutableListOf<String>()

    when {
        variadicParamIndex >= numParams -> throw IndexOutOfBoundsException("variadicParameterIndex")
        numParamsAfterVariadic >= numParams -> throw IndexOutOfBoundsException("numParametersAfterVariadic")
        arrayLikeArgIndex >= numArguments -> throw IndexOutOfBoundsException("arrayLikeParameterIndex")
    }

    while (curArgIndex < numArguments || curParamIndex < numParams) {
        println("curParamIndex: $curParamIndex, curArgIndex: $curArgIndex")
        if (curArgIndex != arrayLikeArgIndex) {
            if (curParamIndex < variadicParamIndex) {
                stringList.add("p$curParamIndex")
                curArgIndex++
                curParamIndex++
            } else if (curParamIndex == variadicParamIndex) {
                do {
                    stringList.add("p$curParamIndex[$curVariadicRelativeIndex]")
                    curVariadicRelativeIndex++
                    curArgIndex++
                } while (curArgIndex < numParams - numParamsAfterVariadic && curArgIndex != arrayLikeArgIndex)
            } else if (curParamIndex > variadicParamIndex) {
                stringList.add("p$curParamIndex=p$curParamIndex")
                curArgIndex++
                curParamIndex++
            }
        } else {
            if (curParamIndex < variadicParamIndex) {
                curParamIndex++
            } else if (curParamIndex == variadicParamIndex) {
                if (curVariadicRelativeIndex == 0) {
                    stringList.add("*p$curParamIndex")
                } else {
                    stringList.add("*slice(p$curParamIndex, $curVariadicRelativeIndex)")
                }
                curArgIndex++
            } else if (curParamIndex > variadicParamIndex) {
                do {
                    stringList.add("p$curParamIndex=p$curParamIndex")
                    curVariadicRelativeIndex++
                    curParamIndex++
                } while (curParamIndex < numParams)
            }
        }
    }
    return stringList.joinToString()
}

fun main() {
    // params -> vararg val p0, val p1
    // args -> X, Y*
    println(mapArguments(2, 0, 1, 2, 1))
    // Should print p0[0], slice(p0, 1), p1=p1
}