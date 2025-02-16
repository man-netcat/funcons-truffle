package interpreter

class Sequence<T>(private vararg val values: T) {
    fun toArray(): Array<out T> {
        return values
    }
}
