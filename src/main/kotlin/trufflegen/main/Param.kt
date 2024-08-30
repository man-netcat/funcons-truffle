package trufflegen.main

class Param(private val index: Int, val value: Value, val type: ParamType) {
    val string: String
        get() = "p$index"
}