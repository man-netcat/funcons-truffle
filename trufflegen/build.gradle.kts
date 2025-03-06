@file:OptIn(ExperimentalPathApi::class)

import java.nio.file.Files
import java.nio.file.Paths
import kotlin.io.path.ExperimentalPathApi
import kotlin.io.path.deleteRecursively

plugins {
    kotlin("jvm") version "2.1.0"
    java
    application
}

dependencies {
    implementation(project(":antlr"))
}

tasks.compileKotlin {
    dependsOn(":antlr:generateGrammarSource")
}

tasks.test {
    useJUnitPlatform()
}

application {
    mainClass.set("main.TruffleGen")
}

tasks.named<JavaExec>("run") {
    val cbsPathStr = "../../CBS-beta/Funcons-beta/"
    val generatedPathStr = "../fctlang/src/main/kotlin/generated"
    val generated = Paths.get(generatedPathStr)
    generated.deleteRecursively()
    Files.createDirectories(generated)
    args = listOf(
        cbsPathStr,
        generatedPathStr,
        *listOf(
//            "Computations/Normal/Flowing/tests/do-while.config",
            "Computations/Normal/Flowing/tests/if-true-else.config",
            "Computations/Normal/Flowing/tests/sequential.config",
            "Values/Primitive/Booleans/tests/and.config",
            "Values/Primitive/Booleans/tests/exclusive-or.config",
            "Values/Primitive/Booleans/tests/implies.config",
            "Values/Primitive/Booleans/tests/not.config",
            "Values/Primitive/Booleans/tests/or.config",
        ).map { "$cbsPathStr/$it" }.toTypedArray()
    )
}