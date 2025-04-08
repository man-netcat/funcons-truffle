package language

import builtin.TermNode

class GlobalScope {
    private val variables: MutableMap<String?, TermNode?> = HashMap()

    fun putEntity(name: String, value: TermNode?): Boolean {
        val existingValue = this.variables.put(name, value)
        return existingValue == null
    }

    fun getEntity(name: String): TermNode? {
        return this.variables[name]
    }

    override fun toString(): String {
        return "{\n" + variables.map { (name, entity) -> "    $name: ${entity?.value}" }
            .joinToString("\n") + "\n}"
    }

    fun isEmpty(): Boolean = variables.isEmpty()
}
