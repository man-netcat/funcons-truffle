package dependencies

object Vars {
    const val GENERATEDPATHSTR = "../fctlang/src/main/kotlin/generated"
    const val CBSFILEPATH = "../../CBS-beta/Funcons-beta/"
    val configFiles = listOf<String>(
//        "Computations/Normal/Interacting/tests/print-1.config",
//        "Computations/Normal/Interacting/tests/print-2.config",
//        "Computations/Normal/Interacting/tests/read-1.config",
//        "Computations/Normal/Interacting/tests/read-2.config",
//        "Computations/Normal/Interacting/tests/read-3.config",
        "Computations/Normal/Flowing/tests/effect.config",
        "Computations/Normal/Flowing/tests/left-to-right.config",
//        "Computations/Normal/Flowing/tests/right-to-left.config",
        "Computations/Normal/Flowing/tests/choice.config",
        "Computations/Normal/Flowing/tests/do-while.config",
        "Computations/Normal/Flowing/tests/while.config",
        "Computations/Normal/Flowing/tests/if-true-else.config",
        "Computations/Normal/Flowing/tests/sequential.config",
        "Values/Primitive/Booleans/tests/and.config",
        "Values/Primitive/Booleans/tests/exclusive-or.config",
        "Values/Primitive/Booleans/tests/implies.config",
        "Values/Primitive/Booleans/tests/not.config",
        "Values/Primitive/Booleans/tests/or.config",
    ).map { "$CBSFILEPATH/$it" }.toTypedArray()
}