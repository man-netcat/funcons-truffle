package trufflegen.main

class Param(val index: Int, val value: Value, val type: ParamType) {
    val string: String
        get() {
            return "p$index"
        }
}