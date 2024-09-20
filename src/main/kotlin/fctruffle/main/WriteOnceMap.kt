package fctruffle.main

class WriteOnceMap<K, V> : MutableMap<K, V> by mutableMapOf() {
    override fun put(key: K, value: V): V? {
        if (this.containsKey(key)) {
            throw IllegalStateException("Attempt to overwrite immutable entity $key")
        }
        return (this as MutableMap<K, V>).put(key, value)
    }

    override fun get(key: K): V? {
        return if (this.containsKey(key)) {
            (this as MutableMap<K, V>)[key]
        } else {
            null  // Return null if the key is not present
        }
    }
}