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

    fun getEntity(name: String?): Entity? {
        return this.variables[name]
    }

    override fun toString(): String {
        return "{\n" + variables.map { (name, entity) -> "    $name: ${entity?.value?.value}" }
            .joinToString("\n") + "\n}"
    }

    fun isEmpty(): Boolean = variables.isEmpty()
}
