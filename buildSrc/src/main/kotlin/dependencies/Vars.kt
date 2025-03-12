package dependencies

object Vars {
    const val generatedPathStr = "../fctlang/src/main/kotlin/generated"
    const val cbsFilePath = "../../CBS-beta/Funcons-beta/"
    val configFiles = listOf<String>(
        "Computations/Normal/Flowing/tests/do-while.config",
        "Computations/Normal/Flowing/tests/if-true-else.config",
        "Computations/Normal/Flowing/tests/sequential.config",
        "Values/Primitive/Booleans/tests/and.config",
        "Values/Primitive/Booleans/tests/exclusive-or.config",
        "Values/Primitive/Booleans/tests/implies.config",
        "Values/Primitive/Booleans/tests/not.config",
        "Values/Primitive/Booleans/tests/or.config",
    ).map { "$cbsFilePath/$it" }.toTypedArray()
}