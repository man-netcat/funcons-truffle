package dependencies

object Vars {
    const val GENERATEDPATHSTR = "../fctlang/src/main/kotlin/generated"
    const val CBSFILEPATH = "../../CBS-beta/Funcons-beta/"
    val configFiles = listOf<String>(
//        "Values/Composite/Classes/tests/class.config",
//        "Computations/Normal/Binding/tests/accumulate.config",
//        "Computations/Normal/Flowing/tests/atomic.config",
//        "Computations/Normal/Binding/tests/bind-recursively.config",
        "Computations/Normal/Binding/tests/bind-value.config",
        "Computations/Normal/Binding/tests/closed.config",
        "Computations/Normal/Binding/tests/environments.config",
        "Computations/Normal/Flowing/tests/choice.config",
        "Computations/Normal/Flowing/tests/do-while.config",
        "Computations/Normal/Flowing/tests/effect.config",
        "Computations/Normal/Flowing/tests/if-true-else.config",
        "Computations/Normal/Flowing/tests/interleave.config",
        "Computations/Normal/Flowing/tests/left-to-right.config",
        "Computations/Normal/Flowing/tests/right-to-left.config",
        "Computations/Normal/Flowing/tests/sequential.config",
        "Computations/Normal/Flowing/tests/while.config",
        "Computations/Normal/Giving/tests/fold-left.config", // Maybe correct?
        "Computations/Normal/Giving/tests/give.config",
        "Computations/Normal/Giving/tests/no-given.config",
        "Computations/Normal/Interacting/tests/print-1.config",
        "Computations/Normal/Interacting/tests/print-2.config",
        "Computations/Normal/Interacting/tests/read-1.config",
        "Computations/Normal/Interacting/tests/read-2.config",
        "Computations/Normal/Interacting/tests/read-3.config",
        "Values/Composite/Lists/tests/lists.config",
        "Values/Composite/Maps/tests/map-delete.config",
        "Values/Composite/Maps/tests/map-domain.config",
        "Values/Composite/Maps/tests/map-elements.config",
        "Values/Composite/Maps/tests/map-lookup.config",
        "Values/Composite/Maps/tests/map-override.config",
        "Values/Composite/Maps/tests/map-unite.config",
        "Values/Composite/Maps/tests/map.config",
        "Values/Composite/Sets/tests/set-difference.config",
        "Values/Composite/Sets/tests/set-elements.config",
        "Values/Composite/Sets/tests/set-unite.config",
        "Values/Composite/Sets/tests/set.config",
        "Values/Composite/Sets/tests/some-element.config",
        "Values/Composite/Tuples/tests/tuple-zip.config",
        "Values/Primitive/Booleans/tests/and.config",
        "Values/Primitive/Booleans/tests/exclusive-or.config",
        "Values/Primitive/Booleans/tests/implies.config",
        "Values/Primitive/Booleans/tests/not.config",
        "Values/Primitive/Booleans/tests/or.config",
        "Values/Value-Types/tests/cast-to-type.config",
        "Values/Value-Types/tests/is-equal.config",
        "Values/Value-Types/tests/is-in-type.config",
        "Values/Value-Types/tests/is-value.config",
        "Values/Value-Types/tests/when-true.config",
    ).map { "$CBSFILEPATH$it" }.toTypedArray()
}
