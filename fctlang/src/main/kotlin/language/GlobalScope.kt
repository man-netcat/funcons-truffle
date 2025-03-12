package language

class GlobalScope {
    private val variables: MutableMap<String?, Entity?> = HashMap()

    fun putEntity(name: String?, value: Entity?): Boolean {
        val existingValue = this.variables.put(name, value)
        return existingValue == null
    }

    fun update(name: String?, value: Entity?): Boolean {
        val existingValue = this.variables.computeIfPresent(name) { k, v -> value }
        return existingValue != null
    }

    // Rename `get` to avoid conflict with property accessors
    fun getEntity(name: String?): Entity? {
        return this.variables[name]
    }
}
