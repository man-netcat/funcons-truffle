package main

import main.exceptions.DetailedException
import main.objects.Object
import main.objects.TypeObject

/**
 * The corpus as code generation sees it: every object by name and by alias, and
 * the subtype index.
 *
 * CBS has no module system: a rule may name anything declared in any of the 43
 * files, and a funcon has to be generated whether or not the file that declares it
 * was read first. So the set of objects is shared rather than owned by one file,
 * and the only thing that is genuinely per-file is which of them to write out.
 *
 * Read-only, so a stage that only generates code cannot add to the corpus it is
 * reading. [RegistryBuilder] is the one thing that can.
 */
interface Registry {
    /** Every object once, in the order the files declared them. */
    val objects: List<Object>

    operator fun get(name: String): Object?

    /**
     * The object named `name`.
     *
     * @throws DetailedException with the names that do exist, since a name that
     * does not resolve is a missing declaration rather than a null to propagate.
     */
    fun require(name: String): Object

    fun isBuiltin(name: String): Boolean

    /**
     * The types CBS declares a subtype of `typeName` with `Type N ~> T`.
     *
     * CBS reads `~>` as "N is a T", so `isIn<T>()` has to accept N's values. The
     * index is built as types are registered, since every type is registered
     * before any code is generated.
     */
    fun subtypesOf(typeName: String): List<TypeObject>
}

/**
 * Builds a [Registry] while a file's definitions are read.
 *
 * @param builtins names the generator overrides rather than generates, because the
 * corpus declares them but Truffle implements them differently: `print` reads a
 * stream, `choice` picks leftmost rather than at random, `atomic` cannot be
 * expressed as a reduction. They are still in the registry, so a rule that names
 * one still resolves; what changes is that no node class is written for it.
 */
class RegistryBuilder(private val builtins: Set<String>) : Registry {
    private val byName = mutableMapOf<String, Object>()
    private val bySupertype = mutableMapOf<String, MutableList<TypeObject>>()

    /**
     * Every object once, in the order the files declared them.
     *
     * An object is looked up by each of its aliases but exists once, so this is
     * kept alongside [byName] rather than derived from it: `byName.values` holds
     * one entry per alias and would have to be de-duplicated on every read.
     */
    private val registered = mutableListOf<Object>()

    /** Registers an object under its own name and under each of its aliases. */
    fun add(obj: Object) {
        byName[obj.name] = obj
        obj.aliases.forEach { byName[it] = obj }
        if (obj is TypeObject && obj.supertypeName != null) {
            bySupertype.getOrPut(obj.supertypeName!!) { mutableListOf() }.add(obj)
        }
        registered.add(obj)
    }

    override val objects: List<Object> get() = registered

    override operator fun get(name: String): Object? = byName[name]

    override fun require(name: String): Object =
        byName[name] ?: throw DetailedException("$name is not declared in the corpus")

    override fun isBuiltin(name: String): Boolean = name in builtins

    override fun subtypesOf(typeName: String): List<TypeObject> = bySupertype[typeName].orEmpty()
}
